package com.clearevo.bluetooth_gnss;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugins.GeneratedPluginRegistrant;

import android.support.v4.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;


import com.clearevo.libecodroidbluetooth.*;
import com.clearevo.libecodroidgnss_parse.*;
import com.clearevo.libbluetooth_gnss_service.*;


public class MainActivity extends FlutterActivity implements gnss_sentence_parser.gnss_parser_callbacks {

    private static final String ENGINE_METHOD_CHANNEL = "com.clearevo.bluetooth_gnss/engine";
    private static final String ENGINE_EVENTS_CHANNEL = "com.clearevo.bluetooth_gnss/engine_events";
    static final String TAG = "btgnss_mainactvty";
    EventChannel.EventSink m_events_sink;
    bluetooth_gnss_service m_service;
    boolean mBound = false;
    Handler m_handler = new Handler();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GeneratedPluginRegistrant.registerWith(this);

        new MethodChannel(getFlutterView(), ENGINE_METHOD_CHANNEL).setMethodCallHandler(
                new MethodCallHandler() {
                    @Override
                    public void onMethodCall(MethodCall call, Result result) {
                        if (call.method.equals("connect")) {
                            String bdaddr = call.argument("bdaddr");
                            boolean secure = call.argument("secure");
                            boolean reconnect = call.argument("reconnect");
                            int ret = connect(bdaddr, secure, reconnect);
                            result.success(ret);
                        } else if (call.method.equals("disconnect")) {
                            try {
                                if (mBound) {
                                    m_service.close();
                                    result.success(true);
                                }
                            } catch (Exception e) {
                                Log.d(TAG, "disconnect exception: "+Log.getStackTraceString(e));
                            }
                            result.success(false);
                        } else if (call.method.equals("get_bd_map")) {
                            result.success(rfcomm_conn_mgr.get_bd_map());
                        } else if (call.method.equals("is_bluetooth_on")) {
                            result.success(rfcomm_conn_mgr.is_bluetooth_on());
                        } else if (call.method.equals("is_bt_connected")) {
                            if (mBound && m_service != null && m_service.is_bt_connected()) {
                                result.success(true);
                            } else {
                                result.success(false);
                            }
                        } else if (call.method.equals("is_conn_thread_alive")) {
                            if (mBound && m_service != null && m_service.is_conn_thread_alive()) {
                                result.success(true);
                            } else {
                                result.success(false);
                            }

                        } else if (call.method.equals("open_phone_settings")) {
                            result.success(open_phone_settings());
                        } else if (call.method.equals("open_phone_developer_settings")) {
                            result.success(open_phone_developer_settings());
                        } else if (call.method.equals("open_phone_blueooth_settings")) {
                            result.success(open_phone_bluetooth_settings());
                        } else if (call.method.equals("open_phone_location_settings")) {
                            result.success(open_phone_location_settings());
                        } else if (call.method.equals("is_location_enabled") || call.method.equals("is_mock_location_enabled")) {

                            Log.d(TAG, "is_location_enabled 0");
                            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                    ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    ) {

                                Log.d(TAG, "is_location_enabled check locaiton permission already granted");
                                if (call.method.equals("is_location_enabled")) {
                                    result.success(bluetooth_gnss_service.is_location_enabled(getApplicationContext()));
                                } else if (call.method.equals("is_mock_location_enabled")) {
                                    result.success(bluetooth_gnss_service.is_mock_location_enabled(getApplicationContext(), android.os.Process.myUid(), BuildConfig.APPLICATION_ID));
                                }
                            } else {
                                Log.d(TAG, "is_location_enabled check locaiton permission not granted yet so requesting permission now");
                                Toast.makeText(getApplicationContext(), "BluetoothGNSS needs to check location settings - please allow...", Toast.LENGTH_LONG).show();

                                new Thread() {
                                    public void run() {
                                        try {
                                            Thread.sleep(2000);
                                        } catch (Exception e) {}
                                        m_handler.post(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                                        }, 1);
                                                    }
                                                }
                                        );
                                    }
                                }.start();
                                result.success(false);
                            }

                        } else {
                            result.notImplemented();
                        }
                    }
                }
        );

        new EventChannel(getFlutterView(), ENGINE_EVENTS_CHANNEL).setStreamHandler(
                new EventChannel.StreamHandler() {
                    @Override
                    public void onListen(Object args, final EventChannel.EventSink events) {
                        Log.w(TAG, "adding listener");
                        m_events_sink = events;
                    }

                    @Override
                    public void onCancel(Object args) {
                        Log.w(TAG, "cancelling listener");
                        m_events_sink = null;
                    }
                }
        );
    }

    public boolean open_phone_settings()
    {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            return true;
        } catch (Exception e ) {
            Log.d(TAG, "launch phone settings activity exception: "+Log.getStackTraceString(e));
        }
        return false;
    }

    public boolean open_phone_bluetooth_settings()
    {
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            return true;
        } catch (Exception e ) {
            Log.d(TAG, "launch phone settings activity exception: "+Log.getStackTraceString(e));
        }
        return false;
    }

    public boolean open_phone_location_settings()
    {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return true;
        } catch (Exception e ) {
            Log.d(TAG, "launch phone settings activity exception: "+Log.getStackTraceString(e));
        }
        return false;
    }

    public boolean open_phone_developer_settings()
    {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            return true;
        } catch (Exception e ) {
            Log.d(TAG, "launch phone settings activity exception: "+Log.getStackTraceString(e));
        }
        return false;
    }

    public void on_updated_nmea_params(HashMap<String, Object> params_map)
    {
        try {
            m_events_sink.success(params_map);
        } catch (Exception e) {
            Log.d(TAG, "on_updated_nmea_params sink update exception: "+Log.getStackTraceString(e));
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(getApplicationContext(), bluetooth_gnss_service.class);
        stopService(intent);
        super.onBackPressed();
    }

    int connect(String bdaddr, boolean secure, boolean reconnect)
    {
        Log.d(TAG, "MainActivity connect(): "+bdaddr);
        int ret = -1;

        Intent intent = new Intent(getApplicationContext(), bluetooth_gnss_service.class);
        intent.putExtra("bdaddr", bdaddr);
        intent.putExtra("secure", secure);
        intent.putExtra("reconnect", reconnect);
        intent.putExtra("activity_class_name", this.getClass().getName());
        intent.putExtra("activity_icon_id", R.mipmap.ic_launcher);
        ComponentName ssret = startService(intent);
        Log.d(TAG, "MainActivity connect(): startservice ssret: "+ssret.flattenToString());
        return 0;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Intent intent = new Intent(this, bluetooth_gnss_service.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

    }


    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
        mBound = false;

    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {

            Log.d(TAG, "onServiceConnected()");

            // We've bound to LocalService, cast the IBinder and get LocalService instance
            bluetooth_gnss_service.LocalBinder binder = (bluetooth_gnss_service.LocalBinder) service;
            m_service = binder.getService();
            mBound = true;
            m_service.set_callback((gnss_sentence_parser.gnss_parser_callbacks) MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

}