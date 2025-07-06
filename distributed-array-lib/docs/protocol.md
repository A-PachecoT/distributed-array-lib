# Distributed Array Communication Protocol

## Message Format
All messages follow a JSON format with the following structure:
```json
{
  "type": "MESSAGE_TYPE",
  "from": "NODE_ID",
  "to": "NODE_ID",
  "timestamp": 1234567890,
  "data": {}
}
```

## Message Types

### Control Messages
- `REGISTER_WORKER`: Worker node registration with master
- `HEARTBEAT`: Periodic health check from worker to master
- `WORKER_STATUS`: Worker status response to master
- `SHUTDOWN`: Graceful shutdown command

### Data Operations
- `DISTRIBUTE_ARRAY`: Master sends array segment to worker
- `PROCESS_SEGMENT`: Master instructs worker to process data
- `SEGMENT_RESULT`: Worker returns processed segment
- `REPLICATE_DATA`: Instruction to replicate data to backup node

### Recovery Messages
- `NODE_FAILURE`: Notification of detected node failure
- `RECOVER_DATA`: Request to activate replica data
- `RECOVERY_COMPLETE`: Confirmation of successful recovery

### Client Operations
- `CREATE_ARRAY`: Client creates distributed array
- `APPLY_OPERATION`: Client requests operation on array
- `GET_RESULT`: Client retrieves computation result

## Example Messages

### Worker Registration
```json
{
  "type": "REGISTER_WORKER",
  "from": "worker-1",
  "to": "master",
  "timestamp": 1234567890,
  "data": {
    "host": "192.168.1.10",
    "port": 5001,
    "cores": 4,
    "memory": 8192
  }
}
```

### Array Distribution
```json
{
  "type": "DISTRIBUTE_ARRAY",
  "from": "master",
  "to": "worker-1",
  "timestamp": 1234567891,
  "data": {
    "array_id": "array-123",
    "segment_id": 0,
    "start_index": 0,
    "end_index": 1000,
    "data": [1, 2, 3, ...],
    "replicas": ["worker-2"]
  }
}
```