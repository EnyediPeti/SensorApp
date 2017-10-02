package com.enyedi.peter.sensorapp.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtil {

    public static String getFormattedDate(boolean isForFileName) {
        SimpleDateFormat dateFormat;
        if (isForFileName) {
            dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        } else {
            dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
        }
        return dateFormat.format(new Date());
    }

    public static String getSecondFromSensorTimestamp(final long startTime, final long timestamp) {
        return String.format(Locale.ROOT, "%.3f", ((timestamp - startTime) / 1000000000.0));
    }
}
