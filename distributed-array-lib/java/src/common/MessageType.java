package common;

public class MessageType {
    public static final String REGISTER_WORKER = "REGISTER_WORKER";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String WORKER_STATUS = "WORKER_STATUS";
    public static final String SHUTDOWN = "SHUTDOWN";
    
    public static final String DISTRIBUTE_ARRAY = "DISTRIBUTE_ARRAY";
    public static final String PROCESS_SEGMENT = "PROCESS_SEGMENT";
    public static final String SEGMENT_RESULT = "SEGMENT_RESULT";
    public static final String REPLICATE_DATA = "REPLICATE_DATA";
    
    public static final String NODE_FAILURE = "NODE_FAILURE";
    public static final String RECOVER_DATA = "RECOVER_DATA";
    public static final String RECOVERY_COMPLETE = "RECOVERY_COMPLETE";
    
    public static final String CREATE_ARRAY = "CREATE_ARRAY";
    public static final String APPLY_OPERATION = "APPLY_OPERATION";
    public static final String GET_RESULT = "GET_RESULT";
    public static final String OPERATION_COMPLETE = "OPERATION_COMPLETE";
}