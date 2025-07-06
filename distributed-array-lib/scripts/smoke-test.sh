#!/bin/bash

# Smoke test for distributed array library

echo "=== Distributed Array Library Smoke Test ==="
echo ""

# Function to test Java implementation
test_java() {
    echo "Testing Java implementation..."
    
    cd ../java
    
    # Compile if needed
    if [ ! -d "out" ]; then
        echo "Compiling Java code..."
        ./compile.sh
    fi
    
    # Start master
    java -cp "out:lib/gson-2.10.1.jar" master.MasterNode 5000 &
    MASTER_PID=$!
    sleep 2
    
    # Start workers
    for i in {0..1}; do
        java -cp "out:lib/gson-2.10.1.jar" worker.WorkerNode worker-$i localhost 5000 &
        WORKER_PIDS[$i]=$!
        sleep 1
    done
    
    # Run test commands
    echo "Creating arrays and running operations..."
    
    # Test commands
    {
        echo "create-double test-array-1 10000"
        sleep 2
        echo "apply test-array-1 example1"
        sleep 3
        echo "get test-array-1"
        sleep 1
        echo "create-int test-array-2 5000"
        sleep 2
        echo "apply test-array-2 example2"
        sleep 3
        echo "get test-array-2"
        sleep 1
        echo "exit"
    } | java -cp "out:lib/gson-2.10.1.jar" client.DistributedArrayClient localhost 5000
    
    # Kill processes
    kill $MASTER_PID ${WORKER_PIDS[@]} 2>/dev/null
    wait $MASTER_PID ${WORKER_PIDS[@]} 2>/dev/null
    
    echo "Java test completed!"
    echo ""
}

# Function to test Python implementation
test_python() {
    echo "Testing Python implementation..."
    
    cd ../python
    
    # Start master
    python3 master/master_node.py 5001 &
    MASTER_PID=$!
    sleep 2
    
    # Start workers
    for i in {0..1}; do
        python3 worker/worker_node.py worker-$i localhost 5001 &
        WORKER_PIDS[$i]=$!
        sleep 1
    done
    
    # Run test commands
    echo "Creating arrays and running operations..."
    
    # Test with expect or direct input
    python3 <<EOF
import sys
import os
sys.path.append(os.path.dirname(os.path.abspath('.')))
from client.distributed_array_client import DistributedArrayClient

client = DistributedArrayClient('localhost', 5001)

# Test operations
print("Creating double array...")
client.create_double_array('test-array-1', 10000)

print("Applying example1 operation...")
client.apply_operation('test-array-1', 'example1')

print("Getting result...")
client.get_result('test-array-1')

print("Creating int array...")
client.create_int_array('test-array-2', 5000)

print("Applying example2 operation...")
client.apply_operation('test-array-2', 'example2')

print("Getting result...")
client.get_result('test-array-2')

print("Test completed!")
EOF
    
    # Kill processes
    kill $MASTER_PID ${WORKER_PIDS[@]} 2>/dev/null
    wait $MASTER_PID ${WORKER_PIDS[@]} 2>/dev/null
    
    echo "Python test completed!"
    echo ""
}

# Main execution
echo "Select implementation to test:"
echo "1) Java"
echo "2) Python"
echo "3) Both"
read -p "Enter choice (1-3): " choice

case $choice in
    1)
        test_java
        ;;
    2)
        test_python
        ;;
    3)
        test_java
        test_python
        ;;
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac

echo "=== Smoke test completed ==="