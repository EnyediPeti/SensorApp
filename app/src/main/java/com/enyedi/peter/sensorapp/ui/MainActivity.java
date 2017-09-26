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
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.enyedi.peter.sensorapp.R;
import com.enyedi.peter.sensorapp.model.LocationData;
import com.enyedi.peter.sensorapp.model.SensorData;
import com.enyedi.peter.sensorapp.util.CsvWriterHelper;
import com.enyedi.peter.sensorapp.util.DateUtil;
import com.enyedi.peter.sensorapp.util.SensorUtil;

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
    SensorEvent lastGyroData;
    SensorEvent lastRotData;
    String lastCompassData;

    SensorManager sensorManager;
    Sensor accelerometer;
    Sensor gyroscope;
    Sensor inklinometer;
    Sensor compass;

    CsvWriterHelper csvWriterHelper;

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

        csvWriterHelper = new CsvWriterHelper(this);
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

            boolean success = csvWriterHelper.openCsvWriter();

            if (!success) {
                onStopClicked();
                Toast.makeText(this, "Something went wrong...\nRelaunch the app!", Toast.LENGTH_LONG).show();
            }

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
    }

    private void startInklinometer() {
        inklinometer = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, inklinometer, SensorManager.SENSOR_DELAY_FASTEST);
        rotEventList = new ArrayList<>();
    }

    private void startGyro() {
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        gyrEventList = new ArrayList<>();
    }

    private void startAccelerometer() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        accEventList = new ArrayList<>();
    }

    private void stopAllSensor() {
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
        locationManager.removeGpsStatusListener(this);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                csvWriterHelper.writeDataInFile(startDate, accEventList, rotEventList, gyrEventList, compassList, locationList);
            }
        });
        t.run();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accEventList.add(SensorUtil.createNewSensorData(event));
                locationList.add(SensorUtil.createNewLocationData(loc));
                gyrEventList.add(SensorUtil.createNewSensorData(lastGyroData));
                rotEventList.add(SensorUtil.createNewSensorData(lastRotData));
                compassList.add(lastCompassData);
                gData = event.values.clone();
                break;
            case Sensor.TYPE_GYROSCOPE:
                lastGyroData = event;
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                lastRotData = event;
                SensorManager.getRotationMatrixFromVector(rMat, event.values);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mData = event.values.clone();
                lastCompassData = SensorUtil.calculateCompassDegree(orientation, rMat, iMat, gData, mData);
                break;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        loc = location;
        //Log.d(TAG, "onLocationChanged() called with: location = [" + location.getAccuracy() + "]");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
        } else if (event == GpsStatus.GPS_EVENT_FIRST_FIX) {
            satellites.setBackgroundColor(getResources().getColor(R.color.green));
        }
    }
}
