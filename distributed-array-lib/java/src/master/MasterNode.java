package master;

import common.*;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;

public class MasterNode {
    private static final Logger logger = Logger.getLogger(MasterNode.class.getName());
    private final int port;
    private ServerSocket serverSocket;
    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private final Map<String, DArrayInt> intArrays = new ConcurrentHashMap<>();
    private final Map<String, DArrayDouble> doubleArrays = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Gson gson = new Gson();
    private volatile boolean running = true;
    
    // Replication tracking
    private final Map<String, Map<Integer, List<String>>> segmentReplicas = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> workerSegments = new ConcurrentHashMap<>();
    private static final int REPLICATION_FACTOR = 2; // Primary + 1 replica

    class WorkerInfo {
        String workerId;
        Socket socket;
        BufferedReader reader;
        PrintWriter writer;
        long lastHeartbeat;
        int cores;
        int memory;
        boolean alive = true;

        WorkerInfo(String workerId, Socket socket) throws IOException {
            this.workerId = workerId;
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.lastHeartbeat = System.currentTimeMillis();
        }
    }

    public MasterNode(int port) {
        this.port = port;
        setupLogging();
    }

    private void setupLogging() {
        try {
            FileHandler fileHandler = new FileHandler("master.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            logger.severe("Failed to setup logging: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        logger.info("Master node started on port " + port);

        scheduler.scheduleAtFixedRate(this::checkWorkerHealth, 5, 5, TimeUnit.SECONDS);

        executor.execute(this::acceptConnections);
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.execute(() -> handleConnection(clientSocket));
            } catch (IOException e) {
                if (running) {
                    logger.severe("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            
            String messageStr = reader.readLine();
            if (messageStr != null) {
                Message message = gson.fromJson(messageStr, Message.class);
                
                if (message.getType().equals(MessageType.REGISTER_WORKER)) {
                    handleWorkerRegistration(message, socket);
                } else {
                    handleClientRequest(message, socket, reader, writer);
                }
            }
        } catch (IOException e) {
            logger.severe("Error handling connection: " + e.getMessage());
        }
    }

    private void handleWorkerRegistration(Message message, Socket socket) throws IOException {
        String workerId = message.getFrom();
        WorkerInfo worker = new WorkerInfo(workerId, socket);
        
        Map<String, Object> data = message.getData();
        worker.cores = ((Double) data.get("cores")).intValue();
        worker.memory = ((Double) data.get("memory")).intValue();
        
        workers.put(workerId, worker);
        logger.info("Worker registered: " + workerId);
        
        executor.execute(() -> handleWorkerMessages(worker));
    }

    private void handleWorkerMessages(WorkerInfo worker) {
        try {
            while (worker.alive && running) {
                String messageStr = worker.reader.readLine();
                if (messageStr == null) break;
                
                Message message = gson.fromJson(messageStr, Message.class);
                
                switch (message.getType()) {
                    case MessageType.HEARTBEAT:
                        worker.lastHeartbeat = System.currentTimeMillis();
                        break;
                    case MessageType.SEGMENT_RESULT:
                        handleSegmentResult(message);
                        break;
                    case MessageType.RECOVERY_COMPLETE:
                        logger.info("Recovery completed by " + worker.workerId);
                        break;
                }
            }
        } catch (IOException e) {
            logger.severe("Lost connection to worker " + worker.workerId);
            worker.alive = false;
            handleWorkerFailure(worker.workerId);
        }
    }

    private void handleClientRequest(Message message, Socket socket, BufferedReader reader, PrintWriter writer) {
        switch (message.getType()) {
            case MessageType.CREATE_ARRAY:
                handleCreateArray(message, writer);
                break;
            case MessageType.APPLY_OPERATION:
                handleApplyOperation(message, writer);
                break;
            case MessageType.GET_RESULT:
                handleGetResult(message, writer);
                break;
        }
    }

    private void handleCreateArray(Message message, PrintWriter writer) {
        Map<String, Object> data = message.getData();
        String arrayId = (String) data.get("arrayId");
        String dataType = (String) data.get("dataType");
        
        if (dataType.equals("int")) {
            List<Double> values = (List<Double>) data.get("values");
            int[] intArray = values.stream().mapToInt(Double::intValue).toArray();
            DArrayInt dArray = new DArrayInt(arrayId, intArray);
            dArray.segmentArray(workers.size());
            intArrays.put(arrayId, dArray);
            distributeArray(dArray);
        } else if (dataType.equals("double")) {
            List<Double> values = (List<Double>) data.get("values");
            double[] doubleArray = values.stream().mapToDouble(Double::doubleValue).toArray();
            DArrayDouble dArray = new DArrayDouble(arrayId, doubleArray);
            dArray.segmentArray(workers.size());
            doubleArrays.put(arrayId, dArray);
            distributeArray(dArray);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "created");
        response.put("arrayId", arrayId);
        Message responseMsg = new Message(MessageType.OPERATION_COMPLETE, "master", message.getFrom(), response);
        writer.println(gson.toJson(responseMsg));
    }

    private void distributeArray(DArrayInt array) {
        List<WorkerInfo> workerList = new ArrayList<>(workers.values());
        if (workerList.isEmpty()) {
            logger.severe("No workers available for distribution");
            return;
        }
        
        int workerIndex = 0;
        segmentReplicas.put(array.getArrayId(), new HashMap<>());
        
        for (DArrayInt.Segment segment : array.getSegments()) {
            if (workerIndex >= workerList.size()) workerIndex = 0;
            WorkerInfo primaryWorker = workerList.get(workerIndex);
            
            // Send to primary worker
            Map<String, Object> data = new HashMap<>();
            data.put("arrayId", array.getArrayId());
            data.put("segmentId", segment.startIndex);
            data.put("startIndex", segment.startIndex);
            data.put("endIndex", segment.endIndex);
            data.put("dataType", "int");
            data.put("data", array.getSegmentData(segment.startIndex, segment.endIndex));
            data.put("isPrimary", true);
            
            Message distributeMsg = new Message(MessageType.DISTRIBUTE_ARRAY, "master", primaryWorker.workerId, data);
            primaryWorker.writer.println(gson.toJson(distributeMsg));
            
            segment.workerId = primaryWorker.workerId;
            
            // Track primary assignment
            workerSegments.computeIfAbsent(primaryWorker.workerId, k -> new HashSet<>()).add(segment.startIndex);
            
            // Send replicas
            List<String> replicas = new ArrayList<>();
            for (int i = 1; i < REPLICATION_FACTOR && workerList.size() > 1; i++) {
                int replicaIndex = (workerIndex + i) % workerList.size();
                WorkerInfo replicaWorker = workerList.get(replicaIndex);
                
                // Don't replicate to the same worker
                if (!replicaWorker.workerId.equals(primaryWorker.workerId)) {
                    data.put("isPrimary", false);
                    Message replicateMsg = new Message(MessageType.REPLICATE_DATA, "master", replicaWorker.workerId, data);
                    replicaWorker.writer.println(gson.toJson(replicateMsg));
                    
                    replicas.add(replicaWorker.workerId);
                    segment.replicas.add(replicaWorker.workerId);
                    logger.info("Replicated segment " + segment.startIndex + " to " + replicaWorker.workerId);
                }
            }
            
            segmentReplicas.get(array.getArrayId()).put(segment.startIndex, replicas);
            workerIndex++;
        }
    }

    private void distributeArray(DArrayDouble array) {
        List<WorkerInfo> workerList = new ArrayList<>(workers.values());
        if (workerList.isEmpty()) {
            logger.severe("No workers available for distribution");
            return;
        }
        
        int workerIndex = 0;
        segmentReplicas.put(array.getArrayId(), new HashMap<>());
        
        for (DArrayDouble.Segment segment : array.getSegments()) {
            if (workerIndex >= workerList.size()) workerIndex = 0;
            WorkerInfo primaryWorker = workerList.get(workerIndex);
            
            // Send to primary worker
            Map<String, Object> data = new HashMap<>();
            data.put("arrayId", array.getArrayId());
            data.put("segmentId", segment.startIndex);
            data.put("startIndex", segment.startIndex);
            data.put("endIndex", segment.endIndex);
            data.put("dataType", "double");
            data.put("data", array.getSegmentData(segment.startIndex, segment.endIndex));
            data.put("isPrimary", true);
            
            Message distributeMsg = new Message(MessageType.DISTRIBUTE_ARRAY, "master", primaryWorker.workerId, data);
            primaryWorker.writer.println(gson.toJson(distributeMsg));
            
            segment.workerId = primaryWorker.workerId;
            
            // Track primary assignment
            workerSegments.computeIfAbsent(primaryWorker.workerId, k -> new HashSet<>()).add(segment.startIndex);
            
            // Send replicas
            List<String> replicas = new ArrayList<>();
            for (int i = 1; i < REPLICATION_FACTOR && workerList.size() > 1; i++) {
                int replicaIndex = (workerIndex + i) % workerList.size();
                WorkerInfo replicaWorker = workerList.get(replicaIndex);
                
                // Don't replicate to the same worker
                if (!replicaWorker.workerId.equals(primaryWorker.workerId)) {
                    data.put("isPrimary", false);
                    Message replicateMsg = new Message(MessageType.REPLICATE_DATA, "master", replicaWorker.workerId, data);
                    replicaWorker.writer.println(gson.toJson(replicateMsg));
                    
                    replicas.add(replicaWorker.workerId);
                    segment.replicas.add(replicaWorker.workerId);
                    logger.info("Replicated segment " + segment.startIndex + " to " + replicaWorker.workerId);
                }
            }
            
            segmentReplicas.get(array.getArrayId()).put(segment.startIndex, replicas);
            workerIndex++;
        }
    }

    private void handleApplyOperation(Message message, PrintWriter writer) {
        Map<String, Object> data = message.getData();
        String arrayId = (String) data.get("arrayId");
        String operation = (String) data.get("operation");
        
        for (WorkerInfo worker : workers.values()) {
            if (worker.alive) {
                Map<String, Object> processData = new HashMap<>();
                processData.put("arrayId", arrayId);
                processData.put("operation", operation);
                
                Message processMsg = new Message(MessageType.PROCESS_SEGMENT, "master", worker.workerId, processData);
                worker.writer.println(gson.toJson(processMsg));
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "processing");
        response.put("arrayId", arrayId);
        Message responseMsg = new Message(MessageType.OPERATION_COMPLETE, "master", message.getFrom(), response);
        writer.println(gson.toJson(responseMsg));
    }

    private void handleSegmentResult(Message message) {
        logger.info("Received segment result from " + message.getFrom());
    }

    private void handleGetResult(Message message, PrintWriter writer) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "complete");
        response.put("result", "Operation completed successfully");
        Message responseMsg = new Message(MessageType.OPERATION_COMPLETE, "master", message.getFrom(), response);
        writer.println(gson.toJson(responseMsg));
    }

    private void checkWorkerHealth() {
        long currentTime = System.currentTimeMillis();
        for (WorkerInfo worker : workers.values()) {
            if (worker.alive && (currentTime - worker.lastHeartbeat) > 10000) {
                logger.warning("Worker " + worker.workerId + " failed health check");
                worker.alive = false;
                handleWorkerFailure(worker.workerId);
            }
        }
    }

    private void handleWorkerFailure(String workerId) {
        logger.severe("Handling failure of worker: " + workerId);
        
        // Get segments owned by failed worker
        Set<Integer> failedSegments = workerSegments.get(workerId);
        if (failedSegments == null || failedSegments.isEmpty()) {
            logger.info("No segments to recover from worker " + workerId);
            return;
        }
        
        // Recover each segment
        for (String arrayId : segmentReplicas.keySet()) {
            Map<Integer, List<String>> arrayReplicas = segmentReplicas.get(arrayId);
            
            // Check if this array has segments on the failed worker
            DArrayInt intArray = intArrays.get(arrayId);
            DArrayDouble doubleArray = doubleArrays.get(arrayId);
            
            if (intArray != null) {
                recoverIntArraySegments(intArray, workerId, failedSegments, arrayReplicas);
            } else if (doubleArray != null) {
                recoverDoubleArraySegments(doubleArray, workerId, failedSegments, arrayReplicas);
            }
        }
        
        // Remove failed worker from tracking
        workerSegments.remove(workerId);
        workers.remove(workerId);
    }
    
    private void recoverIntArraySegments(DArrayInt array, String failedWorkerId, 
                                       Set<Integer> failedSegments, Map<Integer, List<String>> replicas) {
        for (DArrayInt.Segment segment : array.getSegments()) {
            if (segment.workerId.equals(failedWorkerId)) {
                List<String> segmentReplicas = replicas.get(segment.startIndex);
                if (segmentReplicas != null && !segmentReplicas.isEmpty()) {
                    // Find first alive replica
                    for (String replicaId : segmentReplicas) {
                        WorkerInfo replica = workers.get(replicaId);
                        if (replica != null && replica.alive) {
                            // Promote replica to primary
                            Map<String, Object> promoteData = new HashMap<>();
                            promoteData.put("arrayId", array.getArrayId());
                            promoteData.put("segmentId", segment.startIndex);
                            promoteData.put("makePrimary", true);
                            
                            Message promoteMsg = new Message(MessageType.RECOVER_DATA, 
                                "master", replicaId, promoteData);
                            replica.writer.println(gson.toJson(promoteMsg));
                            
                            // Update segment assignment
                            segment.workerId = replicaId;
                            segment.replicas.remove(replicaId);
                            workerSegments.computeIfAbsent(replicaId, k -> new HashSet<>())
                                .add(segment.startIndex);
                            
                            logger.info("Promoted replica " + replicaId + " for segment " + 
                                segment.startIndex + " of array " + array.getArrayId());
                            
                            // Create new replica for resilience
                            createNewReplica(array, segment);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private void recoverDoubleArraySegments(DArrayDouble array, String failedWorkerId, 
                                          Set<Integer> failedSegments, Map<Integer, List<String>> replicas) {
        for (DArrayDouble.Segment segment : array.getSegments()) {
            if (segment.workerId.equals(failedWorkerId)) {
                List<String> segmentReplicas = replicas.get(segment.startIndex);
                if (segmentReplicas != null && !segmentReplicas.isEmpty()) {
                    // Find first alive replica
                    for (String replicaId : segmentReplicas) {
                        WorkerInfo replica = workers.get(replicaId);
                        if (replica != null && replica.alive) {
                            // Promote replica to primary
                            Map<String, Object> promoteData = new HashMap<>();
                            promoteData.put("arrayId", array.getArrayId());
                            promoteData.put("segmentId", segment.startIndex);
                            promoteData.put("makePrimary", true);
                            
                            Message promoteMsg = new Message(MessageType.RECOVER_DATA, 
                                "master", replicaId, promoteData);
                            replica.writer.println(gson.toJson(promoteMsg));
                            
                            // Update segment assignment
                            segment.workerId = replicaId;
                            segment.replicas.remove(replicaId);
                            workerSegments.computeIfAbsent(replicaId, k -> new HashSet<>())
                                .add(segment.startIndex);
                            
                            logger.info("Promoted replica " + replicaId + " for segment " + 
                                segment.startIndex + " of array " + array.getArrayId());
                            
                            // Create new replica for resilience
                            createNewReplica(array, segment);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private void createNewReplica(DArrayInt array, DArrayInt.Segment segment) {
        List<WorkerInfo> availableWorkers = workers.values().stream()
            .filter(w -> w.alive && !w.workerId.equals(segment.workerId) && 
                        !segment.replicas.contains(w.workerId))
            .collect(Collectors.toList());
        
        if (!availableWorkers.isEmpty()) {
            WorkerInfo newReplica = availableWorkers.get(0);
            
            Map<String, Object> data = new HashMap<>();
            data.put("arrayId", array.getArrayId());
            data.put("segmentId", segment.startIndex);
            data.put("startIndex", segment.startIndex);
            data.put("endIndex", segment.endIndex);
            data.put("dataType", "int");
            data.put("data", array.getSegmentData(segment.startIndex, segment.endIndex));
            data.put("isPrimary", false);
            
            Message replicateMsg = new Message(MessageType.REPLICATE_DATA, 
                "master", newReplica.workerId, data);
            newReplica.writer.println(gson.toJson(replicateMsg));
            
            segment.replicas.add(newReplica.workerId);
            segmentReplicas.get(array.getArrayId()).get(segment.startIndex).add(newReplica.workerId);
            
            logger.info("Created new replica on " + newReplica.workerId + 
                " for segment " + segment.startIndex);
        }
    }
    
    private void createNewReplica(DArrayDouble array, DArrayDouble.Segment segment) {
        List<WorkerInfo> availableWorkers = workers.values().stream()
            .filter(w -> w.alive && !w.workerId.equals(segment.workerId) && 
                        !segment.replicas.contains(w.workerId))
            .collect(Collectors.toList());
        
        if (!availableWorkers.isEmpty()) {
            WorkerInfo newReplica = availableWorkers.get(0);
            
            Map<String, Object> data = new HashMap<>();
            data.put("arrayId", array.getArrayId());
            data.put("segmentId", segment.startIndex);
            data.put("startIndex", segment.startIndex);
            data.put("endIndex", segment.endIndex);
            data.put("dataType", "double");
            data.put("data", array.getSegmentData(segment.startIndex, segment.endIndex));
            data.put("isPrimary", false);
            
            Message replicateMsg = new Message(MessageType.REPLICATE_DATA, 
                "master", newReplica.workerId, data);
            newReplica.writer.println(gson.toJson(replicateMsg));
            
            segment.replicas.add(newReplica.workerId);
            segmentReplicas.get(array.getArrayId()).get(segment.startIndex).add(newReplica.workerId);
            
            logger.info("Created new replica on " + newReplica.workerId + 
                " for segment " + segment.startIndex);
        }
    }

    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            executor.shutdown();
            scheduler.shutdown();
        } catch (IOException e) {
            logger.severe("Error during shutdown: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        MasterNode master = new MasterNode(port);
        
        try {
            master.start();
            System.out.println("Master node running on port " + port);
            
            Runtime.getRuntime().addShutdownHook(new Thread(master::shutdown));
            
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}