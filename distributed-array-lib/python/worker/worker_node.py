import socket
import threading
import json
import logging
import time
import sys
import os
import numpy as np
import multiprocessing as mp
from concurrent.futures import ThreadPoolExecutor, ProcessPoolExecutor
from typing import Dict, Any

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from common.message import Message, MessageType

class WorkerNode:
    def __init__(self, worker_id: str, master_host: str, master_port: int):
        self.worker_id = worker_id
        self.master_host = master_host
        self.master_port = master_port
        self.cores = mp.cpu_count()
        self.socket = None
        self.int_segments: Dict[str, np.ndarray] = {}
        self.double_segments: Dict[str, np.ndarray] = {}
        
        # Separate storage for replicas
        self.int_replicas: Dict[str, np.ndarray] = {}
        self.double_replicas: Dict[str, np.ndarray] = {}
        self.is_primary: Dict[str, bool] = {}
        
        self.running = True
        self.thread_pool = ThreadPoolExecutor(max_workers=self.cores)
        self.setup_logging()
    
    def setup_logging(self):
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler(f'worker-{self.worker_id}.log'),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger(f'WorkerNode-{self.worker_id}')
    
    def start(self):
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.master_host, self.master_port))
            
            self.register_with_master()
            
            # Start heartbeat thread
            heartbeat_thread = threading.Thread(target=self.heartbeat_loop)
            heartbeat_thread.daemon = True
            heartbeat_thread.start()
            
            # Listen for messages
            self.listen_for_messages()
            
        except Exception as e:
            self.logger.error(f"Failed to start worker: {e}")
            raise
    
    def register_with_master(self):
        data = {
            "host": self.socket.getsockname()[0],
            "port": self.socket.getsockname()[1],
            "cores": self.cores,
            "memory": os.sysconf('SC_PAGE_SIZE') * os.sysconf('SC_PHYS_PAGES') // (1024 * 1024)
        }
        
        register_msg = Message(
            MessageType.REGISTER_WORKER,
            self.worker_id,
            "master",
            data
        )
        
        self.socket.send(register_msg.to_json().encode() + b'\n')
        self.logger.info("Registered with master node")
    
    def heartbeat_loop(self):
        while self.running:
            try:
                heartbeat = Message(
                    MessageType.HEARTBEAT,
                    self.worker_id,
                    "master",
                    {}
                )
                self.socket.send(heartbeat.to_json().encode() + b'\n')
                time.sleep(3)
            except Exception as e:
                self.logger.error(f"Heartbeat failed: {e}")
                break
    
    def listen_for_messages(self):
        buffer = ""
        while self.running:
            try:
                data = self.socket.recv(8192).decode()
                if not data:
                    break
                
                buffer += data
                while '\n' in buffer:
                    line, buffer = buffer.split('\n', 1)
                    if line:
                        message = Message.from_json(line)
                        self.handle_message(message)
                        
            except ConnectionAbortedError:
                self.logger.warning("Connection aborted.")
                break
            except Exception as e:
                self.logger.error(f"Error receiving message: {e}")
                break
    
    def handle_message(self, message: Message):
        if message.type == MessageType.DISTRIBUTE_ARRAY:
            self.handle_distribute_array(message)
        elif message.type == MessageType.REPLICATE_DATA:
            self.handle_replicate_data(message)
        elif message.type == MessageType.RECOVER_DATA:
            self.handle_recover_data(message)
        elif message.type == MessageType.PROCESS_SEGMENT:
            self.handle_process_segment(message)
        elif message.type == MessageType.SHUTDOWN:
            self.shutdown()
    
    def handle_distribute_array(self, message: Message):
        data = message.data
        array_id = data['arrayId']
        data_type = data['dataType']
        segment_data = data['data']
        is_primary = data.get('isPrimary', True)  # Default to primary for backwards compatibility
        
        segment_key = f"{array_id}_{data.get('segmentId', 0)}"
        self.is_primary[segment_key] = is_primary
        
        if data_type == 'int':
            arr = np.array(segment_data, dtype=np.int32)
            if is_primary:
                self.int_segments[array_id] = arr
                self.logger.info(f"Received PRIMARY int array segment: {array_id} with {len(segment_data)} elements")
            else:
                self.int_replicas[segment_key] = arr
                self.logger.info(f"Received REPLICA int array segment: {segment_key} with {len(segment_data)} elements")
        else:
            arr = np.array(segment_data, dtype=np.float64)
            if is_primary:
                self.double_segments[array_id] = arr
                self.logger.info(f"Received PRIMARY double array segment: {array_id} with {len(segment_data)} elements")
            else:
                self.double_replicas[segment_key] = arr
                self.logger.info(f"Received REPLICA double array segment: {segment_key} with {len(segment_data)} elements")
    
    def handle_replicate_data(self, message: Message):
        # Same logic as distribute but always stored as replica
        data = message.data
        data['isPrimary'] = False
        self.handle_distribute_array(message)
    
    def handle_recover_data(self, message: Message):
        data = message.data
        array_id = data['arrayId']
        segment_id = data['segmentId']
        make_primary = data.get('makePrimary', False)
        
        segment_key = f"{array_id}_{segment_id}"
        
        if make_primary:
            # Promote replica to primary
            int_replica = self.int_replicas.get(segment_key)
            double_replica = self.double_replicas.get(segment_key)
            
            if int_replica is not None:
                self.int_segments[array_id] = int_replica
                self.is_primary[segment_key] = True
                self.logger.info(f"Promoted int replica to primary for {segment_key}")
            elif double_replica is not None:
                self.double_segments[array_id] = double_replica
                self.is_primary[segment_key] = True
                self.logger.info(f"Promoted double replica to primary for {segment_key}")
            
            # Send recovery complete message
            response_data = {
                "arrayId": array_id,
                "segmentId": segment_id,
                "status": "recovered"
            }
            response = Message(
                MessageType.RECOVERY_COMPLETE,
                self.worker_id,
                "master",
                response_data
            )
            self.socket.send(response.to_json().encode() + b'\n')
    
    def handle_process_segment(self, message: Message):
        data = message.data
        array_id = data['arrayId']
        operation = data['operation']

        # Find the primary segment key to identify the segmentId
        segment_key = next((key for key, is_primary in self.is_primary.items() 
                            if is_primary and key.startswith(f"{array_id}_")), None)

        if segment_key is None:
            self.logger.warning(f"No primary segment found for array {array_id} on this worker. Cannot process.")
            return

        segment_id = int(segment_key.split('_')[1])
        
        future = self.thread_pool.submit(self.process_operation, array_id, operation)
        future.add_done_callback(lambda f: self.send_result(array_id, segment_id, f.result()))
    
    def process_operation(self, array_id: str, operation: str):
        if operation == "example1":
            return self.process_example1(array_id)
        elif operation == "example2":
            return self.process_example2(array_id)
        else:
            self.logger.error(f"Unknown operation: {operation}")
            return None
    
    def process_example1(self, array_id: str):
        segment = self.double_segments.get(array_id)
        if segment is None:
            return None
        
        # Parallel processing using threading
        num_threads = min(self.cores, len(segment))
        chunk_size = len(segment) // num_threads
        results = []
        
        def process_chunk(start, end):
            result = np.zeros(end - start)
            for i in range(start, end):
                x = segment[i]
                result[i - start] = ((np.sin(x) + np.cos(x)) ** 2) / (np.sqrt(np.abs(x)) + 1)
            return result
        
        futures = []
        for i in range(num_threads):
            start = i * chunk_size
            end = len(segment) if i == num_threads - 1 else (i + 1) * chunk_size
            future = self.thread_pool.submit(process_chunk, start, end)
            futures.append(future)
        
        # Combine results
        result = np.concatenate([f.result() for f in futures])
        self.double_segments[f"{array_id}_result"] = result
        
        self.logger.info(f"Completed Example 1 processing for {array_id}")
        return result.tolist()
    
    def process_example2(self, array_id: str):
        segment = self.int_segments.get(array_id)
        if segment is None:
            return None
        
        # Parallel processing using threading
        num_threads = min(self.cores, len(segment))
        chunk_size = len(segment) // num_threads
        
        def process_chunk(start, end):
            result = np.zeros(end - start, dtype=np.int32)
            for i in range(start, end):
                x = segment[i]
                if x % 3 == 0 or (500 <= x <= 1000):
                    result[i - start] = int((x * np.log(x)) % 7)
                else:
                    result[i - start] = x
            return result
        
        futures = []
        for i in range(num_threads):
            start = i * chunk_size
            end = len(segment) if i == num_threads - 1 else (i + 1) * chunk_size
            future = self.thread_pool.submit(process_chunk, start, end)
            futures.append(future)
        
        # Combine results
        result = np.concatenate([f.result() for f in futures])
        self.int_segments[f"{array_id}_result"] = result
        
        self.logger.info(f"Completed Example 2 processing for {array_id}")
        return result.tolist()
    
    def send_result(self, array_id: str, segment_id: int, result_data: list):
        if result_data:
            result_msg = Message(
                MessageType.SEGMENT_RESULT,
                self.worker_id,
                "master",
                {
                    "arrayId": array_id,
                    "status": "completed",
                    "segmentId": segment_id,
                    "data": result_data
                }
            )
            self.socket.send(result_msg.to_json().encode() + b'\n')
    
    def shutdown(self):
        self.running = False
        self.thread_pool.shutdown()
        if self.socket:
            self.socket.close()

def main():
    if len(sys.argv) < 4:
        print("Usage: worker_node.py <worker_id> <master_host> <master_port>")
        sys.exit(1)
    
    worker_id = sys.argv[1]
    master_host = sys.argv[2]
    master_port = int(sys.argv[3])
    
    worker = WorkerNode(worker_id, master_host, master_port)
    
    try:
        worker.start()
    except KeyboardInterrupt:
        print(f"\nShutting down worker {worker_id}...")
        worker.shutdown()

if __name__ == "__main__":
    main()