

package com.niklas.activitydoctor;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;

import com.niklas.activitydoctor.util.Logger;
import com.niklas.activitydoctor.util.Util;

import java.text.NumberFormat;
import java.util.Locale;

import com.niklas.activitydoctor.ui.Activity_Main;

/**
 * Service which keeps the accelerometer-sensor alive to always get
 * the users physical activity minutes.
 */
public class SensorListener extends Service implements SensorEventListener {

    private PowerManager.WakeLock mWakeLock;
    private PowerManager mPowerManager;
    Notification notification;

    private final static int NOTIFICATION_ID = 22;

    public final static String ACTION_PAUSE = "pause";


    private float mAccelCurrent;
    private float mAccelLast;
    private int currentCount, todayMinutesModerate, todayMinutesVigorous;
    private int cpmVigorous = 1409;
    private int cpmModerate = 790;
    private float filterValue = 2;
    private long currentTime, lastTime = 0;
    SharedPreferences prefs;


    public final static String ACTION_UPDATE_NOTIFICATION = "updateNotificationState";

    @Override
    public void onAccuracyChanged(final Sensor sensor, int accuracy) {
        // nobody knows what happens here: minute value might magically decrease
        // when this method is called...
        if (BuildConfig.DEBUG) Logger.log(sensor.getName() + " accuracy changed: " + accuracy);
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            currentTime = ((event.timestamp / 1000000L) - lastTime);

            mAccelLast = mAccelCurrent;  //Save last accelerometer value to compare against a future value next time onSensorChanged is triggered.
            mAccelCurrent = (float) Math.sqrt(x * x + y * y + z * z);   //Calculate acceleration.

            if (mAccelCurrent > (9.81 + filterValue)) {     //If acceleration is bigger than gravity + filterValue, a count is added.
                currentCount++;
                //After 60 seconds we check if the count is bigger than cpmThreshold. If it is, a active minute is added.
                if (currentTime > 60000) {
                    lastTime = event.timestamp / 1000000L;          //Save the time this value was taken for use in the next iteration.

                    if (currentCount > cpmVigorous) {
                        todayMinutesVigorous += 1;
                        updateIfNecessary();
                    } else if (currentCount > cpmModerate) {
                        todayMinutesModerate += 1;
                        updateIfNecessary();
                    }
                    currentCount = 0;
                }   //Updates even if no new motion.
            } else if (currentTime > 60000 && (currentCount > cpmVigorous)) {
                lastTime = event.timestamp / 1000000L;
                todayMinutesVigorous += 1;
                updateIfNecessary();
                currentCount = 0;
            } else if (currentTime > 60000 && (currentCount > cpmModerate)) {
                lastTime = event.timestamp / 1000000L;
                todayMinutesModerate += 1;
                updateIfNecessary();
                currentCount = 0;
            }
        }
    }


    private void updateIfNecessary() {
        //Check if we need to save to database. Will be used on a new day.
        checkNewDay();

        //Save todayMinutes in sharedPrefs.
        SharedPreferences prefs = getSharedPreferences("activitydoctor", Context.MODE_PRIVATE);
        prefs.edit().putInt("todayMinutesVigorous", todayMinutesVigorous).commit();
        prefs.edit().putInt("todayMinutesModerate", todayMinutesModerate).commit();

        updateNotificationState();
    }



    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    /**
     * When the user changes something on Fragment_Overview or Fragment_Settings, this method will update the SensorListener class as well.
     */
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (intent != null && ACTION_PAUSE.equals(intent.getStringExtra("action"))) {
            if (BuildConfig.DEBUG)
                Logger.log("onStartCommand action: " + intent.getStringExtra("action"));

            SharedPreferences prefs = getSharedPreferences("activitydoctor", Context.MODE_PRIVATE);
            if (prefs.contains("pauseCount")) { // resume counting

                prefs.edit().remove("pauseCount").commit();
                updateNotificationState();
            } else { // pause counting
                // cancel restart
                ((AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE))
                        .cancel(PendingIntent.getService(getApplicationContext(), 2,
                                new Intent(this, SensorListener.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                prefs.edit().putInt("pauseCount", 1).commit();
                updateNotificationState();
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        if (intent != null && intent.getBooleanExtra(ACTION_UPDATE_NOTIFICATION, false)) {
            updateNotificationState();
        }
        else {
            updateIfNecessary();
        }

        // restart service every hour to restart the sensor, which seems to reduce sensor related bugs.
        ((AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, Math.min(Util.getTomorrow(),
                        System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR), PendingIntent
                        .getService(getApplicationContext(), 2,
                                new Intent(this, SensorListener.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Logger.log("SensorListener onCreate");

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensorWakeLock");

        notification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.notification_title))
                .build();
        startForeground(NOTIFICATION_ID, notification);

        reRegisterSensor();


        prefs = getSharedPreferences("activitydoctor", Context.MODE_PRIVATE);
        if (todayMinutesModerate == 0 || todayMinutesVigorous == 0) {
            todayMinutesModerate = prefs.getInt("todayMinutesModerate", 0);
            todayMinutesVigorous = prefs.getInt("todayMinutesVigorous", 0);
        }
        checkNewDay();

        updateNotificationState();
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        if (BuildConfig.DEBUG) Logger.log("sensor service task removed");
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (BuildConfig.DEBUG) Logger.log("SensorListener onDestroy");
        try {
            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            sm.unregisterListener(this);

        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
            e.printStackTrace();
        }

    }

    private void updateNotificationState() {
        if (BuildConfig.DEBUG) Logger.log("SensorListener updateNotificationState");
        SharedPreferences prefs = getSharedPreferences("activitydoctor", Context.MODE_PRIVATE);
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int goal = prefs.getInt("goal", 60);
        Database db = Database.getInstance(this);
        int todayMinutesTotal = todayMinutesModerate + (todayMinutesVigorous * 2);

        db.close();
        Notification.Builder notificationBuilder = new Notification.Builder(this);

        notificationBuilder.setProgress(goal, todayMinutesTotal, false).setContentText(
                todayMinutesTotal >= goal ? getString(R.string.goal_reached_notification,
                        NumberFormat.getInstance(Locale.getDefault())
                                .format(todayMinutesTotal)) :
                        getString(R.string.notification_text,
                                NumberFormat.getInstance(Locale.getDefault())
                                        .format((goal - todayMinutesTotal))));


        boolean isPaused = prefs.contains("pauseCount");
        notificationBuilder.setPriority(Notification.PRIORITY_MIN).setShowWhen(false)
                .setContentTitle(isPaused ? getString(R.string.ispaused) :
                        getString(R.string.notification_title)).setContentIntent(PendingIntent
                .getActivity(this, 0, new Intent(this, Activity_Main.class),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true);
        nm.notify(NOTIFICATION_ID, notificationBuilder.build());

    }

    private void reRegisterSensor() {
        if (BuildConfig.DEBUG) Logger.log("re-register sensor listener");
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        try {
            sm.unregisterListener(this);
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
            e.printStackTrace();
        }

        if (BuildConfig.DEBUG) {
            Logger.log("minute sensors: " + sm.getSensorList(Sensor.TYPE_ACCELEROMETER).size());
            if (sm.getSensorList(Sensor.TYPE_ACCELEROMETER).size() < 1) return; // emulator
            Logger.log("default: " + sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER).getName());
        }
        mWakeLock.acquire();
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 20000);

    }

    //Check if we need to save to database. Will be used on a new day.
    //New day: add todayMinutes to database for "yesterday" or last active day. Make todayMinutes 0. insertNewDay into database.
    private void checkNewDay() {
        int todayMinutesTotal = todayMinutesModerate + (todayMinutesVigorous * 2);
        Database db = Database.getInstance(this);
        //If no values saved in the database for today, db.getminutes will return Integer.MIN_VALUE. If it does return Int.MIN_VALUE, then it is a new day.
        if (db.getMinutes(Util.getToday()) == Integer.MIN_VALUE) {
            db.addToLastEntry(todayMinutesTotal); //First save today_offset in lastEntry, which is "yesterday".

            //New day, reset todayMinutes.
            todayMinutesModerate = 0;
            todayMinutesVigorous = 0;
            prefs.edit().putInt("todayMinutesModerate", todayMinutesModerate).commit();
            prefs.edit().putInt("todayMinutesVigorous", todayMinutesVigorous).commit(); //Reset todayMinutes in sharedPreferences.
            db.insertNewDay(Util.getToday(), 0);        //Insert new day to database.
        }
        db.close();
    }
}
