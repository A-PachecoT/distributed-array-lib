import json
import time
from typing import Dict, Any

class MessageType:
    REGISTER_WORKER = "REGISTER_WORKER"
    HEARTBEAT = "HEARTBEAT"
    WORKER_STATUS = "WORKER_STATUS"
    SHUTDOWN = "SHUTDOWN"
    
    DISTRIBUTE_ARRAY = "DISTRIBUTE_ARRAY"
    PROCESS_SEGMENT = "PROCESS_SEGMENT"
    SEGMENT_RESULT = "SEGMENT_RESULT"
    REPLICATE_DATA = "REPLICATE_DATA"
    
    NODE_FAILURE = "NODE_FAILURE"
    RECOVER_DATA = "RECOVER_DATA"
    RECOVERY_COMPLETE = "RECOVERY_COMPLETE"
    
    CREATE_ARRAY = "CREATE_ARRAY"
    APPLY_OPERATION = "APPLY_OPERATION"
    GET_RESULT = "GET_RESULT"
    OPERATION_COMPLETE = "OPERATION_COMPLETE"

class Message:
    def __init__(self, msg_type: str, from_node: str, to_node: str, data: Dict[str, Any]):
        self.type = msg_type
        self.from_node = from_node
        self.to_node = to_node
        self.timestamp = int(time.time() * 1000)
        self.data = data
    
    def to_json(self) -> str:
        return json.dumps({
            "type": self.type,
            "from": self.from_node,
            "to": self.to_node,
            "timestamp": self.timestamp,
            "data": self.data
        })
    
    @staticmethod
    def from_json(json_str: str) -> 'Message':
        obj = json.loads(json_str)
        msg = Message(obj["type"], obj["from"], obj["to"], obj["data"])
        msg.timestamp = obj["timestamp"]
        return msg