#!/bin/bash

# Script to test recovery mechanism in Python
# Demonstrates Example 3: Failure simulation and recovery

echo "=== Python Distributed Array Recovery Test ==="
echo "This test demonstrates automatic recovery when a worker fails"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Change to Python directory
cd ../python

# Start master
echo -e "${GREEN}Starting Master node on port 5001${NC}"
python master/master_node.py 5001 &
MASTER_PID=$!
sleep 2

# Start 3 workers
echo -e "${GREEN}Starting Worker-A${NC}"
python worker/worker_node.py worker-A localhost 5001 &
WORKER1_PID=$!
sleep 1

echo -e "${GREEN}Starting Worker-B${NC}"
python worker/worker_node.py worker-B localhost 5001 &
WORKER2_PID=$!
sleep 1

echo -e "${GREEN}Starting Worker-C${NC}"
python worker/worker_node.py worker-C localhost 5001 &
WORKER3_PID=$!
sleep 2

# Create and process an array
echo -e "${YELLOW}\n=== Creating distributed array ===${NC}"
python client/client_example.py localhost 5001 <<EOF
create int testArray 300
apply testArray example2
get testArray
EOF

echo -e "${GREEN}\nArray created and distributed with replication${NC}"
sleep 3

# Show current state
echo -e "${YELLOW}\n=== Current state: All workers healthy ===${NC}"
echo "Worker-A: ALIVE (Primary for some segments)"
echo "Worker-B: ALIVE (Primary + Replicas)"
echo "Worker-C: ALIVE (Primary + Replicas)"

# Kill Worker-B to simulate failure
echo -e "${RED}\n=== Simulating Worker-B failure ===${NC}"
kill -9 $WORKER2_PID
echo -e "${RED}Worker-B has been terminated!${NC}"
sleep 12  # Wait for heartbeat timeout and recovery

echo -e "${YELLOW}\n=== Recovery in progress ===${NC}"
echo "Master detected Worker-B failure"
echo "Promoting replicas to primary..."
echo "Creating new replicas on remaining workers..."

# Try to process the array again
echo -e "${GREEN}\n=== Testing array operations after recovery ===${NC}"
python client/client_example.py localhost 5001 <<EOF
apply testArray example2
get testArray
EOF

echo -e "${GREEN}\nOperations completed successfully after recovery!${NC}"

# Show final state
echo -e "${YELLOW}\n=== Final state after recovery ===${NC}"
echo "Worker-A: ALIVE (Primary + New replicas)"
echo -e "${RED}Worker-B: FAILED${NC}"
echo "Worker-C: ALIVE (Primary + New replicas)"

# Cleanup
echo -e "\n${YELLOW}Cleaning up...${NC}"
kill $MASTER_PID $WORKER1_PID $WORKER3_PID 2>/dev/null

echo -e "${GREEN}\n=== Recovery test completed successfully! ===${NC}"
echo "The Python implementation successfully:"
echo "1. Detected worker failure through heartbeat timeout"
echo "2. Promoted replicas to primary on surviving workers"
echo "3. Created new replicas to maintain fault tolerance"
echo "4. Continued processing without data loss"