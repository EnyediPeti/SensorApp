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
        this.values[0] = values[0];
        this.values[1] = values[1];
        this.values[2] = values[2];
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
