# Manual Cluster Execution Guide

This guide details how to run the system components (master and workers) in separate terminals to observe their behavior in real-time.

## Prerequisites

1.  **Compile the Java code**: Make sure the Java code is compiled. If not, run from the project root:
    ```bash
    bash distributed-array-lib/java/compile.sh
    ```
2.  **Set up the Python environment**: Ensure you have created the Python virtual environment. If not, run from the project root:
    ```bash
    bash distributed-array-lib/scripts/setup_python_env.sh
    ```

---

## Option 1: 100% Java Cluster

Ideal for debugging the main logic of the Java implementation.

1.  **Open 4 or more terminals.** In all of them, make sure you are in the project root (`distributed-array-lib/`).
2.  **Execute a command in each terminal:**

    *   **Terminal 1 (Master):**
        ```bash
        java -cp "distributed-array-lib/java/out:distributed-array-lib/java/lib/gson-2.10.1.jar" master.MasterNode 5000
        ```

    *   **Terminal 2 (Worker 1):**
        ```bash
        java -cp "distributed-array-lib/java/out:distributed-array-lib/java/lib/gson-2.10.1.jar" worker.WorkerNode worker-java-0 localhost 5000
        ```

    *   **Terminal 3 (Worker 2):**
        ```bash
        java -cp "distributed-array-lib/java/out:distributed-array-lib/java/lib/gson-2.10.1.jar" worker.WorkerNode worker-java-1 localhost 5000
        ```

    *   **Terminal 4 (Worker 3):**
        ```bash
        java -cp "distributed-array-lib/java/out:distributed-array-lib/java/lib/gson-2.10.1.jar" worker.WorkerNode worker-java-2 localhost 5000
        ```
3.  **To send them work, open a 5th terminal (Client):**
    ```bash
    java -cp "distributed-array-lib/java/out:distributed-array-lib/java/lib/gson-2.10.1.jar" client.DistributedArrayClient localhost 5000
    ```
    You will see the master's logs in Terminal 1 and each worker's logs in their respective terminals in real-time.

---

## Option 2: 100% Python Cluster

To observe the behavior of the Python implementation.

1.  **Open 4 or more terminals** in the project root.
2.  **Execute the following commands**, one per terminal:

    *   **Terminal 1 (Master):**
        ```bash
        ./distributed-array-lib/python/venv/bin/python3 distributed-array-lib/python/master/master_node.py 5001
        ```

    *   **Terminal 2 (Worker 1):**
        ```bash
        ./distributed-array-lib/python/venv/bin/python3 distributed-array-lib/python/worker/worker_node.py worker-python-0 localhost 5001
        ```

    *   **Terminal 3 (Worker 2):**
        ```bash
        ./distributed-array-lib/python/venv/bin/python3 distributed-array-lib/python/worker/worker_node.py worker-python-1 localhost 5001
        ```

    *   **Terminal 4 (Worker 3):**
        ```bash
        ./distributed-array-lib/python/venv/bin/python3 distributed-array-lib/python/worker/worker_node.py worker-python-2 localhost 5001
        ```
3.  **To send them work, open a 5th terminal (Client):**
    ```bash
    ./distributed-array-lib/python/venv/bin/python3 distributed-array-lib/python/client/distributed_array_client.py localhost 5001
    ```

---

## Option 3: Mixed Cluster (Interoperability)

This is the setup to test interoperability between Java and Python, with a Java master and workers from both languages.

1.  **Open 4 or more terminals** in the project root.
2.  **Execute the following commands**, one per terminal (we will use port 6000 to avoid confusion):

    *   **Terminal 1 (Java Master):**
        ```bash
        java -cp "distributed-array-lib/java/out:distributed-array-lib/java/lib/gson-2.10.1.jar" master.MasterNode 6000
        ```

    *   **Terminal 2 (Java Worker):**
        ```bash
        java -cp "distributed-array-lib/java/out:distributed-array-lib/java/lib/gson-2.10.1.jar" worker.WorkerNode worker-java-0 localhost 6000
        ```

    *   **Terminal 3 (Python Worker 1):**
        ```bash
        ./distributed-array-lib/python/venv/bin/python3 distributed-array-lib/python/worker/worker_node.py worker-python-0 localhost 6000
        ```

    *   **Terminal 4 (Python Worker 2):**
        ```bash
        ./distributed-array-lib/python/venv/bin/python3 distributed-array-lib/python/worker/worker_node.py worker-python-1 localhost 6000
        ```

3.  **To send them work, open a 5th terminal (Python Client):**
    ```bash
    ./distributed-array-lib/python/venv/bin/python3 distributed-array-lib/python/client/interop_client.py localhost 6000
    ```

With this setup, you can see in Terminal 1 how the Java master registers all workers and distributes work among them, regardless of the language they are written in. 