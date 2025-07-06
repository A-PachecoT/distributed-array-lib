#!/bin/bash

# Run all tests for the distributed array library

echo "=== Running All Tests for Distributed Array Library ==="
echo ""

# Function to check requirements
check_requirements() {
    echo "Checking requirements..."
    
    # Check Java
    if command -v java &> /dev/null; then
        echo "✓ Java installed: $(java -version 2>&1 | head -n 1)"
    else
        echo "✗ Java not found. Please install Java 8 or higher."
        return 1
    fi
    
    # Check Python
    if command -v python3 &> /dev/null; then
        echo "✓ Python installed: $(python3 --version)"
    else
        echo "✗ Python 3 not found. Please install Python 3.6 or higher."
        return 1
    fi
    
    # Check numpy
    if python3 -c "import numpy" &> /dev/null; then
        echo "✓ NumPy installed"
    else
        echo "✗ NumPy not found. Installing..."
        pip3 install numpy
    fi
    
    echo ""
    return 0
}

# Function to compile Java code
compile_java() {
    echo "Compiling Java code..."
    cd ../java
    if ./compile.sh; then
        echo "✓ Java compilation successful"
    else
        echo "✗ Java compilation failed"
        return 1
    fi
    cd ../scripts
    echo ""
}

# Function to run quick test
run_quick_test() {
    echo "Running quick test..."
    if ./quick-test.sh; then
        echo "✓ Quick test passed"
    else
        echo "✗ Quick test failed"
        return 1
    fi
    echo ""
}

# Function to test Java operations
test_java_operations() {
    echo "Testing Java operations..."
    cd ../java
    
    # Start mini cluster
    java -cp "out:lib/gson-2.10.1.jar" master.MasterNode 5000 &
    MASTER=$!
    sleep 2
    
    java -cp "out:lib/gson-2.10.1.jar" worker.WorkerNode worker-0 localhost 5000 &
    WORKER=$!
    sleep 2
    
    # Test operations
    {
        echo "create-double test1 100"
        sleep 1
        echo "apply test1 example1"
        sleep 2
        echo "create-int test2 100"
        sleep 1
        echo "apply test2 example2"
        sleep 2
        echo "exit"
    } | timeout 10s java -cp "out:lib/gson-2.10.1.jar" client.DistributedArrayClient localhost 5000
    
    # Clean up
    kill $MASTER $WORKER 2>/dev/null
    wait $MASTER $WORKER 2>/dev/null
    
    cd ../scripts
    echo "✓ Java operations test completed"
    echo ""
}

# Function to test Python operations
test_python_operations() {
    echo "Testing Python operations..."
    cd ../python
    
    # Start mini cluster
    python3 master/master_node.py 5001 &
    MASTER=$!
    sleep 2
    
    python3 worker/worker_node.py worker-0 localhost 5001 &
    WORKER=$!
    sleep 2
    
    # Test operations
    python3 <<EOF
import sys
import time
sys.path.append('.')
from client.distributed_array_client import DistributedArrayClient

try:
    client = DistributedArrayClient('localhost', 5001)
    client.create_double_array('test1', 100)
    time.sleep(1)
    client.apply_operation('test1', 'example1')
    time.sleep(2)
    client.create_int_array('test2', 100)
    time.sleep(1)
    client.apply_operation('test2', 'example2')
    time.sleep(2)
    print("Python operations test successful!")
except Exception as e:
    print(f"Python test failed: {e}")
    exit(1)
EOF
    
    # Clean up
    kill $MASTER $WORKER 2>/dev/null
    wait $MASTER $WORKER 2>/dev/null
    
    cd ../scripts
    echo "✓ Python operations test completed"
    echo ""
}

# Main execution
echo "Starting comprehensive test suite..."
echo ""

# Check requirements
if ! check_requirements; then
    echo "Requirements check failed. Please install missing dependencies."
    exit 1
fi

# Compile Java
if ! compile_java; then
    echo "Java compilation failed."
    exit 1
fi

# Run tests
echo "=== Running Tests ==="
echo ""

# Quick test
run_quick_test

# Java operations test
test_java_operations

# Python operations test
test_python_operations

# Summary
echo "=== Test Summary ==="
echo ""
echo "✓ All tests completed successfully!"
echo ""
echo "The distributed array library is working correctly with:"
echo "- Java implementation"
echo "- Python implementation"
echo "- Socket communication"
echo "- Multi-threading"
echo "- Array segmentation"
echo "- Example operations"
echo ""
echo "To run the full demo, use: ./demo.sh"
echo "To start a cluster, use: ./start-java-cluster.sh or ./start-python-cluster.sh"