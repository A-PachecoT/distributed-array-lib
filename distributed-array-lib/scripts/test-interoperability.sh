#!/bin/bash

# Script to test interoperability between Java and Python components

echo "=== Distributed Array Interoperability Test ==="
echo "This test demonstrates a mixed-language cluster."
echo "Master: Java"
echo "Workers: 1 Java, 1 Python"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Cleaning up processes...${NC}"
    # Kill all child processes of this script
    pkill -P $$
    wait
    echo "Cleanup complete."
}

# Trap EXIT signal to ensure cleanup runs
trap cleanup EXIT

# --- Java Setup ---
echo -e "${YELLOW}Setting up Java components...${NC}"
cd ../java

# Compile if needed
if [ ! -d "out" ]; then
    echo "Compiling Java code..."
    if ! ./compile.sh; then
        echo -e "${RED}Java compilation failed!${NC}"
        exit 1
    fi
fi

# Start Java master
echo -e "${GREEN}Starting Java Master node on port 6000${NC}"
java -cp "out:lib/gson-2.10.1.jar" master.MasterNode 6000 &
MASTER_PID=$!
sleep 3 # Give master time to start

# Start Java worker
echo -e "${GREEN}Starting Java Worker-J0 on port 6000${NC}"
java -cp "out:lib/gson-2.10.1.jar" worker.WorkerNode worker-J0 localhost 6000 &
WORKER_J0_PID=$!
sleep 2

# --- Python Setup ---
echo -e "\n${YELLOW}Setting up Python components...${NC}"

# Check for venv
if [ ! -d "../python/venv" ]; then
    echo -e "${RED}Python virtual environment not found!${NC}"
    echo "Please run 'bash scripts/setup_python_env.sh' first."
    exit 1
fi
PYTHON_EXEC="../python/venv/bin/python3"

cd ../python

# Start Python worker
echo -e "${GREEN}Starting Python Worker-P0 on port 6000${NC}"
$PYTHON_EXEC worker/worker_node.py worker-P0 localhost 6000 &
WORKER_P0_PID=$!
sleep 2

# --- Run Test ---
echo -e "\n${YELLOW}=== Running Interoperability Test ===${NC}"
# This client will create an array, apply an operation, get the result,
# and verify it against the expected output.
$PYTHON_EXEC client/interop_client.py localhost 6000

# Capture exit code of the test
TEST_EXIT_CODE=$?

# --- Teardown ---
echo -e "\n${YELLOW}Test finished. Shutting down cluster...${NC}"

# The cleanup function will be called automatically on exit.
# We just need to exit with the test's status code.
exit $TEST_EXIT_CODE 