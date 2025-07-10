#!/bin/bash

# Comprehensive demonstration of the distributed array library

echo "=== Distributed Array Library Demonstration ==="
echo ""
echo "This demo shows the key features of the distributed array library:"
echo "1. Distributed array creation (int and double)"
echo "2. Parallel processing across multiple nodes"
echo "3. Example operations (mathematical and conditional)"
echo "4. Cross-language support (Java and Python)"
echo ""

# Function to run Java demo
run_java_demo() {
    echo "=== Java Implementation Demo ==="
    cd ../java
    
    # Compile if needed
    if [ ! -d "out" ]; then
        echo "Compiling Java code..."
        ./compile.sh
    fi
    
    # Start master
    echo "Starting Java master node..."
    java -cp "out:lib/gson-2.10.1.jar" master.MasterNode 5000 &
    MASTER_PID=$!
    sleep 2
    
    # Start 3 workers
    echo "Starting 3 Java worker nodes..."
    for i in {0..2}; do
        java -cp "out:lib/gson-2.10.1.jar" worker.WorkerNode worker-$i localhost 5000 &
        WORKER_PIDS[$i]=$!
        sleep 1
    done
    
    echo ""
    echo "Java cluster is running with 1 master and 3 workers"
    echo ""
    
    # Run demo commands
    echo "Running demonstration..."
    {
        echo "help"
        sleep 1
        echo "create-double math-array 10000"
        sleep 2
        echo "apply math-array example1"
        sleep 3
        echo "get math-array"
        sleep 1
        echo "create-int cond-array 5000"
        sleep 2
        echo "apply cond-array example2"
        sleep 3
        echo "get cond-array"
        sleep 1
        echo "exit"
    } | java -cp "out:lib/gson-2.10.1.jar" client.DistributedArrayClient localhost 5000
    
    # Clean up
    echo ""
    echo "Shutting down Java cluster..."
    kill $MASTER_PID ${WORKER_PIDS[@]} 2>/dev/null
    wait $MASTER_PID ${WORKER_PIDS[@]} 2>/dev/null
    
    echo "Java demo completed!"
    echo ""
}

# Function to run Python demo
run_python_demo() {
    echo "=== Python Implementation Demo ==="
    
    # Check for venv
    if [ ! -d "../python/venv" ]; then
        echo "Python virtual environment not found. Please run 'setup_python_env.sh' in the 'scripts' directory first."
        return 1
    fi
    PYTHON_EXEC="../python/venv/bin/python3"

    cd ../python
    
    # Start master
    echo "Starting Python master node..."
    $PYTHON_EXEC master/master_node.py 5001 &
    MASTER_PID=$!
    sleep 2
    
    # Start 3 workers
    echo "Starting 3 Python worker nodes..."
    for i in {0..2}; do
        $PYTHON_EXEC worker/worker_node.py worker-$i localhost 5001 &
        WORKER_PIDS[$i]=$!
        sleep 1
    done
    
    echo ""
    echo "Python cluster is running with 1 master and 3 workers"
    echo ""
    
    # Run demo
    echo "Running demonstration..."
    $PYTHON_EXEC <<EOF
import sys
import time
sys.path.append('.')
from client.distributed_array_client import DistributedArrayClient

client = DistributedArrayClient('localhost', 5001)

print("Creating double array with 10000 elements...")
client.create_double_array('math-array', 10000)
time.sleep(2)

print("\nApplying Example 1 (mathematical operations)...")
client.apply_operation('math-array', 'example1')
time.sleep(3)

print("\nGetting results...")
client.get_result('math-array')
time.sleep(1)

print("\nCreating int array with 5000 elements...")
client.create_int_array('cond-array', 5000)
time.sleep(2)

print("\nApplying Example 2 (conditional evaluation)...")
client.apply_operation('cond-array', 'example2')
time.sleep(3)

print("\nGetting results...")
client.get_result('cond-array')

print("\nDemo completed!")
EOF
    
    # Clean up
    echo ""
    echo "Shutting down Python cluster..."
    kill $MASTER_PID ${WORKER_PIDS[@]} 2>/dev/null
    wait $MASTER_PID ${WORKER_PIDS[@]} 2>/dev/null
    
    echo "Python demo completed!"
    echo ""
}

# Main menu
echo "Select implementation to demonstrate:"
echo "1) Java"
echo "2) Python"
echo "3) Both"
read -p "Enter choice (1-3): " choice

case $choice in
    1)
        run_java_demo
        ;;
    2)
        run_python_demo
        ;;
    3)
        run_java_demo
        echo "Press Enter to continue with Python demo..."
        read
        run_python_demo
        ;;
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac

echo ""
echo "=== Demonstration Complete ==="
echo ""
echo "Key features demonstrated:"
echo "✓ Distributed array creation and segmentation"
echo "✓ Parallel processing using multiple threads per node"
echo "✓ Mathematical operations (Example 1)"
echo "✓ Conditional evaluation (Example 2)"
echo "✓ Worker node coordination"
echo "✓ Client-server communication"
echo ""
echo "Check the log files for detailed execution information:"
echo "- Java: master.log, worker-*.log"
echo "- Python: master.log, worker-*.log"
echo ""
echo "Before running the Python demo, make sure you have set up the environment by running:"
echo "bash scripts/setup_python_env.sh"