package com.enyedi.peter.sensorapp.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.enyedi.peter.sensorapp.model.LocationData;
import com.enyedi.peter.sensorapp.model.SensorData;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class CsvWriterHelper {

    private static final String TAG = CsvWriterHelper.class.getSimpleName();
    private CSVWriter writer;

    private Context context;

    public CsvWriterHelper(Context context) {
        this.context = context;
    }

    public boolean openCsvWriter() {
        String baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
        String fileName = "MeasurementData_" + DateUtil.getFormattedDate() + ".csv";
        String filePath = baseDir + File.separator + fileName;
        Log.d(TAG, "saveDataToCsv: filepath: " + filePath);
        File f = new File(filePath);

        try {
            // File exist
            if (f.exists() && !f.isDirectory()) {
                FileWriter mFileWriter = new FileWriter(filePath, true);
                writer = new CSVWriter(mFileWriter, ';');
            } else {
                writer = new CSVWriter(new FileWriter(filePath), ';');
            }
            return true;
        } catch (IOException e) {
            Toast.makeText(context, "Error during opening csv writer", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return false;
        }
    }

    public void writeDataInFile(String startDate, List<SensorData> accEventList, List<SensorData> rotEventList, List<SensorData> gyrEventList, List<String> compassList, List<LocationData> locationList) {
        writeTimeAndFrequencyData(startDate, accEventList);

        writeHeader();


        List<SensorData> accData;
        List<SensorData> gyroData;
        List<SensorData> rotData;
        List<String> compData;

        Answers.getInstance().logCustom(new CustomEvent("Original Sensor lists")
                .putCustomAttribute("Acc", accEventList.size())
                .putCustomAttribute("Gyro", gyrEventList.size())
                .putCustomAttribute("Rot", rotEventList.size())
                .putCustomAttribute("Comp", compassList.size())
        );

        Log.d(TAG, "writeDataInFile: acc " + accEventList.size());
        Log.d(TAG, "writeDataInFile: gyro " + gyrEventList.size());
        Log.d(TAG, "writeDataInFile: rot " + rotEventList.size());
        Log.d(TAG, "writeDataInFile: comp " + compassList.size());

        accData = accEventList/*.subList(accEventList.size() - rotEventList.size(), accEventList.size())*/;
        gyroData = accEventList/*.subList(gyrEventList.size() - rotEventList.size(), gyrEventList.size())*/;
        rotData = rotEventList;
        compData = /*SensorUtil.removeZeroValues(*/compassList/*)*/;

        Answers.getInstance().logCustom(new CustomEvent("Modified Sensor lists")
                .putCustomAttribute("Acc", accData.size())
                .putCustomAttribute("Gyro", gyroData.size())
                .putCustomAttribute("Rot", rotData.size())
                .putCustomAttribute("Comp", compData.size())
        );

        Log.d(TAG, "writeDataInFile: accData " + accData.size());
        Log.d(TAG, "writeDataInFile: gyroData " + gyroData.size());
        Log.d(TAG, "writeDataInFile: rotData " + rotData.size());
        Log.d(TAG, "writeDataInFile: compData " + compData.size());

        int listSize = accData.size()/* < compData.size() ? accData.size() : compData.size()*/;

        for (int i = 0; i < listSize; i++) {
            writer.writeNext(
                    new String[]{"",
                            DateUtil.getSecondFromSensorTimestamp(accData.get(0).getTimestamp(), accData.get(i).getTimestamp()),
                            String.valueOf(locationList.get(i).getSpeed()), String.valueOf(locationList.get(i).getLat()), String.valueOf(locationList.get(i).getLon()), String.valueOf(locationList.get(i).getAccuracy()), // GPS speed, lat, lon, acc
                            String.valueOf(accData.get(i).getValues()[0]), String.valueOf(accData.get(i).getValues()[1]), String.valueOf(accData.get(i).getValues()[2]), // accelerometer x, y, z
                            String.valueOf(rotData.get(i).getValues()[0]), String.valueOf(rotData.get(i).getValues()[1]), String.valueOf(rotData.get(i).getValues()[2]), // rotation x, y, z
                            String.valueOf(gyroData.get(i).getValues()[0]), String.valueOf(gyroData.get(i).getValues()[1]), String.valueOf(gyroData.get(i).getValues()[2]), // gyroscope x, y, z
                            compData.get(i)
                    });
        }

        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeTimeAndFrequencyData(String startDate, List<SensorData> accEventList) {
        String[] startTime = {"Start", startDate};
        String[] endTime = {"End", DateUtil.getFormattedDate()};

        Answers.getInstance().logCustom(new CustomEvent("End measurement")
                .putCustomAttribute("Start time", startDate)
                .putCustomAttribute("End time", endTime[1])
        );

        String[] sensorFreq = {"All sensor Fs", String.format(Locale.getDefault(), "%d Hz", SensorUtil.calculateSensorFrequency(accEventList.subList(0, 100)))};

        writer.writeNext(startTime);
        writer.writeNext(endTime);
        writer.writeNext(sensorFreq);
    }

    private void writeHeader() {
        String[] headers = {"parameters:",
                "t[s]",
                "v[m/s]", "lat", "lon", "accuracy",
                "ax", "ay", "az",
                "pitch", "roll", "yaw",
                "gyro_x", "gyro_y", "gyro_z",
                "deg"};

        writer.writeNext(headers);
    }
}
