import socket
import sys
import os
import json
import numpy as np
from typing import List, Dict, Any

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from common.message import Message, MessageType

class DistributedArrayClient:
    def __init__(self, master_host: str, master_port: int):
        self.master_host = master_host
        self.master_port = master_port
    
    def create_int_array(self, array_id: str, size: int):
        # Generate random integer array
        data = np.random.randint(1, 1001, size=size).tolist()
        
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.connect((self.master_host, self.master_port))
            
            msg = Message(
                MessageType.CREATE_ARRAY,
                "client",
                "master",
                {
                    "arrayId": array_id,
                    "dataType": "int",
                    "values": data
                }
            )
            
            sock.send(msg.to_json().encode() + b'\n')
            response = sock.recv(8192).decode()
            print(f"Create array response: {response}")
    
    def create_double_array(self, array_id: str, size: int):
        # Generate random double array
        data = np.random.uniform(1.0, 100.0, size=size).tolist()
        
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.connect((self.master_host, self.master_port))
            
            msg = Message(
                MessageType.CREATE_ARRAY,
                "client",
                "master",
                {
                    "arrayId": array_id,
                    "dataType": "double",
                    "values": data
                }
            )
            
            sock.send(msg.to_json().encode() + b'\n')
            response = sock.recv(8192).decode()
            print(f"Create array response: {response}")
    
    def apply_operation(self, array_id: str, operation: str):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.connect((self.master_host, self.master_port))
            
            msg = Message(
                MessageType.APPLY_OPERATION,
                "client",
                "master",
                {
                    "arrayId": array_id,
                    "operation": operation
                }
            )
            
            sock.send(msg.to_json().encode() + b'\n')
            response = sock.recv(8192).decode()
            print(f"Apply operation response: {response}")
    
    def get_result(self, array_id: str):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.connect((self.master_host, self.master_port))
            
            msg = Message(
                MessageType.GET_RESULT,
                "client",
                "master",
                {
                    "arrayId": array_id
                }
            )
            
            sock.send(msg.to_json().encode() + b'\n')
            response = sock.recv(8192).decode()
            print(f"Get result response: {response}")

def main():
    if len(sys.argv) < 3:
        print("Usage: distributed_array_client.py <master_host> <master_port>")
        print("\nCommands:")
        print("  create-int <array_id> <size>")
        print("  create-double <array_id> <size>")
        print("  apply <array_id> <operation>")
        print("  get <array_id>")
        sys.exit(1)
    
    master_host = sys.argv[1]
    master_port = int(sys.argv[2])
    
    client = DistributedArrayClient(master_host, master_port)
    
    print(f"Connected to master at {master_host}:{master_port}")
    print("Enter commands (type 'help' for usage, 'exit' to quit):")
    
    while True:
        try:
            command = input("> ").strip().split()
            
            if not command:
                continue
            
            if command[0] == "create-int":
                if len(command) >= 3:
                    client.create_int_array(command[1], int(command[2]))
                else:
                    print("Usage: create-int <array_id> <size>")
            
            elif command[0] == "create-double":
                if len(command) >= 3:
                    client.create_double_array(command[1], int(command[2]))
                else:
                    print("Usage: create-double <array_id> <size>")
            
            elif command[0] == "apply":
                if len(command) >= 3:
                    client.apply_operation(command[1], command[2])
                else:
                    print("Usage: apply <array_id> <operation>")
            
            elif command[0] == "get":
                if len(command) >= 2:
                    client.get_result(command[1])
                else:
                    print("Usage: get <array_id>")
            
            elif command[0] == "help":
                print("\nCommands:")
                print("  create-int <array_id> <size> - Create integer array")
                print("  create-double <array_id> <size> - Create double array")
                print("  apply <array_id> <operation> - Apply operation (example1 or example2)")
                print("  get <array_id> - Get result")
                print("  exit - Quit")
            
            elif command[0] == "exit":
                print("Goodbye!")
                break
            
            else:
                print("Unknown command. Type 'help' for usage.")
                
        except Exception as e:
            print(f"Error: {e}")

if __name__ == "__main__":
    main()