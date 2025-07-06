#!/bin/bash

# Start Java distributed array cluster

echo "Starting Java distributed array cluster..."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Java is not installed. Please install Java 8 or higher."
    exit 1
fi

# Compile Java code if not already compiled
cd ../java
if [ ! -d "out" ]; then
    echo "Compiling Java code..."
    ./compile.sh
fi

# Start master node
echo "Starting master node on port 5000..."
java -cp "out:lib/gson-2.10.1.jar" master.MasterNode 5000 &
MASTER_PID=$!
echo "Master node PID: $MASTER_PID"

# Wait for master to start
sleep 2

# Start worker nodes
for i in {0..2}; do
    echo "Starting worker-$i..."
    java -cp "out:lib/gson-2.10.1.jar" worker.WorkerNode worker-$i localhost 5000 &
    WORKER_PID=$!
    echo "Worker-$i PID: $WORKER_PID"
    sleep 1
done

echo "Java cluster started successfully!"
echo "Master node running on port 5000"
echo "3 worker nodes connected"
echo ""
echo "To start a client, run:"
echo "java -cp 'out:lib/gson-2.10.1.jar' client.DistributedArrayClient localhost 5000"
echo ""
echo "Press Ctrl+C to stop the cluster"

# Wait for user to stop
wait