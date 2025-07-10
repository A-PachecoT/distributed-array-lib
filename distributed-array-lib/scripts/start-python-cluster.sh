#!/bin/bash

# Start Python distributed array cluster

echo "Starting Python distributed array cluster..."

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "Python 3 is not installed. Please install Python 3.6 or higher."
    exit 1
fi

# Check if numpy is installed
if ! python3 -c "import numpy" &> /dev/null; then
    echo "NumPy is not installed. Installing numpy..."
    pip3 install numpy
fi

# Check for venv
if [ ! -d "../python/venv" ]; then
    echo "Python virtual environment not found. Please run 'setup_python_env.sh' in the 'scripts' directory first."
    exit 1
fi
PYTHON_EXEC="../python/venv/bin/python3"

cd ../python

# Start master node
echo "Starting master node on port 5001..."
$PYTHON_EXEC master/master_node.py 5001 &
MASTER_PID=$!
echo "Master node PID: $MASTER_PID"

# Wait for master to start
sleep 2

# Start worker nodes
for i in {0..2}; do
    echo "Starting worker-$i..."
    $PYTHON_EXEC worker/worker_node.py worker-$i localhost 5001 &
    WORKER_PID=$!
    echo "Worker-$i PID: $WORKER_PID"
    sleep 1
done

echo "Python cluster started successfully!"
echo "Master node running on port 5001"
echo "3 worker nodes connected"
echo ""
echo "To start a client, run:"
echo "python3 client/distributed_array_client.py localhost 5001"
echo ""
echo "Press Ctrl+C to stop the cluster"

# Wait for user to stop
wait