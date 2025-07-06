package common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DArrayInt implements Serializable {
    private String arrayId;
    private int[] data;
    private int totalSize;
    private List<Segment> segments;

    public static class Segment implements Serializable {
        public String workerId;
        public int startIndex;
        public int endIndex;
        public List<String> replicas;

        public Segment(String workerId, int startIndex, int endIndex) {
            this.workerId = workerId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.replicas = new ArrayList<>();
        }
    }

    public DArrayInt(String arrayId, int[] data) {
        this.arrayId = arrayId;
        this.data = data;
        this.totalSize = data.length;
        this.segments = new ArrayList<>();
    }

    public void segmentArray(int numWorkers) {
        int segmentSize = totalSize / numWorkers;
        int remainder = totalSize % numWorkers;
        
        int currentIndex = 0;
        for (int i = 0; i < numWorkers; i++) {
            int size = segmentSize + (i < remainder ? 1 : 0);
            if (size > 0) {
                segments.add(new Segment("worker-" + i, currentIndex, currentIndex + size));
                currentIndex += size;
            }
        }
    }

    public int[] getSegmentData(int startIndex, int endIndex) {
        int size = endIndex - startIndex;
        int[] segmentData = new int[size];
        System.arraycopy(data, startIndex, segmentData, 0, size);
        return segmentData;
    }

    public String getArrayId() { return arrayId; }
    public int[] getData() { return data; }
    public int getTotalSize() { return totalSize; }
    public List<Segment> getSegments() { return segments; }
}