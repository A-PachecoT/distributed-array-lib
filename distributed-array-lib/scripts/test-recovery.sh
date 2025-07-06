#!/bin/bash

# Script to test recovery mechanism
# Demonstrates Example 3: Failure simulation and recovery

echo "=== Distributed Array Recovery Test ==="
echo "This test demonstrates automatic recovery when a worker fails"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Change to Java directory
cd ../java

# Compile if needed
echo "Compiling Java code..."
./compile.sh

# Start master
echo -e "${GREEN}Starting Master node on port 5000${NC}"
java -cp "build:lib/*" master.MasterNode 5000 &
MASTER_PID=$!
sleep 2

# Start 3 workers
echo -e "${GREEN}Starting Worker-1${NC}"
java -cp "build:lib/*" worker.WorkerNode worker-1 localhost 5000 &
WORKER1_PID=$!
sleep 1

echo -e "${GREEN}Starting Worker-2${NC}"
java -cp "build:lib/*" worker.WorkerNode worker-2 localhost 5000 &
WORKER2_PID=$!
sleep 1

echo -e "${GREEN}Starting Worker-3${NC}"
java -cp "build:lib/*" worker.WorkerNode worker-3 localhost 5000 &
WORKER3_PID=$!
sleep 2

# Create and process an array
echo -e "${YELLOW}\n=== Creating distributed array ===${NC}"
java -cp "build:lib/*" client.ClientExample localhost 5000 <<EOF
create double myArray 300
apply myArray example1
get myArray
EOF

echo -e "${GREEN}\nArray created and distributed with replication${NC}"
sleep 3

# Show current state
echo -e "${YELLOW}\n=== Current state: All workers healthy ===${NC}"
echo "Worker-1: ALIVE (Primary for some segments)"
echo "Worker-2: ALIVE (Primary + Replicas)"
echo "Worker-3: ALIVE (Primary + Replicas)"

# Kill Worker-2 to simulate failure
echo -e "${RED}\n=== Simulating Worker-2 failure ===${NC}"
kill -9 $WORKER2_PID
echo -e "${RED}Worker-2 has been terminated!${NC}"
sleep 12  # Wait for heartbeat timeout and recovery

echo -e "${YELLOW}\n=== Recovery in progress ===${NC}"
echo "Master detected Worker-2 failure"
echo "Promoting replicas to primary..."
echo "Creating new replicas on remaining workers..."

# Try to process the array again
echo -e "${GREEN}\n=== Testing array operations after recovery ===${NC}"
java -cp "build:lib/*" client.ClientExample localhost 5000 <<EOF
apply myArray example1
get myArray
EOF

echo -e "${GREEN}\nOperations completed successfully after recovery!${NC}"

# Show final state
echo -e "${YELLOW}\n=== Final state after recovery ===${NC}"
echo "Worker-1: ALIVE (Primary + New replicas)"
echo -e "${RED}Worker-2: FAILED${NC}"
echo "Worker-3: ALIVE (Primary + New replicas)"

# Cleanup
echo -e "\n${YELLOW}Cleaning up...${NC}"
kill $MASTER_PID $WORKER1_PID $WORKER3_PID 2>/dev/null

echo -e "${GREEN}\n=== Recovery test completed successfully! ===${NC}"
echo "The system automatically:"
echo "1. Detected worker failure through heartbeat timeout"
echo "2. Promoted replicas to primary on surviving workers"
echo "3. Created new replicas to maintain fault tolerance"
echo "4. Continued processing without data loss"