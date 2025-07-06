# Proyecto Final: Librería Distribuida para Arrays

## Resumen Ejecutivo

Se ha implementado una librería distribuida, concurrente y tolerante a fallos para estructuras de datos distribuidas (DArrayInt y DArrayDouble) en Java y Python, utilizando únicamente sockets TCP e hilos nativos.

## Estructura del Proyecto

```
distributed-array-lib/
├── java/                    # Implementación en Java
│   ├── src/
│   │   ├── common/         # Clases compartidas
│   │   ├── master/         # Nodo maestro
│   │   ├── worker/         # Nodos trabajadores
│   │   └── client/         # Cliente
│   └── compile.sh          # Script de compilación
├── python/                  # Implementación en Python
│   ├── common/             # Módulos compartidos
│   ├── master/             # Nodo maestro
│   ├── worker/             # Nodos trabajadores
│   └── client/             # Cliente
├── scripts/                 # Scripts de despliegue
│   ├── start-java-cluster.sh
│   ├── start-python-cluster.sh
│   ├── smoke-test.sh
│   ├── quick-test.sh
│   ├── demo.sh
│   └── run-all-tests.sh
├── docs/                    # Documentación
│   └── protocol.md         # Protocolo de comunicación
└── README.md               # Documentación principal
```

## Características Implementadas

### 1. Tipos de Datos Distribuidos
- **DArrayInt**: Arrays de enteros distribuidos
- **DArrayDouble**: Arrays de decimales distribuidos
- Segmentación automática entre nodos
- Distribución equitativa de carga

### 2. Procesamiento Paralelo
- Uso de todos los núcleos disponibles en cada nodo
- ThreadPool en Java / ThreadPoolExecutor en Python
- Procesamiento concurrente de segmentos

### 3. Comunicación por Sockets
- Protocolo basado en JSON
- Sockets TCP nativos
- Sin frameworks externos

### 4. Tolerancia a Fallos
- Sistema de heartbeat (latidos)
- Detección de nodos caídos
- Logging completo de actividades

### 5. Ejemplos Implementados

#### Ejemplo 1: Operaciones Matemáticas
```
resultado = ((sin(x) + cos(x))^2) / (sqrt(abs(x)) + 1)
```

#### Ejemplo 2: Evaluación Condicional
```
Si x es múltiplo de 3 o está entre 500 y 1000:
    resultado = (x * log(x)) % 7
```

## Cómo Ejecutar

### Opción 1: Demo Completa
```bash
cd distributed-array-lib/scripts
./demo.sh
```

### Opción 2: Cluster Java
```bash
# Terminal 1 - Iniciar cluster
cd distributed-array-lib/scripts
./start-java-cluster.sh

# Terminal 2 - Cliente
cd distributed-array-lib/java
java -cp "out:lib/gson-2.10.1.jar" client.DistributedArrayClient localhost 5000
```

### Opción 3: Cluster Python
```bash
# Terminal 1 - Iniciar cluster
cd distributed-array-lib/scripts
./start-python-cluster.sh

# Terminal 2 - Cliente
cd distributed-array-lib/python
python3 client/distributed_array_client.py localhost 5001
```

## Comandos del Cliente

- `create-int <id> <tamaño>` - Crear array de enteros
- `create-double <id> <tamaño>` - Crear array de decimales
- `apply <id> <operación>` - Aplicar operación (example1 o example2)
- `get <id>` - Obtener resultado
- `help` - Mostrar ayuda
- `exit` - Salir

## Pruebas

```bash
# Prueba rápida
./scripts/quick-test.sh

# Prueba completa
./scripts/run-all-tests.sh

# Demo interactiva
./scripts/demo.sh
```

## Logs

- Java: `master.log`, `worker-*.log`
- Python: `master.log`, `worker-*.log`

## Notas de Implementación

1. **Modularidad**: Diseño completamente modular y extensible
2. **Concurrencia**: Uso correcto de hilos y sincronización
3. **Escalabilidad**: Soporta N nodos trabajadores
4. **Portabilidad**: Funciona en Linux, macOS y Windows
5. **Sin dependencias**: Solo usa bibliotecas estándar (excepto Gson para Java y NumPy para Python)

## Posibles Mejoras Futuras

1. Implementar replicación activa completa
2. Añadir recuperación automática con réplicas
3. Implementar Ejemplo 3 (simulación de fallos)
4. Optimizar serialización para arrays muy grandes
5. Añadir métricas de rendimiento en tiempo real