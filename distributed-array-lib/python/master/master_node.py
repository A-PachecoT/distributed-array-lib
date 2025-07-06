import socket
import threading
import json
import logging
import time
import sys
import os
from typing import Dict, List, Any
from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from common.message import Message, MessageType
from common.darray import DArrayInt, DArrayDouble

@dataclass
class WorkerInfo:
    worker_id: str
    socket: socket.socket
    address: tuple
    cores: int
    memory: int
    last_heartbeat: float
    alive: bool = True

class MasterNode:
    def __init__(self, port: int):
        self.port = port
        self.server_socket = None
        self.workers: Dict[str, WorkerInfo] = {}
        self.int_arrays: Dict[str, DArrayInt] = {}
        self.double_arrays: Dict[str, DArrayDouble] = {}
        self.running = True
        self.executor = ThreadPoolExecutor(max_workers=20)
        self.setup_logging()
    
    def setup_logging(self):
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler('master.log'),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger('MasterNode')
    
    def start(self):
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind(('0.0.0.0', self.port))
        self.server_socket.listen(10)
        
        self.logger.info(f"Master node started on port {self.port}")
        
        # Start health check thread
        health_thread = threading.Thread(target=self.health_check_loop)
        health_thread.daemon = True
        health_thread.start()
        
        # Accept connections
        while self.running:
            try:
                client_socket, address = self.server_socket.accept()
                self.executor.submit(self.handle_connection, client_socket, address)
            except Exception as e:
                if self.running:
                    self.logger.error(f"Error accepting connection: {e}")
    
    def handle_connection(self, client_socket: socket.socket, address: tuple):
        try:
            # Receive data with larger buffer and handle partial messages
            buffer = ""
            while True:
                chunk = client_socket.recv(8192).decode()
                if not chunk:
                    break
                buffer += chunk
                if '\n' in buffer:
                    data, _ = buffer.split('\n', 1)
                    break
            
            if not buffer:
                return
            
            message = Message.from_json(data if '\n' in buffer else buffer)
            
            if message.type == MessageType.REGISTER_WORKER:
                self.handle_worker_registration(message, client_socket, address)
            else:
                self.handle_client_request(message, client_socket)
        except Exception as e:
            self.logger.error(f"Error handling connection: {e}")
            client_socket.close()
    
    def handle_worker_registration(self, message: Message, worker_socket: socket.socket, address: tuple):
        worker_id = message.from_node
        data = message.data
        
        worker = WorkerInfo(
            worker_id=worker_id,
            socket=worker_socket,
            address=address,
            cores=data['cores'],
            memory=data['memory'],
            last_heartbeat=time.time()
        )
        
        self.workers[worker_id] = worker
        self.logger.info(f"Worker registered: {worker_id} from {address}")
        
        # Start worker message handler
        self.executor.submit(self.handle_worker_messages, worker)
    
    def handle_worker_messages(self, worker: WorkerInfo):
        try:
            while worker.alive and self.running:
                data = worker.socket.recv(8192).decode()
                if not data:
                    break
                
                message = Message.from_json(data)
                
                if message.type == MessageType.HEARTBEAT:
                    worker.last_heartbeat = time.time()
                elif message.type == MessageType.SEGMENT_RESULT:
                    self.logger.info(f"Received segment result from {worker.worker_id}")
                elif message.type == MessageType.RECOVERY_COMPLETE:
                    self.logger.info(f"Recovery completed by {worker.worker_id}")
        except Exception as e:
            self.logger.error(f"Lost connection to worker {worker.worker_id}: {e}")
            worker.alive = False
            self.handle_worker_failure(worker.worker_id)
    
    def handle_client_request(self, message: Message, client_socket: socket.socket):
        try:
            if message.type == MessageType.CREATE_ARRAY:
                self.handle_create_array(message, client_socket)
            elif message.type == MessageType.APPLY_OPERATION:
                self.handle_apply_operation(message, client_socket)
            elif message.type == MessageType.GET_RESULT:
                self.handle_get_result(message, client_socket)
        finally:
            client_socket.close()
    
    def handle_create_array(self, message: Message, client_socket: socket.socket):
        data = message.data
        array_id = data['arrayId']
        data_type = data['dataType']
        values = data['values']
        
        if data_type == 'int':
            darray = DArrayInt(array_id, values)
            darray.segment_array(len(self.workers))
            self.int_arrays[array_id] = darray
            self.distribute_int_array(darray)
        else:
            darray = DArrayDouble(array_id, values)
            darray.segment_array(len(self.workers))
            self.double_arrays[array_id] = darray
            self.distribute_double_array(darray)
        
        response = Message(
            MessageType.OPERATION_COMPLETE,
            "master",
            message.from_node,
            {"status": "created", "arrayId": array_id}
        )
        client_socket.send(response.to_json().encode() + b'\n')
    
    def distribute_int_array(self, array: DArrayInt):
        worker_list = list(self.workers.values())
        worker_index = 0
        
        for segment in array.segments:
            if worker_index >= len(worker_list):
                worker_index = 0
            
            worker = worker_list[worker_index]
            segment_data = array.get_segment_data(segment.start_index, segment.end_index)
            
            msg_data = {
                "arrayId": array.array_id,
                "segmentId": segment.start_index,
                "startIndex": segment.start_index,
                "endIndex": segment.end_index,
                "dataType": "int",
                "data": segment_data.tolist()
            }
            
            distribute_msg = Message(
                MessageType.DISTRIBUTE_ARRAY,
                "master",
                worker.worker_id,
                msg_data
            )
            
            worker.socket.send(distribute_msg.to_json().encode() + b'\n')
            segment.worker_id = worker.worker_id
            worker_index += 1
    
    def distribute_double_array(self, array: DArrayDouble):
        worker_list = list(self.workers.values())
        worker_index = 0
        
        for segment in array.segments:
            if worker_index >= len(worker_list):
                worker_index = 0
            
            worker = worker_list[worker_index]
            segment_data = array.get_segment_data(segment.start_index, segment.end_index)
            
            msg_data = {
                "arrayId": array.array_id,
                "segmentId": segment.start_index,
                "startIndex": segment.start_index,
                "endIndex": segment.end_index,
                "dataType": "double",
                "data": segment_data.tolist()
            }
            
            distribute_msg = Message(
                MessageType.DISTRIBUTE_ARRAY,
                "master",
                worker.worker_id,
                msg_data
            )
            
            worker.socket.send(distribute_msg.to_json().encode() + b'\n')
            segment.worker_id = worker.worker_id
            worker_index += 1
    
    def handle_apply_operation(self, message: Message, client_socket: socket.socket):
        data = message.data
        array_id = data['arrayId']
        operation = data['operation']
        
        for worker in self.workers.values():
            if worker.alive:
                process_msg = Message(
                    MessageType.PROCESS_SEGMENT,
                    "master",
                    worker.worker_id,
                    {"arrayId": array_id, "operation": operation}
                )
                worker.socket.send(process_msg.to_json().encode() + b'\n')
        
        response = Message(
            MessageType.OPERATION_COMPLETE,
            "master",
            message.from_node,
            {"status": "processing", "arrayId": array_id}
        )
        client_socket.send(response.to_json().encode() + b'\n')
    
    def handle_get_result(self, message: Message, client_socket: socket.socket):
        response = Message(
            MessageType.OPERATION_COMPLETE,
            "master",
            message.from_node,
            {"status": "complete", "result": "Operation completed successfully"}
        )
        client_socket.send(response.to_json().encode() + b'\n')
    
    def health_check_loop(self):
        while self.running:
            time.sleep(5)
            current_time = time.time()
            
            for worker_id, worker in list(self.workers.items()):
                if worker.alive and (current_time - worker.last_heartbeat) > 10:
                    self.logger.warning(f"Worker {worker_id} failed health check")
                    worker.alive = False
                    self.handle_worker_failure(worker_id)
    
    def handle_worker_failure(self, worker_id: str):
        self.logger.error(f"Handling failure of worker: {worker_id}")
        # TODO: Implement recovery mechanism
    
    def shutdown(self):
        self.running = False
        if self.server_socket:
            self.server_socket.close()
        self.executor.shutdown()

def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5000
    master = MasterNode(port)
    
    try:
        master.start()
    except KeyboardInterrupt:
        print("\nShutting down master node...")
        master.shutdown()

if __name__ == "__main__":
    main()