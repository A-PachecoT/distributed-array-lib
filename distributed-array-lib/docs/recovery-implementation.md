# Implementación de Recuperación ante Fallos

## Estrategia de Replicación

### 1. Distribución con Réplicas
Cuando se distribuye un array, cada segmento se envía a:
- **Worker Principal**: Procesa las operaciones
- **Worker(es) de Respaldo**: Mantienen copias del segmento

```
Segmento 0: Worker-0 (principal), Worker-1 (réplica)
Segmento 1: Worker-1 (principal), Worker-2 (réplica)
Segmento 2: Worker-2 (principal), Worker-0 (réplica)
```

### 2. Detección de Fallos
```
Master detecta timeout → Worker marcado como caído → Activar recuperación
```

### 3. Proceso de Recuperación

#### Paso 1: Identificar segmentos afectados
```java
List<Segment> affectedSegments = findSegmentsByWorker(failedWorkerId);
```

#### Paso 2: Promover réplicas
```java
for (Segment segment : affectedSegments) {
    String backupWorker = segment.replicas.get(0);
    promoteToMainWorker(segment, backupWorker);
}
```

#### Paso 3: Crear nuevas réplicas
```java
for (Segment segment : affectedSegments) {
    String newReplica = selectHealthyWorker();
    replicateSegment(segment, newReplica);
}
```

## Implementación Ejemplo

### Java - Modificación en MasterNode
```java
private Map<String, List<Segment>> workerSegments = new HashMap<>();
private Map<String, List<String>> segmentReplicas = new HashMap<>();

private void handleWorkerFailure(String failedWorkerId) {
    logger.severe("Iniciando recuperación para worker: " + failedWorkerId);
    
    // 1. Obtener segmentos del worker caído
    List<Segment> segments = workerSegments.get(failedWorkerId);
    if (segments == null) return;
    
    // 2. Para cada segmento, activar réplica
    for (Segment segment : segments) {
        List<String> replicas = segmentReplicas.get(segment.id);
        if (replicas != null && !replicas.isEmpty()) {
            String newMainWorker = replicas.get(0);
            
            // Promover réplica a principal
            WorkerInfo worker = workers.get(newMainWorker);
            if (worker != null && worker.alive) {
                // Enviar mensaje de activación
                Message activateMsg = new Message(
                    MessageType.RECOVER_DATA,
                    "master",
                    newMainWorker,
                    Map.of("segmentId", segment.id, "activate", true)
                );
                worker.writer.println(gson.toJson(activateMsg));
                
                // Actualizar mapeos
                segment.workerId = newMainWorker;
                replicas.remove(0);
                
                logger.info("Segmento " + segment.id + 
                           " recuperado en " + newMainWorker);
            }
        }
    }
}
```

### Python - Modificación en master_node.py
```python
def handle_worker_failure(self, worker_id: str):
    self.logger.error(f"Manejando fallo del worker: {worker_id}")
    
    # Buscar arrays afectados
    for array_id, array in self.int_arrays.items():
        for segment in array.segments:
            if segment.worker_id == worker_id and segment.replicas:
                # Activar primera réplica disponible
                for replica_id in segment.replicas:
                    if replica_id in self.workers and self.workers[replica_id].alive:
                        # Promover réplica
                        recover_msg = Message(
                            MessageType.RECOVER_DATA,
                            "master",
                            replica_id,
                            {"arrayId": array_id, "segment": segment.__dict__}
                        )
                        self.workers[replica_id].socket.send(
                            recover_msg.to_json().encode() + b'\n'
                        )
                        
                        # Actualizar asignación
                        segment.worker_id = replica_id
                        segment.replicas.remove(replica_id)
                        
                        self.logger.info(
                            f"Segmento recuperado en {replica_id}"
                        )
                        break
```

## Limitaciones Actuales

1. **Sin replicación activa**: Los datos no se replican automáticamente
2. **Sin sincronización de estado**: Las réplicas no tienen los resultados procesados
3. **Sin rebalanceo**: No se redistribuye la carga después de recuperar

## Mejoras Futuras

1. **Replicación síncrona**: Actualizar réplicas en cada operación
2. **Checkpointing**: Guardar estado intermedio
3. **Consenso**: Usar algoritmo como Raft para coordinación
4. **Rebalanceo dinámico**: Redistribuir segmentos equitativamente