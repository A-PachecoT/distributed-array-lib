package common;

import java.io.Serializable;
import java.util.Map;

public class Message implements Serializable {
    private String type;
    private String from;
    private String to;
    private long timestamp;
    private Map<String, Object> data;

    public Message(String type, String from, String to, Map<String, Object> data) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
    }

    public String getType() { return type; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getData() { return data; }

    public void setType(String type) { this.type = type; }
    public void setFrom(String from) { this.from = from; }
    public void setTo(String to) { this.to = to; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setData(Map<String, Object> data) { this.data = data; }
}