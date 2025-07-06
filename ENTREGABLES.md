# Proyecto Final - Entregables

## Equipo
- André Pacheco
- Arbues Perez  
- Sergio Pezo

## GitHub Repository
https://github.com/A-PachecoT/distributed-array-lib

## Estructura del Proyecto

### 1. Librería Distribuida (distributed-array-lib/)
- **Java**: Implementación completa (Master, Worker, Cliente)
- **Python**: Implementación completa (Master, Worker, Cliente)
- **TypeScript**: Cliente adicional
- **Scripts**: Automatización de despliegue y pruebas

### 2. Presentación
- **presentacion_array_distribuido.tex**: Presentación en Beamer
- **presentacion_array_distribuido.pdf**: PDF compilado

### 3. Documentación
- **README.md**: Documentación principal
- **PROYECTO_RESUMEN.md**: Resumen ejecutivo en español
- **docs/protocol.md**: Protocolo de comunicación

## Características Implementadas

### Funcionalidades Core
✅ Arrays distribuidos (DArrayInt, DArrayDouble)
✅ Segmentación automática
✅ Procesamiento paralelo con threads
✅ Comunicación por sockets TCP
✅ Tolerancia a fallos básica (heartbeat)
✅ Logging completo

### Ejemplos
✅ Ejemplo 1: Operaciones matemáticas paralelas
✅ Ejemplo 2: Evaluación condicional con resiliencia
⏳ Ejemplo 3: Simulación de fallos (parcial)

### Lenguajes
✅ Java (completo)
✅ Python (completo)  
✅ TypeScript (cliente)

## Cómo Ejecutar

### Opción Rápida
```bash
cd distributed-array-lib/scripts
./demo.sh
```

### Clusters Individuales
```bash
# Java
./start-java-cluster.sh

# Python
./start-python-cluster.sh

# TypeScript Client
./start-typescript-client.sh
```

## Pruebas
```bash
./quick-test.sh
./smoke-test.sh
./run-all-tests.sh
```

## Archivos para Entregar
1. Carpeta completa: `distributed-array-lib/`
2. Presentación: `presentacion_array_distribuido.pdf`
3. Documentación: `PROYECTO_RESUMEN.md`
4. Este archivo: `ENTREGABLES.md`

## Notas
- Sin uso de frameworks externos (solo sockets nativos)
- Compatible con múltiples sistemas operativos
- Probado con arrays de 10,000+ elementos
- Escalable a N nodos trabajadores