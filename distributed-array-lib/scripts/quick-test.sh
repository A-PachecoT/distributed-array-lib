#!/bin/bash

# Quick test script for distributed array library

echo "=== Quick Test for Distributed Array Library ==="
echo ""

# Test Java
echo "1. Testing Java Implementation..."
cd ../java

# Start Java master
timeout 20s java -cp "out:lib/gson-2.10.1.jar" master.MasterNode 5000 &
JAVA_MASTER=$!
sleep 2

# Start Java workers
for i in 0 1; do
    timeout 20s java -cp "out:lib/gson-2.10.1.jar" worker.WorkerNode worker-$i localhost 5000 &
    JAVA_WORKERS[$i]=$!
done
sleep 2

# Test Java client
echo "Testing Java client..."
echo -e "create-double array1 100\nexit" | timeout 5s java -cp "out:lib/gson-2.10.1.jar" client.DistributedArrayClient localhost 5000

# Kill Java processes
kill $JAVA_MASTER ${JAVA_WORKERS[@]} 2>/dev/null
wait $JAVA_MASTER ${JAVA_WORKERS[@]} 2>/dev/null

echo ""
echo "2. Testing Python Implementation..."
cd ../python

# Start Python master
timeout 20s python3 master/master_node.py 5001 &
PY_MASTER=$!
sleep 2

# Start Python workers
for i in 0 1; do
    timeout 20s python3 worker/worker_node.py worker-$i localhost 5001 &
    PY_WORKERS[$i]=$!
done
sleep 2

# Test Python client
echo "Testing Python client..."
python3 -c "
import sys
sys.path.append('.')
from client.distributed_array_client import DistributedArrayClient
client = DistributedArrayClient('localhost', 5001)
client.create_double_array('array1', 100)
print('Python test successful!')
"

# Kill Python processes
kill $PY_MASTER ${PY_WORKERS[@]} 2>/dev/null
wait $PY_MASTER ${PY_WORKERS[@]} 2>/dev/null

echo ""
echo "=== Quick test completed ==="