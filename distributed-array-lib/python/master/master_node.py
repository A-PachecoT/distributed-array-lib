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
        
        # Replication tracking
        self.segment_replicas: Dict[str, Dict[int, List[str]]] = {}
        self.worker_segments: Dict[str, set] = {}
        self.REPLICATION_FACTOR = 2  # Primary + 1 replica
        
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
        if not worker_list:
            self.logger.error("No workers available for distribution")
            return
        
        worker_index = 0
        self.segment_replicas[array.array_id] = {}
        
        for segment in array.segments:
            if worker_index >= len(worker_list):
                worker_index = 0
            
            primary_worker = worker_list[worker_index]
            segment_data = array.get_segment_data(segment.start_index, segment.end_index)
            
            # Send to primary worker
            msg_data = {
                "arrayId": array.array_id,
                "segmentId": segment.start_index,
                "startIndex": segment.start_index,
                "endIndex": segment.end_index,
                "dataType": "int",
                "data": segment_data.tolist(),
                "isPrimary": True
            }
            
            distribute_msg = Message(
                MessageType.DISTRIBUTE_ARRAY,
                "master",
                primary_worker.worker_id,
                msg_data
            )
            
            primary_worker.socket.send(distribute_msg.to_json().encode() + b'\n')
            segment.worker_id = primary_worker.worker_id
            
            # Track primary assignment
            if primary_worker.worker_id not in self.worker_segments:
                self.worker_segments[primary_worker.worker_id] = set()
            self.worker_segments[primary_worker.worker_id].add(segment.start_index)
            
            # Send replicas
            replicas = []
            for i in range(1, self.REPLICATION_FACTOR):
                if len(worker_list) > 1:
                    replica_index = (worker_index + i) % len(worker_list)
                    replica_worker = worker_list[replica_index]
                    
                    # Don't replicate to the same worker
                    if replica_worker.worker_id != primary_worker.worker_id:
                        msg_data["isPrimary"] = False
                        replicate_msg = Message(
                            MessageType.REPLICATE_DATA,
                            "master",
                            replica_worker.worker_id,
                            msg_data
                        )
                        replica_worker.socket.send(replicate_msg.to_json().encode() + b'\n')
                        
                        replicas.append(replica_worker.worker_id)
                        segment.replicas.append(replica_worker.worker_id)
                        self.logger.info(f"Replicated segment {segment.start_index} to {replica_worker.worker_id}")
            
            self.segment_replicas[array.array_id][segment.start_index] = replicas
            worker_index += 1
    
    def distribute_double_array(self, array: DArrayDouble):
        worker_list = list(self.workers.values())
        if not worker_list:
            self.logger.error("No workers available for distribution")
            return
        
        worker_index = 0
        self.segment_replicas[array.array_id] = {}
        
        for segment in array.segments:
            if worker_index >= len(worker_list):
                worker_index = 0
            
            primary_worker = worker_list[worker_index]
            segment_data = array.get_segment_data(segment.start_index, segment.end_index)
            
            # Send to primary worker
            msg_data = {
                "arrayId": array.array_id,
                "segmentId": segment.start_index,
                "startIndex": segment.start_index,
                "endIndex": segment.end_index,
                "dataType": "double",
                "data": segment_data.tolist(),
                "isPrimary": True
            }
            
            distribute_msg = Message(
                MessageType.DISTRIBUTE_ARRAY,
                "master",
                primary_worker.worker_id,
                msg_data
            )
            
            primary_worker.socket.send(distribute_msg.to_json().encode() + b'\n')
            segment.worker_id = primary_worker.worker_id
            
            # Track primary assignment
            if primary_worker.worker_id not in self.worker_segments:
                self.worker_segments[primary_worker.worker_id] = set()
            self.worker_segments[primary_worker.worker_id].add(segment.start_index)
            
            # Send replicas
            replicas = []
            for i in range(1, self.REPLICATION_FACTOR):
                if len(worker_list) > 1:
                    replica_index = (worker_index + i) % len(worker_list)
                    replica_worker = worker_list[replica_index]
                    
                    # Don't replicate to the same worker
                    if replica_worker.worker_id != primary_worker.worker_id:
                        msg_data["isPrimary"] = False
                        replicate_msg = Message(
                            MessageType.REPLICATE_DATA,
                            "master",
                            replica_worker.worker_id,
                            msg_data
                        )
                        replica_worker.socket.send(replicate_msg.to_json().encode() + b'\n')
                        
                        replicas.append(replica_worker.worker_id)
                        segment.replicas.append(replica_worker.worker_id)
                        self.logger.info(f"Replicated segment {segment.start_index} to {replica_worker.worker_id}")
            
            self.segment_replicas[array.array_id][segment.start_index] = replicas
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
        
        # Get segments owned by failed worker
        failed_segments = self.worker_segments.get(worker_id, set())
        if not failed_segments:
            self.logger.info(f"No segments to recover from worker {worker_id}")
            return
        
        # Recover each segment
        for array_id, array_replicas in self.segment_replicas.items():
            # Check if this array has segments on the failed worker
            int_array = self.int_arrays.get(array_id)
            double_array = self.double_arrays.get(array_id)
            
            if int_array:
                self._recover_int_array_segments(int_array, worker_id, failed_segments, array_replicas)
            elif double_array:
                self._recover_double_array_segments(double_array, worker_id, failed_segments, array_replicas)
        
        # Remove failed worker from tracking
        if worker_id in self.worker_segments:
            del self.worker_segments[worker_id]
        if worker_id in self.workers:
            del self.workers[worker_id]
    
    def _recover_int_array_segments(self, array: DArrayInt, failed_worker_id: str, 
                                   failed_segments: set, replicas: Dict[int, List[str]]):
        for segment in array.segments:
            if segment.worker_id == failed_worker_id:
                segment_replicas = replicas.get(segment.start_index, [])
                if segment_replicas:
                    # Find first alive replica
                    for replica_id in segment_replicas:
                        replica_worker = self.workers.get(replica_id)
                        if replica_worker and replica_worker.alive:
                            # Promote replica to primary
                            promote_data = {
                                "arrayId": array.array_id,
                                "segmentId": segment.start_index,
                                "makePrimary": True
                            }
                            
                            promote_msg = Message(
                                MessageType.RECOVER_DATA,
                                "master",
                                replica_id,
                                promote_data
                            )
                            replica_worker.socket.send(promote_msg.to_json().encode() + b'\n')
                            
                            # Update segment assignment
                            segment.worker_id = replica_id
                            segment.replicas.remove(replica_id)
                            if replica_id not in self.worker_segments:
                                self.worker_segments[replica_id] = set()
                            self.worker_segments[replica_id].add(segment.start_index)
                            
                            self.logger.info(f"Promoted replica {replica_id} for segment "
                                           f"{segment.start_index} of array {array.array_id}")
                            
                            # Create new replica for resilience
                            self._create_new_replica_int(array, segment)
                            break
    
    def _recover_double_array_segments(self, array: DArrayDouble, failed_worker_id: str,
                                     failed_segments: set, replicas: Dict[int, List[str]]):
        for segment in array.segments:
            if segment.worker_id == failed_worker_id:
                segment_replicas = replicas.get(segment.start_index, [])
                if segment_replicas:
                    # Find first alive replica
                    for replica_id in segment_replicas:
                        replica_worker = self.workers.get(replica_id)
                        if replica_worker and replica_worker.alive:
                            # Promote replica to primary
                            promote_data = {
                                "arrayId": array.array_id,
                                "segmentId": segment.start_index,
                                "makePrimary": True
                            }
                            
                            promote_msg = Message(
                                MessageType.RECOVER_DATA,
                                "master",
                                replica_id,
                                promote_data
                            )
                            replica_worker.socket.send(promote_msg.to_json().encode() + b'\n')
                            
                            # Update segment assignment
                            segment.worker_id = replica_id
                            segment.replicas.remove(replica_id)
                            if replica_id not in self.worker_segments:
                                self.worker_segments[replica_id] = set()
                            self.worker_segments[replica_id].add(segment.start_index)
                            
                            self.logger.info(f"Promoted replica {replica_id} for segment "
                                           f"{segment.start_index} of array {array.array_id}")
                            
                            # Create new replica for resilience
                            self._create_new_replica_double(array, segment)
                            break
    
    def _create_new_replica_int(self, array: DArrayInt, segment):
        available_workers = [w for w in self.workers.values() 
                           if w.alive and w.worker_id != segment.worker_id 
                           and w.worker_id not in segment.replicas]
        
        if available_workers:
            new_replica = available_workers[0]
            segment_data = array.get_segment_data(segment.start_index, segment.end_index)
            
            msg_data = {
                "arrayId": array.array_id,
                "segmentId": segment.start_index,
                "startIndex": segment.start_index,
                "endIndex": segment.end_index,
                "dataType": "int",
                "data": segment_data.tolist(),
                "isPrimary": False
            }
            
            replicate_msg = Message(
                MessageType.REPLICATE_DATA,
                "master",
                new_replica.worker_id,
                msg_data
            )
            new_replica.socket.send(replicate_msg.to_json().encode() + b'\n')
            
            segment.replicas.append(new_replica.worker_id)
            self.segment_replicas[array.array_id][segment.start_index].append(new_replica.worker_id)
            
            self.logger.info(f"Created new replica on {new_replica.worker_id} "
                           f"for segment {segment.start_index}")
    
    def _create_new_replica_double(self, array: DArrayDouble, segment):
        available_workers = [w for w in self.workers.values() 
                           if w.alive and w.worker_id != segment.worker_id 
                           and w.worker_id not in segment.replicas]
        
        if available_workers:
            new_replica = available_workers[0]
            segment_data = array.get_segment_data(segment.start_index, segment.end_index)
            
            msg_data = {
                "arrayId": array.array_id,
                "segmentId": segment.start_index,
                "startIndex": segment.start_index,
                "endIndex": segment.end_index,
                "dataType": "double",
                "data": segment_data.tolist(),
                "isPrimary": False
            }
            
            replicate_msg = Message(
                MessageType.REPLICATE_DATA,
                "master",
                new_replica.worker_id,
                msg_data
            )
            new_replica.socket.send(replicate_msg.to_json().encode() + b'\n')
            
            segment.replicas.append(new_replica.worker_id)
            self.segment_replicas[array.array_id][segment.start_index].append(new_replica.worker_id)
            
            self.logger.info(f"Created new replica on {new_replica.worker_id} "
                           f"for segment {segment.start_index}")
    
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