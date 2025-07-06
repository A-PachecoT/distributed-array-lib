export enum MessageType {
    REGISTER_WORKER = "REGISTER_WORKER",
    HEARTBEAT = "HEARTBEAT",
    WORKER_STATUS = "WORKER_STATUS",
    SHUTDOWN = "SHUTDOWN",
    
    DISTRIBUTE_ARRAY = "DISTRIBUTE_ARRAY",
    PROCESS_SEGMENT = "PROCESS_SEGMENT",
    SEGMENT_RESULT = "SEGMENT_RESULT",
    REPLICATE_DATA = "REPLICATE_DATA",
    
    NODE_FAILURE = "NODE_FAILURE",
    RECOVER_DATA = "RECOVER_DATA",
    RECOVERY_COMPLETE = "RECOVERY_COMPLETE",
    
    CREATE_ARRAY = "CREATE_ARRAY",
    APPLY_OPERATION = "APPLY_OPERATION",
    GET_RESULT = "GET_RESULT",
    OPERATION_COMPLETE = "OPERATION_COMPLETE"
}

export interface Message {
    type: string;
    from: string;
    to: string;
    timestamp: number;
    data: Record<string, any>;
}

export class MessageBuilder {
    static create(type: string, from: string, to: string, data: Record<string, any>): Message {
        return {
            type,
            from,
            to,
            timestamp: Date.now(),
            data
        };
    }

    static toJSON(message: Message): string {
        return JSON.stringify(message);
    }

    static fromJSON(json: string): Message {
        return JSON.parse(json);
    }
}