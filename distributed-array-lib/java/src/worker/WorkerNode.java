package worker;

import common.*;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class WorkerNode {
    private static final Logger logger = Logger.getLogger(WorkerNode.class.getName());
    private final String workerId;
    private final String masterHost;
    private final int masterPort;
    private final int cores;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Map<String, int[]> intSegments = new ConcurrentHashMap<>();
    private final Map<String, double[]> doubleSegments = new ConcurrentHashMap<>();

    // Separate storage for replicas
    private final Map<String, int[]> intReplicas = new ConcurrentHashMap<>();
    private final Map<String, double[]> doubleReplicas = new ConcurrentHashMap<>();
    private final Map<String, Boolean> isPrimary = new ConcurrentHashMap<>();

    private final ExecutorService threadPool;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);
    private final Gson gson = new Gson();
    private volatile boolean running = true;

    public WorkerNode(String workerId, String masterHost, int masterPort) {
        this.workerId = workerId;
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.cores = Runtime.getRuntime().availableProcessors();
        this.threadPool = Executors.newFixedThreadPool(cores);
        setupLogging();
    }

    private void setupLogging() {
        try {
            FileHandler fileHandler = new FileHandler("worker-" + workerId + ".log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            logger.severe("Failed to setup logging: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        socket = new Socket(masterHost, masterPort);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);

        registerWithMaster();

        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, 3, TimeUnit.SECONDS);

        listenForMessages();
    }

    private void registerWithMaster() {
        Map<String, Object> data = new HashMap<>();
        data.put("host", socket.getLocalAddress().getHostAddress());
        data.put("port", socket.getLocalPort());
        data.put("cores", cores);
        data.put("memory", Runtime.getRuntime().maxMemory() / (1024 * 1024));

        Message registerMsg = new Message(MessageType.REGISTER_WORKER, workerId, "master", data);
        writer.println(gson.toJson(registerMsg));
        logger.info("Registered with master node");
    }

    private void sendHeartbeat() {
        if (running && socket.isConnected()) {
            Message heartbeat = new Message(MessageType.HEARTBEAT, workerId, "master", new HashMap<>());
            writer.println(gson.toJson(heartbeat));
        }
    }

    private void listenForMessages() {
        try {
            while (running) {
                String messageStr = reader.readLine();
                if (messageStr == null)
                    break;

                Message message = gson.fromJson(messageStr, Message.class);
                handleMessage(message);
            }
        } catch (IOException e) {
            logger.severe("Lost connection to master: " + e.getMessage());
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case MessageType.DISTRIBUTE_ARRAY:
                handleDistributeArray(message);
                break;
            case MessageType.REPLICATE_DATA:
                handleReplicateData(message);
                break;
            case MessageType.RECOVER_DATA:
                handleRecoverData(message);
                break;
            case MessageType.PROCESS_SEGMENT:
                handleProcessSegment(message);
                break;
            case MessageType.SHUTDOWN:
                shutdown();
                break;
        }
    }

    private void handleDistributeArray(Message message) {
        Map<String, Object> data = message.getData();
        String arrayId = (String) data.get("arrayId");
        String dataType = (String) data.get("dataType");
        Boolean primary = (Boolean) data.get("isPrimary");
        if (primary == null)
            primary = true; // Default to primary for backwards compatibility

        String segmentKey = arrayId + "_" + data.get("segmentId");
        isPrimary.put(segmentKey, primary);

        if (dataType.equals("int")) {
            List<Double> values = (List<Double>) data.get("data");
            int[] segment = values.stream().mapToInt(Double::intValue).toArray();
            if (primary) {
                intSegments.put(arrayId, segment);
                logger.info("Received PRIMARY int array segment: " + arrayId + " with " + segment.length + " elements");
            } else {
                intReplicas.put(segmentKey, segment);
                logger.info(
                        "Received REPLICA int array segment: " + segmentKey + " with " + segment.length + " elements");
            }
        } else if (dataType.equals("double")) {
            List<Double> values = (List<Double>) data.get("data");
            double[] segment = values.stream().mapToDouble(Double::doubleValue).toArray();
            if (primary) {
                doubleSegments.put(arrayId, segment);
                logger.info(
                        "Received PRIMARY double array segment: " + arrayId + " with " + segment.length + " elements");
            } else {
                doubleReplicas.put(segmentKey, segment);
                logger.info("Received REPLICA double array segment: " + segmentKey + " with " + segment.length
                        + " elements");
            }
        }
    }

    private void handleReplicateData(Message message) {
        // Same logic as distribute but always stored as replica
        Map<String, Object> data = message.getData();
        data.put("isPrimary", false);
        handleDistributeArray(message);
    }

    private void handleRecoverData(Message message) {
        Map<String, Object> data = message.getData();
        String arrayId = (String) data.get("arrayId");
        Integer segmentId = ((Double) data.get("segmentId")).intValue();
        Boolean makePrimary = (Boolean) data.get("makePrimary");

        String segmentKey = arrayId + "_" + segmentId;

        if (makePrimary != null && makePrimary) {
            // Promote replica to primary
            int[] intReplica = intReplicas.get(segmentKey);
            double[] doubleReplica = doubleReplicas.get(segmentKey);

            if (intReplica != null) {
                intSegments.put(arrayId, intReplica);
                isPrimary.put(segmentKey, true);
                logger.info("Promoted int replica to primary for " + segmentKey);
            } else if (doubleReplica != null) {
                doubleSegments.put(arrayId, doubleReplica);
                isPrimary.put(segmentKey, true);
                logger.info("Promoted double replica to primary for " + segmentKey);
            }

            // Send recovery complete message
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("arrayId", arrayId);
            responseData.put("segmentId", segmentId);
            responseData.put("status", "recovered");
            Message response = new Message(MessageType.RECOVERY_COMPLETE, workerId, "master", responseData);
            writer.println(gson.toJson(response));
        }
    }

    private void handleProcessSegment(Message message) {
        Map<String, Object> data = message.getData();
        String arrayId = (String) data.get("arrayId");
        String operation = (String) data.get("operation");

        // We need the segmentId to send it back with the result.
        // This information isn't currently passed to the worker for processing.
        // Let's find a primary segment key associated with this worker for this
        // arrayId.
        String segmentKey = isPrimary.entrySet().stream()
                .filter(entry -> entry.getValue() && entry.getKey().startsWith(arrayId + "_"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (segmentKey == null) {
            logger.warning("No primary segment found for array " + arrayId + " on this worker. Cannot process.");
            return;
        }

        int segmentId = Integer.parseInt(segmentKey.split("_")[1]);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            if (operation.equals("example1")) {
                processExample1(arrayId);
            } else if (operation.equals("example2")) {
                processExample2(arrayId);
            }
        }, threadPool);

        future.thenRun(() -> {
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("arrayId", arrayId);
            resultData.put("status", "completed");
            resultData.put("segmentId", segmentId);

            String resultKey = arrayId + "_result";
            if (intSegments.containsKey(resultKey)) {
                resultData.put("data", intSegments.get(resultKey));
            } else if (doubleSegments.containsKey(resultKey)) {
                resultData.put("data", doubleSegments.get(resultKey));
            }

            Message resultMsg = new Message(MessageType.SEGMENT_RESULT, workerId, "master", resultData);
            writer.println(gson.toJson(resultMsg));
        });
    }

    private void processExample1(String arrayId) {
        double[] segment = doubleSegments.get(arrayId);
        if (segment == null)
            return;

        int numThreads = Math.min(cores, segment.length);
        int chunkSize = segment.length / numThreads;
        List<Future<double[]>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int start = i * chunkSize;
            final int end = (i == numThreads - 1) ? segment.length : (i + 1) * chunkSize;

            Future<double[]> future = threadPool.submit(() -> {
                double[] result = new double[end - start];
                for (int j = start; j < end; j++) {
                    double x = segment[j];
                    result[j - start] = Math.pow(Math.sin(x) + Math.cos(x), 2) / (Math.sqrt(Math.abs(x)) + 1);
                }
                return result;
            });

            futures.add(future);
        }

        try {
            double[] result = new double[segment.length];
            int index = 0;
            for (Future<double[]> future : futures) {
                double[] chunk = future.get();
                System.arraycopy(chunk, 0, result, index, chunk.length);
                index += chunk.length;
            }
            doubleSegments.put(arrayId + "_result", result);
            logger.info("Completed Example 1 processing for " + arrayId);
        } catch (Exception e) {
            logger.severe("Error in Example 1 processing: " + e.getMessage());
        }
    }

    private void processExample2(String arrayId) {
        int[] segment = intSegments.get(arrayId);
        if (segment == null) {
            logger.warning("No int segment found for arrayId: " + arrayId);
            return;
        }

        int numThreads = Math.min(cores, segment.length);
        int chunkSize = segment.length / numThreads;
        List<Future<int[]>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int start = i * chunkSize;
            final int end = (i == numThreads - 1) ? segment.length : (i + 1) * chunkSize;

            Future<int[]> future = threadPool.submit(() -> {
                int[] result = new int[end - start];
                for (int j = start; j < end; j++) {
                    int x = segment[j];
                    if (x % 3 == 0 || (x >= 500 && x <= 1000)) {
                        result[j - start] = (int) ((x * Math.log(x)) % 7);
                    } else {
                        result[j - start] = x;
                    }
                }
                return result;
            });

            futures.add(future);
        }

        try {
            int[] result = new int[segment.length];
            int index = 0;
            for (Future<int[]> future : futures) {
                int[] chunk = future.get();
                System.arraycopy(chunk, 0, result, index, chunk.length);
                index += chunk.length;
            }
            intSegments.put(arrayId + "_result", result);
            logger.info("Completed Example 2 processing for " + arrayId);
        } catch (Exception e) {
            logger.severe("Error in Example 2 processing: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        heartbeatScheduler.shutdown();
        threadPool.shutdown();
        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            logger.severe("Error during shutdown: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: WorkerNode <workerId> <masterHost> <masterPort>");
            return;
        }

        String workerId = args[0];
        String masterHost = args[1];
        int masterPort = Integer.parseInt(args[2]);

        WorkerNode worker = new WorkerNode(workerId, masterHost, masterPort);

        try {
            worker.start();
            System.out.println("Worker " + workerId + " connected to master at " + masterHost + ":" + masterPort);

            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));

            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}