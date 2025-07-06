# Distributed Array Library

A concurrent, distributed, and fault-tolerant library for distributed arrays (DArrayInt and DArrayDouble) implemented in Java and Python using only native sockets and threads.

## Features

- **Distributed Arrays**: Support for integer (DArrayInt) and double (DArrayDouble) arrays
- **Automatic Segmentation**: Arrays are automatically segmented across worker nodes
- **Parallel Processing**: Multi-threaded processing on each node using all CPU cores
- **Fault Tolerance**: Heartbeat mechanism for node health monitoring
- **Cross-Language**: Implementations in both Java and Python
- **Native Implementation**: Uses only sockets and threads, no external frameworks

## Architecture

```
Master Node
    |
    +-- Worker 0
    +-- Worker 1
    +-- Worker 2
    ...
    +-- Worker N
```

## Quick Start

### Java Implementation

1. Compile the Java code:
```bash
cd java
./compile.sh
```

2. Start the cluster:
```bash
cd scripts
./start-java-cluster.sh
```

3. In another terminal, run the client:
```bash
cd java
java -cp "out:lib/gson-2.10.1.jar" client.DistributedArrayClient localhost 5000
```

### Python Implementation

1. Install dependencies:
```bash
pip3 install numpy
```

2. Start the cluster:
```bash
cd scripts
./start-python-cluster.sh
```

3. In another terminal, run the client:
```bash
cd python
python3 client/distributed_array_client.py localhost 5001
```

## Client Commands

- `create-int <array_id> <size>` - Create an integer array
- `create-double <array_id> <size>` - Create a double array
- `apply <array_id> <operation>` - Apply operation (example1 or example2)
- `get <array_id>` - Get operation result
- `help` - Show help
- `exit` - Exit client

## Examples

### Example 1: Mathematical Operations
Applies the formula: `((sin(x) + cos(x))^2) / (sqrt(abs(x)) + 1)` to each element

### Example 2: Conditional Evaluation
For integers, applies: if `x % 3 == 0` or `500 <= x <= 1000`, then `(x * log(x)) % 7`

## Testing

Run the smoke test:
```bash
cd scripts
./smoke-test.sh
```

Run the quick test:
```bash
cd scripts
./quick-test.sh
```

## Project Structure

```
distributed-array-lib/
├── java/
│   ├── src/
│   │   ├── common/      # Shared classes (Message, DArray types)
│   │   ├── master/      # Master node implementation
│   │   ├── worker/      # Worker node implementation
│   │   └── client/      # Client application
│   ├── lib/             # External libraries (Gson)
│   └── compile.sh       # Compilation script
├── python/
│   ├── common/          # Shared modules
│   ├── master/          # Master node implementation
│   ├── worker/          # Worker node implementation
│   └── client/          # Client application
├── scripts/             # Deployment and testing scripts
└── docs/                # Documentation
```

## Communication Protocol

The system uses JSON messages for communication. See `docs/protocol.md` for details.

## Logging

- Java: Logs are written to `master.log` and `worker-*.log`
- Python: Logs are written to console and log files

## Performance

- Automatic parallelization using all available CPU cores
- Efficient array segmentation for load distribution
- Minimal network overhead with binary data transfer