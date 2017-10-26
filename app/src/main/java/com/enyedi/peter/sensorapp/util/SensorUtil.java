package com.enyedi.peter.sensorapp.util;

import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.Location;

import com.enyedi.peter.sensorapp.model.LocationData;
import com.enyedi.peter.sensorapp.model.SensorData;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class SensorUtil {

    public static SensorData createNewSensorData(SensorEvent event) {
        SensorData data = new SensorData();
        data.setValues(event == null ? new float[3] : event.values);
        data.setTimestamp(event == null ? 0 : event.timestamp);
        return data;
    }

    public static SensorData createNewRotationData(float[] orientation) {
        SensorData data = new SensorData();
        float[] degValues = new float[3];
        degValues[0] = (float) Math.toDegrees(orientation[0]);
        degValues[1] = (float) Math.toDegrees(orientation[1]);
        degValues[2] = (float) Math.toDegrees(orientation[2]);
        data.setValues(degValues);
        data.setTimestamp(0);
        return data;
    }

    public static LocationData createNewLocationData(Location l) {
        LocationData locationData = new LocationData();
        locationData.setAccuracy(l == null ? 0 : l.getAccuracy());
        locationData.setLat(l == null ? 0 : l.getLatitude());
        locationData.setLon(l == null ? 0 : l.getLongitude());
        locationData.setSpeed(l == null ? 0 : l.getSpeed());
        return locationData;
    }

    public static int calculateSensorFrequency(List<SensorData> sensorDataList) {
        float diff = 0;

        for (int i = 1; i < sensorDataList.size(); i++) {
            diff += (sensorDataList.get(i).getTimestamp() - sensorDataList.get(i - 1).getTimestamp());
        }

        return (int) (1 / ((diff / sensorDataList.size()) / 1000000000));
    }

    public static String calculateCompassDegree(float[] orientation, float[] rMat, float[] iMat, float[] gData, float[] mData) {
        return String.valueOf((int) (Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[0]) + 360) % 360);
    }

    public static String getNumberOfSatellites(Iterable iterable) {
        int count = 0;
        Iterator iterator = iterable.iterator();
        while (iterator.hasNext()) {
            if (((GpsSatellite) iterator.next()).usedInFix()) {
                count++;
            }
        }
        return String.format(Locale.getDefault(), "Number of satellites: %d", count);
    }

    public static String getGpsAccuracy(Location location) {
        return String.format(Locale.getDefault(), "GPS accuracy: %d meters", (int) location.getAccuracy());
    }
}
