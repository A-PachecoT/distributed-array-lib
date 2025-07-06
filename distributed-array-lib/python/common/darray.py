import numpy as np
from typing import List, Dict, Any, Union
from dataclasses import dataclass

@dataclass
class Segment:
    worker_id: str
    start_index: int
    end_index: int
    replicas: List[str]

class DArrayInt:
    def __init__(self, array_id: str, data: Union[List[int], np.ndarray]):
        self.array_id = array_id
        self.data = np.array(data, dtype=np.int32)
        self.total_size = len(self.data)
        self.segments: List[Segment] = []
    
    def segment_array(self, num_workers: int):
        segment_size = self.total_size // num_workers
        remainder = self.total_size % num_workers
        
        current_index = 0
        for i in range(num_workers):
            size = segment_size + (1 if i < remainder else 0)
            if size > 0:
                self.segments.append(
                    Segment(f"worker-{i}", current_index, current_index + size, [])
                )
                current_index += size
    
    def get_segment_data(self, start_index: int, end_index: int) -> np.ndarray:
        return self.data[start_index:end_index]

class DArrayDouble:
    def __init__(self, array_id: str, data: Union[List[float], np.ndarray]):
        self.array_id = array_id
        self.data = np.array(data, dtype=np.float64)
        self.total_size = len(self.data)
        self.segments: List[Segment] = []
    
    def segment_array(self, num_workers: int):
        segment_size = self.total_size // num_workers
        remainder = self.total_size % num_workers
        
        current_index = 0
        for i in range(num_workers):
            size = segment_size + (1 if i < remainder else 0)
            if size > 0:
                self.segments.append(
                    Segment(f"worker-{i}", current_index, current_index + size, [])
                )
                current_index += size
    
    def get_segment_data(self, start_index: int, end_index: int) -> np.ndarray:
        return self.data[start_index:end_index]