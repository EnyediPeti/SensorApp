package com.enyedi.peter.sensorapp.model;

public class SensorData {
    private float[] values;
    private long timestamp;

    public SensorData() {
        values = new float[3];
    }

    public float[] getValues() {
        return values;
    }

    public void setValues(float[] values) {
        this.values = values;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
