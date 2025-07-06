package client;

import common.*;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DistributedArrayClient {
    private final String masterHost;
    private final int masterPort;
    private final Gson gson = new Gson();

    public DistributedArrayClient(String masterHost, int masterPort) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    public void createIntArray(String arrayId, int size) throws IOException {
        try (Socket socket = new Socket(masterHost, masterPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            
            int[] data = new int[size];
            for (int i = 0; i < size; i++) {
                data[i] = ThreadLocalRandom.current().nextInt(1, 1001);
            }
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("arrayId", arrayId);
            messageData.put("dataType", "int");
            messageData.put("values", data);
            
            Message createMsg = new Message(MessageType.CREATE_ARRAY, "client", "master", messageData);
            writer.println(gson.toJson(createMsg));
            
            String response = reader.readLine();
            System.out.println("Create array response: " + response);
        }
    }

    public void createDoubleArray(String arrayId, int size) throws IOException {
        try (Socket socket = new Socket(masterHost, masterPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            
            double[] data = new double[size];
            for (int i = 0; i < size; i++) {
                data[i] = ThreadLocalRandom.current().nextDouble(1.0, 100.0);
            }
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("arrayId", arrayId);
            messageData.put("dataType", "double");
            messageData.put("values", data);
            
            Message createMsg = new Message(MessageType.CREATE_ARRAY, "client", "master", messageData);
            writer.println(gson.toJson(createMsg));
            
            String response = reader.readLine();
            System.out.println("Create array response: " + response);
        }
    }

    public void applyOperation(String arrayId, String operation) throws IOException {
        try (Socket socket = new Socket(masterHost, masterPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("arrayId", arrayId);
            messageData.put("operation", operation);
            
            Message applyMsg = new Message(MessageType.APPLY_OPERATION, "client", "master", messageData);
            writer.println(gson.toJson(applyMsg));
            
            String response = reader.readLine();
            System.out.println("Apply operation response: " + response);
        }
    }

    public void getResult(String arrayId) throws IOException {
        try (Socket socket = new Socket(masterHost, masterPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("arrayId", arrayId);
            
            Message getMsg = new Message(MessageType.GET_RESULT, "client", "master", messageData);
            writer.println(gson.toJson(getMsg));
            
            String response = reader.readLine();
            System.out.println("Get result response: " + response);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: DistributedArrayClient <masterHost> <masterPort>");
            System.out.println("Commands:");
            System.out.println("  create-int <arrayId> <size>");
            System.out.println("  create-double <arrayId> <size>");
            System.out.println("  apply <arrayId> <operation>");
            System.out.println("  get <arrayId>");
            return;
        }

        String masterHost = args[0];
        int masterPort = Integer.parseInt(args[1]);
        DistributedArrayClient client = new DistributedArrayClient(masterHost, masterPort);

        Scanner scanner = new Scanner(System.in);
        System.out.println("Connected to master at " + masterHost + ":" + masterPort);
        System.out.println("Enter commands (type 'help' for usage, 'exit' to quit):");

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine();
            String[] parts = line.split(" ");
            
            if (parts.length == 0) continue;
            
            try {
                switch (parts[0]) {
                    case "create-int":
                        if (parts.length >= 3) {
                            client.createIntArray(parts[1], Integer.parseInt(parts[2]));
                        } else {
                            System.out.println("Usage: create-int <arrayId> <size>");
                        }
                        break;
                    case "create-double":
                        if (parts.length >= 3) {
                            client.createDoubleArray(parts[1], Integer.parseInt(parts[2]));
                        } else {
                            System.out.println("Usage: create-double <arrayId> <size>");
                        }
                        break;
                    case "apply":
                        if (parts.length >= 3) {
                            client.applyOperation(parts[1], parts[2]);
                        } else {
                            System.out.println("Usage: apply <arrayId> <operation>");
                        }
                        break;
                    case "get":
                        if (parts.length >= 2) {
                            client.getResult(parts[1]);
                        } else {
                            System.out.println("Usage: get <arrayId>");
                        }
                        break;
                    case "help":
                        System.out.println("Commands:");
                        System.out.println("  create-int <arrayId> <size> - Create integer array");
                        System.out.println("  create-double <arrayId> <size> - Create double array");
                        System.out.println("  apply <arrayId> <operation> - Apply operation (example1 or example2)");
                        System.out.println("  get <arrayId> - Get result");
                        System.out.println("  exit - Quit");
                        break;
                    case "exit":
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Unknown command. Type 'help' for usage.");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}