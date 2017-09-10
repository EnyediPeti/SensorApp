package com.enyedi.peter.sensorapp.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtil {

    public static String getFormattedDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
        return dateFormat.format(new Date());
    }

    public static String getSecondFromSensorTimestamp(final long startTime, final long timestamp) {
        return String.valueOf((timestamp - startTime) / 1000000000);
    }
}
