package com.enyedi.peter.sensorapp.ui;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.enyedi.peter.sensorapp.R;
import com.enyedi.peter.sensorapp.util.DateUtil;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    Button startStopButton;
    TextView chronometer;
    boolean isMeasurementStarted = false;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long end = System.currentTimeMillis();
            long delta = end - startTimeMillisec;
            long elapsedSeconds = delta / 1000;
            chronometer.setText(String.format(Locale.getDefault(), "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60));
        }
    };
    Timer timer;
    TimerTask task;

    SensorManager sensorManager;
    Sensor accelerometer;
    Sensor gyroscope;
    Sensor inklinometer;
    Sensor compass;

    CSVWriter writer;
    List<SensorEvent> accEventList;
    List<SensorEvent> gyrEventList;
    List<SensorEvent> rotEventList;

    String startDate;
    long startTimeMillisec;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        startStopButton = (Button) findViewById(R.id.startStopButton);
        chronometer = (TextView) findViewById(R.id.chronometer);
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isMeasurementStarted) {
                    onStartClicked();
                } else {
                    onStopClicked();
                }
            }
        });
    }

    private void onStartClicked() {
        startTimer();
        startDate = DateUtil.getFormattedDate();
        startStopButton.setText(getString(R.string.stop));
        isMeasurementStarted = true;
        startAccelerometer();
        startGyro();
        startInklinometer();
        //startTimer();
        //startGps();
        startCompass();
        //getStartOtherData();

        openCsvWriter();
    }

    private void onStopClicked() {
        timer.cancel();
        startStopButton.setText(getString(R.string.start));
        isMeasurementStarted = false;
        stopAllSensor();
    }

    private void startTimer() {
        startTimeMillisec = System.currentTimeMillis();
        timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                Message msg = new Message();
                handler.sendMessage(msg);
            }
        };
        timer.scheduleAtFixedRate(task, 1000, 1000);
    }

    private void startCompass() {
        compass = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Log.d(TAG, "startCompass: name: [" + compass.getName() +
                "], min delay: [" + compass.getMinDelay() +
                "], max delay: [" + compass.getMaxDelay() +
                "], max range: [" + compass.getMaximumRange() +
                "], resolution: [" + compass.getResolution() +
                "]"
        );
    }

    private void startInklinometer() {
        inklinometer = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, inklinometer, SensorManager.SENSOR_DELAY_FASTEST);
        rotEventList = new ArrayList<>();
        Log.d(TAG, "startInklinometer: name: [" + inklinometer.getName() +
                "], min delay: [" + inklinometer.getMinDelay() +
                "], max delay: [" + inklinometer.getMaxDelay() +
                "], max range: [" + inklinometer.getMaximumRange() +
                "], resolution: [" + inklinometer.getResolution() +
                "]"
        );
    }

    private void startGyro() {
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        gyrEventList = new ArrayList<>();
        Log.d(TAG, "startGyro: name: [" + gyroscope.getName() +
                "], min delay: [" + gyroscope.getMinDelay() +
                "], max delay: [" + gyroscope.getMaxDelay() +
                "], max range: [" + gyroscope.getMaximumRange() +
                "], resolution: [" + gyroscope.getResolution() +
                "]"
        );
    }

    private void startAccelerometer() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        accEventList = new ArrayList<>();
        Log.d(TAG, "startAccelerometer: name: [" + accelerometer.getName() +
                "], min delay: [" + accelerometer.getMinDelay() +
                "], max delay: [" + accelerometer.getMaxDelay() +
                "], max range: [" + accelerometer.getMaximumRange() +
                "], resolution: [" + accelerometer.getResolution() +
                "]"
        );
    }

    private void stopAllSensor() {
        sensorManager.unregisterListener(this);
        writeDataInFile();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                Log.d(TAG, "onSensorChanged: Accelerometer changed " + event.timestamp);
                //sensorData = new String[]{"acc", String.valueOf(System.currentTimeMillis())};
                //writer.writeNext(sensorData);
                accEventList.add(event);
                break;
            case Sensor.TYPE_GYROSCOPE:
                Log.d(TAG, "onSensorChanged: Gyroscope changed " + System.currentTimeMillis());
                //sensorData = new String[]{"gyr", String.valueOf(System.currentTimeMillis())};
                //writer.writeNext(sensorData);
                gyrEventList.add(event);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                Log.d(TAG, "onSensorChanged: Rotation vector changed " + System.currentTimeMillis());
                //sensorData = new String[]{"rot", String.valueOf(System.currentTimeMillis())};
                //writer.writeNext(sensorData);
                rotEventList.add(event);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void openCsvWriter() {
        String baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
        String fileName = "MeasurementData_" + DateUtil.getFormattedDate() + ".csv";
        String filePath = baseDir + File.separator + fileName;
        Log.d(TAG, "saveDataToCsv: filepath: " + filePath);
        File f = new File(filePath);

        try {
            // File exist
            if (f.exists() && !f.isDirectory()) {
                FileWriter mFileWriter = new FileWriter(filePath, true);
                writer = new CSVWriter(mFileWriter);
            } else {
                writer = new CSVWriter(new FileWriter(filePath));
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error during opening csv writer", Toast.LENGTH_LONG).show();
        }
    }

    private void writeDataInFile() {
        String[] startTime = {"Start", startDate};
        String[] endTime = {"End", DateUtil.getFormattedDate()};
        String[] sensorFreq = {"All sensor Fs", "100 Hz"};
        String[] headers = {"parameters:", "t[s]", "v[m/s]", "lat", "lon", "ax", "ay", "az", "pitch", "roll", "yaw", "gyro_y", "gyro_z"};


        writer.writeNext(startTime);
        writer.writeNext(endTime);
        writer.writeNext(sensorFreq);
        writer.writeNext(headers);

        List<SensorEvent> accData;
        List<SensorEvent> gyroData;
        List<SensorEvent> rotData;

        accData = accEventList.subList(accEventList.size() - rotEventList.size(), accEventList.size());
        gyroData = accEventList.subList(gyrEventList.size() - rotEventList.size(), gyrEventList.size());
        rotData = rotEventList;

        Log.d(TAG, "writeDataInFile: acc: " + accEventList.size());
        Log.d(TAG, "writeDataInFile: gyro: " + gyrEventList.size());
        Log.d(TAG, "writeDataInFile: rot: " + rotEventList.size());
        Log.d(TAG, "writeDataInFile: acc: " + accData.size());
        Log.d(TAG, "writeDataInFile: gyro: " + gyroData.size());
        Log.d(TAG, "writeDataInFile: rot: " + rotData.size());

        for (int i = 0; i < accData.size(); i++) {
            writer.writeNext(
                    new String[]{"",
                            DateUtil.getSecondFromSensorTimestamp(accData.get(0).timestamp, accData.get(i).timestamp),
                            "0", "0", "0",
                            String.valueOf(accData.get(i).values[0]), String.valueOf(accData.get(i).values[1]), String.valueOf(accData.get(i).values[2])
                    });
        }

        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
