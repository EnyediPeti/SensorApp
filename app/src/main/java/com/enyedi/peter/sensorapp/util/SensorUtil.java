package com.enyedi.peter.sensorapp.util;

import android.hardware.SensorManager;
import android.location.Location;

import com.enyedi.peter.sensorapp.model.LocationData;
import com.enyedi.peter.sensorapp.model.SensorData;

import java.util.ArrayList;
import java.util.List;

public class SensorUtil {

    public static LocationData createNewLocationData(Location l) {
        LocationData locationData = new LocationData();
        locationData.setAccuracy(l.getAccuracy());
        locationData.setLat(l.getLatitude());
        locationData.setLon(l.getLongitude());
        locationData.setSpeed(l.getSpeed());
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

    public static List<String> removeZeroValues(List<String> list) {
        List<String> newList = new ArrayList<>();
        for (String s : list) {
            if (!s.equals("0")) {
                newList.add(s);
            }
        }
        return newList;
    }
}
