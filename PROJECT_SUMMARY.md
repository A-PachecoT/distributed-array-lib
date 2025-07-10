# Final Project: Distributed Array Library

## Executive Summary

A distributed, concurrent, and fault-tolerant library for distributed data structures (DArrayInt and DArrayDouble) has been implemented in Java and Python, using only native TCP sockets and threads.

## Project Structure

```
distributed-array-lib/
├── java/                    # Java Implementation
│   ├── src/
│   │   ├── common/         # Shared classes
│   │   ├── master/         # Master node
│   │   ├── worker/         # Worker nodes
│   │   └── client/         # Client
│   └── compile.sh          # Compilation script
├── python/                  # Python Implementation
│   ├── common/             # Shared modules
│   ├── master/             # Master node
│   ├── worker/             # Worker nodes
│   └── client/             # Client
├── scripts/                 # Deployment scripts
│   ├── start-java-cluster.sh
│   ├── start-python-cluster.sh
│   ├── smoke-test.sh
│   ├── quick-test.sh
│   ├── demo.sh
│   └── run-all-tests.sh
├── docs/                    # Documentation
│   └── protocol.md         # Communication protocol
└── README.md               # Main documentation
```

## Implemented Features

### 1. Distributed Data Types
- **DArrayInt**: Distributed integer arrays
- **DArrayDouble**: Distributed double arrays
- Automatic segmentation across nodes
- Equitable load distribution

### 2. Parallel Processing
- Use of all available cores on each node
- ThreadPool in Java / ThreadPoolExecutor in Python
- Concurrent processing of segments

### 3. Socket Communication
- JSON-based protocol
- Native TCP sockets
- No external frameworks

### 4. Fault Tolerance
- Heartbeat system
- Detection of failed nodes
- Complete activity logging

### 5. Implemented Examples

#### Example 1: Mathematical Operations
```
result = ((sin(x) + cos(x))^2) / (sqrt(abs(x)) + 1)
```

#### Example 2: Conditional Evaluation
```
If x is a multiple of 3 or is between 500 and 1000:
    result = (x * log(x)) % 7
```

## How to Run

### **IMPORTANT**: Python Environment Setup

Before running any Python-related script, you need to set up the virtual environment. From the project root, run the following command once:

```bash
bash scripts/setup_python_env.sh
```

This script will create a virtual environment in `python/venv` and install the necessary dependencies.

### Option 1: Full Demo
```bash
cd distributed-array-lib/scripts
./demo.sh
```

### Option 2: Java Cluster
```bash
# Terminal 1 - Start cluster
cd distributed-array-lib/scripts
./start-java-cluster.sh

# Terminal 2 - Client
cd distributed-array-lib/java
java -cp "out:lib/gson-2.10.1.jar" client.DistributedArrayClient localhost 5000
```

### Option 3: Python Cluster
```bash
# Terminal 1 - Start cluster
cd distributed-array-lib/scripts
./start-python-cluster.sh

# Terminal 2 - Client
cd distributed-array-lib/python
./venv/bin/python3 client/distributed_array_client.py localhost 5001
```

## Client Commands

- `create-int <id> <size>` - Create integer array
- `create-double <id> <size>` - Create double array
- `apply <id> <operation>` - Apply operation (example1 or example2)
- `get <id>` - Get result
- `help` - Show help
- `exit` - Quit

## Tests

```bash
# Quick test
./scripts/quick-test.sh

# Full test suite
./scripts/run-all-tests.sh

# Interactive demo
./scripts/demo.sh
```

## Logs

- Java: `master.log`, `worker-*.log`
- Python: `master.log`, `worker-*.log`

## Implementation Notes

1.  **Modularity**: Completely modular and extensible design
2.  **Concurrency**: Correct use of threads and synchronization
3.  **Scalability**: Supports N worker nodes
4.  **Portability**: Works on Linux, macOS, and Windows
5.  **No dependencies**: Only uses standard libraries (except Gson for Java and NumPy for Python)

## Possible Future Improvements

1.  Implement full active replication
2.  Add automatic recovery with replicas
3.  Implement Example 3 (failure simulation)
4.  Optimize serialization for very large arrays
5.  Add real-time performance metrics 