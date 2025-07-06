package master;

import common.*;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

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
        int workerIndex = 0;
        
        for (DArrayInt.Segment segment : array.getSegments()) {
            if (workerIndex >= workerList.size()) workerIndex = 0;
            WorkerInfo worker = workerList.get(workerIndex);
            
            Map<String, Object> data = new HashMap<>();
            data.put("arrayId", array.getArrayId());
            data.put("segmentId", segment.startIndex);
            data.put("startIndex", segment.startIndex);
            data.put("endIndex", segment.endIndex);
            data.put("dataType", "int");
            data.put("data", array.getSegmentData(segment.startIndex, segment.endIndex));
            
            Message distributeMsg = new Message(MessageType.DISTRIBUTE_ARRAY, "master", worker.workerId, data);
            worker.writer.println(gson.toJson(distributeMsg));
            
            segment.workerId = worker.workerId;
            workerIndex++;
        }
    }

    private void distributeArray(DArrayDouble array) {
        List<WorkerInfo> workerList = new ArrayList<>(workers.values());
        int workerIndex = 0;
        
        for (DArrayDouble.Segment segment : array.getSegments()) {
            if (workerIndex >= workerList.size()) workerIndex = 0;
            WorkerInfo worker = workerList.get(workerIndex);
            
            Map<String, Object> data = new HashMap<>();
            data.put("arrayId", array.getArrayId());
            data.put("segmentId", segment.startIndex);
            data.put("startIndex", segment.startIndex);
            data.put("endIndex", segment.endIndex);
            data.put("dataType", "double");
            data.put("data", array.getSegmentData(segment.startIndex, segment.endIndex));
            
            Message distributeMsg = new Message(MessageType.DISTRIBUTE_ARRAY, "master", worker.workerId, data);
            worker.writer.println(gson.toJson(distributeMsg));
            
            segment.workerId = worker.workerId;
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