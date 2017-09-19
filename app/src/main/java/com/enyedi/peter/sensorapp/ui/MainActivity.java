package com.enyedi.peter.sensorapp.ui;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.enyedi.peter.sensorapp.R;
import com.enyedi.peter.sensorapp.model.LocationData;
import com.enyedi.peter.sensorapp.model.SensorData;
import com.enyedi.peter.sensorapp.util.DateUtil;
import com.enyedi.peter.sensorapp.util.SensorUtil;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import io.fabric.sdk.android.Fabric;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.PermissionUtils;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener, GpsStatus.Listener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final long MIN_TIME_BW_UPDATES = 0;
    private static final float MIN_DISTANCE_CHANGE_FOR_UPDATES = 0;
    Button startStopButton;
    TextView chronometer;
    TextView satellites;

    boolean isMeasurementStarted = false;

    Timer timer;
    TimerTask task;
    LocationManager locationManager;
    Location loc;

    SensorManager sensorManager;
    Sensor accelerometer;
    Sensor gyroscope;
    Sensor inklinometer;
    Sensor compass;

    CSVWriter writer;

    List<SensorData> accEventList = new ArrayList<>();
    List<SensorData> gyrEventList = new ArrayList<>();
    List<SensorData> rotEventList = new ArrayList<>();
    List<LocationData> locationList = new ArrayList<>();
    List<String> compassList = new ArrayList<>();

    // For compass
    float[] gData = new float[3]; // gravity or accelerometer
    float[] mData = new float[3]; // magnetometer
    float[] rMat = new float[9];
    float[] iMat = new float[9];
    float[] orientation = new float[3];

    String startDate;
    long startTimeMillisec;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long end = System.currentTimeMillis();
            long delta = end - startTimeMillisec;
            long elapsedSeconds = delta / 1000;
            chronometer.setText(String.format(Locale.getDefault(), "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60));
        }
    };
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
        checkPermissionForGpsStatus();

        startStopButton = (Button) findViewById(R.id.startStopButton);
        chronometer = (TextView) findViewById(R.id.chronometer);
        satellites = (TextView) findViewById(R.id.satellites);
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

    @Override
    protected void onPause() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            locationManager.removeGpsStatusListener(this);
        }
        super.onPause();
    }

    private void checkPermissionForGpsStatus() {
        if (PermissionUtils.hasSelfPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            addGpsStatusListener();
        } else {
            if (PermissionUtils.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                MainActivityPermissionsDispatcher.addGpsStatusListenerWithCheck(this);
            } else {
                showPermissionDialog(getString(R.string.permission_show_rationale), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivityPermissionsDispatcher.addGpsStatusListenerWithCheck(MainActivity.this);
                    }
                });
            }
        }
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void addGpsStatusListener() {
        try {
            locationManager.addGpsStatusListener(this);
            startGps();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void onStartClicked() {
        if (PermissionUtils.hasSelfPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            startAllSensor();
        } else {
            if (PermissionUtils.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                MainActivityPermissionsDispatcher.startAllSensorWithCheck(this);
            } else {
                showPermissionDialog(getString(R.string.permission_show_rationale), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivityPermissionsDispatcher.startAllSensorWithCheck(MainActivity.this);
                    }
                });
            }

        }
    }

    private void onStopClicked() {
        timer.cancel();
        startStopButton.setText(getString(R.string.start));
        isMeasurementStarted = false;
        stopAllSensor();
    }

    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void startAllSensor() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            startTimer();
            startDate = DateUtil.getFormattedDate();
            startStopButton.setText(getString(R.string.stop));
            isMeasurementStarted = true;
            startAccelerometer();
            startGyro();
            startInklinometer();
            //startGps();
            startCompass();

            openCsvWriter();

            Answers.getInstance().logCustom(new CustomEvent("Start measurement"));
        } else {
            Toast.makeText(this, "Enable GPS to use this app", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @OnShowRationale({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void showRationale(final PermissionRequest request) {
        showPermissionDialog(getString(R.string.permission_show_rationale), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        request.proceed();
                        break;
                }
            }
        });
    }

    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    public void onAccessFineLocationPermissionDenied() {
        showNeedPermissionDialogWhenDenied();
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void onWriteExternalStoragePermissionDenied() {
        showNeedPermissionDialogWhenDenied();
    }

    private void showNeedPermissionDialogWhenDenied() {
        showPermissionDialog(getString(R.string.permission_show_rationale), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        startInstalledAppDetailsActivity();
                        break;
                }
            }
        });
    }

    protected void showPermissionDialog(String message, DialogInterface.OnClickListener onPosClickListener) {
        if (dialog == null || !dialog.isShowing()) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message);
            builder.setPositiveButton("Ok", onPosClickListener);
            builder.setNegativeButton("Cancel", null);
            builder.setCancelable(false);

            dialog = builder.create();
            dialog.show();
        }
    }

    private void startInstalledAppDetailsActivity() {
        final Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        startActivity(intent);
    }

    private void startGps() {
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BW_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

            if (locationManager != null) {
                loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
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
        sensorManager.registerListener(this, compass, SensorManager.SENSOR_DELAY_FASTEST);
        /*Log.d(TAG, "startCompass: name: [" + compass.getName() +
                "], min delay: [" + compass.getMinDelay() +
                "], max delay: [" + compass.getMaxDelay() +
                "], max range: [" + compass.getMaximumRange() +
                "], resolution: [" + compass.getResolution() +
                "]"
        );*/
    }

    private void startInklinometer() {
        inklinometer = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, inklinometer, SensorManager.SENSOR_DELAY_FASTEST);
        rotEventList = new ArrayList<>();
        /*Log.d(TAG, "startInklinometer: name: [" + inklinometer.getName() +
                "], min delay: [" + inklinometer.getMinDelay() +
                "], max delay: [" + inklinometer.getMaxDelay() +
                "], max range: [" + inklinometer.getMaximumRange() +
                "], resolution: [" + inklinometer.getResolution() +
                "]"
        );*/
    }

    private void startGyro() {
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        gyrEventList = new ArrayList<>();
        /*Log.d(TAG, "startGyro: name: [" + gyroscope.getName() +
                "], min delay: [" + gyroscope.getMinDelay() +
                "], max delay: [" + gyroscope.getMaxDelay() +
                "], max range: [" + gyroscope.getMaximumRange() +
                "], resolution: [" + gyroscope.getResolution() +
                "]"
        );*/
    }

    private void startAccelerometer() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        accEventList = new ArrayList<>();
        /*Log.d(TAG, "startAccelerometer: name: [" + accelerometer.getName() +
                "], min delay: [" + accelerometer.getMinDelay() +
                "], max delay: [" + accelerometer.getMaxDelay() +
                "], max range: [" + accelerometer.getMaximumRange() +
                "], resolution: [" + accelerometer.getResolution() +
                "]"
        );*/
    }

    private void stopAllSensor() {
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
        locationManager.removeGpsStatusListener(this);
        writeDataInFile();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accEventList.add(SensorUtil.createNewSensorData(event));
                locationList.add(SensorUtil.createNewLocationData(loc));
                gData = event.values.clone();
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyrEventList.add(SensorUtil.createNewSensorData(event));
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                rotEventList.add(SensorUtil.createNewSensorData(event));
                SensorManager.getRotationMatrixFromVector(rMat, event.values);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mData = event.values.clone();
                compassList.add(SensorUtil.calculateCompassDegree(orientation, rMat, iMat, gData, mData));
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
                writer = new CSVWriter(mFileWriter, ';');
            } else {
                writer = new CSVWriter(new FileWriter(filePath), ';');
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error during opening csv writer", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void writeDataInFile() {
        String[] startTime = {"Start", startDate};
        String[] endTime = {"End", DateUtil.getFormattedDate()};

        Answers.getInstance().logCustom(new CustomEvent("End measurement")
                .putCustomAttribute("Start time", startDate)
                .putCustomAttribute("End time", endTime[1])
        );

        String[] sensorFreq = {"All sensor Fs", String.format(Locale.getDefault(), "%d Hz", SensorUtil.calculateSensorFrequency(accEventList.subList(0, 100)))};
        String[] headers = {"parameters:", "t[s]", "v[m/s]", "lat", "lon", "accuracy", "ax", "ay", "az", "pitch", "roll", "yaw", "gyro_x", "gyro_y", "gyro_z", "deg"};


        writer.writeNext(startTime);
        writer.writeNext(endTime);
        writer.writeNext(sensorFreq);
        writer.writeNext(headers);

        List<SensorData> accData;
        List<SensorData> gyroData;
        List<SensorData> rotData;
        List<String> compData;

        accData = accEventList.subList(accEventList.size() - rotEventList.size(), accEventList.size());
        gyroData = accEventList.subList(gyrEventList.size() - rotEventList.size(), gyrEventList.size());
        rotData = rotEventList;
        compData = SensorUtil.removeZeroValues(compassList);

        Log.d(TAG, "writeDataInFile: accData " + accData.size());
        Log.d(TAG, "writeDataInFile: gyroData " + gyroData.size());
        Log.d(TAG, "writeDataInFile: rotData " + rotData.size());
        Log.d(TAG, "writeDataInFile: compData " + compData.size());

        int listSize = accData.size() < compData.size() ? accData.size() : compData.size();

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

    @Override
    public void onLocationChanged(Location location) {
        loc = location;
        Log.d(TAG, "onLocationChanged() called with: location = [" + location.getAccuracy() + "]");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    @Override
    public void onGpsStatusChanged(int event) {
        if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            try {
                satellites.setText(SensorUtil.getNumberOfSatellites(locationManager.getGpsStatus(null).getSatellites()));
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }
}
