package com.pipiqiang.qcamera.app;

public class PhotoItem {
    private String path;
    private long timestamp;
    
    public PhotoItem(String path, long timestamp) {
        this.path = path;
        this.timestamp = timestamp;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}