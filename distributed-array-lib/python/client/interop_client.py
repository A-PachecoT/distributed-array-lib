import socket
import sys
import os
import json
import numpy as np
import time
from typing import List, Dict, Any

# Add project root to path to import common modules
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from common.message import Message, MessageType

class InteropClient:
    def __init__(self, master_host: str, master_port: int):
        self.master_host = master_host
        self.master_port = master_port

    def _send_and_receive(self, msg: Message) -> Dict[str, Any]:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.connect((self.master_host, self.master_port))
            sock.send(msg.to_json().encode() + b'\n')
            
            # Read response, handling potential fragmentation
            buffer = ""
            while '\n' not in buffer:
                chunk = sock.recv(4096).decode()
                if not chunk:
                    raise ConnectionError("Socket closed before full response was received.")
                buffer += chunk
            
            response_str, _ = buffer.split('\n', 1)
            return json.loads(response_str)

    def create_double_array(self, array_id: str, values: List[float]):
        msg = Message(
            MessageType.CREATE_ARRAY,
            "interop-client",
            "master",
            {"arrayId": array_id, "dataType": "double", "values": values}
        )
        return self._send_and_receive(msg)

    def apply_operation(self, array_id: str, operation: str):
        msg = Message(
            MessageType.APPLY_OPERATION,
            "interop-client",
            "master",
            {"arrayId": array_id, "operation": operation}
        )
        return self._send_and_receive(msg)

    def get_result(self, array_id: str):
        msg = Message(
            MessageType.GET_RESULT,
            "interop-client",
            "master",
            {"arrayId": array_id}
        )
        return self._send_and_receive(msg)

def calculate_example1(arr: np.ndarray) -> np.ndarray:
    """Calculates the expected result for 'example1' operation."""
    return ((np.sin(arr) + np.cos(arr)) ** 2) / (np.sqrt(np.abs(arr)) + 1)

def main():
    if len(sys.argv) < 3:
        print("Usage: interop_client.py <master_host> <master_port>")
        sys.exit(1)

    master_host = sys.argv[1]
    master_port = int(sys.argv[2])
    client = InteropClient(master_host, master_port)
    
    array_id = "interop-test-array"
    original_data = np.array([10.0, 20.0, 30.0, 40.0, 50.0, 60.0])
    
    print("-" * 50)
    print(f"Starting test with Master at {master_host}:{master_port}")
    print("-" * 50)

    try:
        # 1. Create Array
        print(f"1. Creating array '{array_id}' with data: {original_data.tolist()}")
        response = client.create_double_array(array_id, original_data.tolist())
        print(f"   -> Master response: {response.get('data', {})}")
        if response.get('data', {}).get('status') != 'created':
            raise RuntimeError("Array creation failed.")
        
        time.sleep(2) # Give time for distribution

        # 2. Apply Operation
        operation = "example1"
        formula = "resultado = ((sin(x) + cos(x))^2) / (sqrt(abs(x)) + 1)"
        print(f"\n2. Applying operation '{operation}'")
        print(f"   Formula: {formula}")
        response = client.apply_operation(array_id, operation)
        print(f"   -> Master response: {response.get('data', {})}")
        if response.get('data', {}).get('status') != 'processing':
            raise RuntimeError("Apply operation failed to start.")

        time.sleep(3) # Give time for processing

        # 3. Get Result
        print(f"\n3. Getting result for array '{array_id}'...")
        response = client.get_result(array_id)
        
        status = response.get('data', {}).get('status')
        actual_result = response.get('data', {}).get('result')

        if status != 'complete' or actual_result is None:
            raise RuntimeError(f"Failed to get result. Status: {status}, Response: {response}")

        actual_result_np = np.array(actual_result)
        print(f"   -> Actual result received:   {actual_result_np.tolist()}")
        
        # 4. Verify Result
        print("\n4. Verifying result...")
        expected_result_np = calculate_example1(original_data)
        print(f"   -> Expected result should be: {expected_result_np.tolist()}")

        if np.allclose(actual_result_np, expected_result_np):
            print("\n" + "="*50)
            print("   ✅ SUCCESS: Actual result matches expected result.")
            print("="*50)
            sys.exit(0)
        else:
            print("\n" + "="*50)
            print("   ❌ FAILURE: Actual result does NOT match expected result.")
            print("="*50)
            sys.exit(1)

    except Exception as e:
        print(f"\n❌ An error occurred during the test: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 