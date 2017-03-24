package com.example.jimtseng.estimotedatacollector;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.lang.StringBuilder;
import java.lang.String;

public class MyIbeaconCollector extends AppCompatActivity {

    private static final int REQUEST_CODE_COARSE = 2528;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private ScanFilter beacon_filter;
    private List<ScanFilter> filters = new ArrayList<>();
    private BluetoothGatt mGatt;
    private Handler mHandler;
    private ScanResult SharedScanResult;
    private Button button_start, button_stop;
    private TextView mytext,mystatustext;
    private ProgressBar mybar;
    private FileOutputStream myfile;
    private boolean FileInOpen = false;
    private int myposition;
    private int progress;
    final String TAG = "EstimoteCollector";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_ibeacon_collector);
        final Spinner myspinner = (Spinner) findViewById(R.id.spinner_cell);
        button_start = (Button) findViewById(R.id.button_start);
        button_stop = (Button) findViewById(R.id.button_stop);
        mybar = (ProgressBar) findViewById(R.id.progressBar);
        mybar.setProgress(0);
        mytext = (TextView) findViewById(R.id.textView);
        mystatustext = (TextView) findViewById(R.id.statustext);
        mytext.setText("Press Start button to record");
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.cell,
                android.R.layout.simple_spinner_dropdown_item);
        myspinner.setAdapter(adapter);
        myspinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "position:" + position + " is selected");
                myposition = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        button_start.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.d(TAG,"Start recording");
                mystatustext.setText("Start recording");
                OpenRecordFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/my_beacon_log.csv");
                progress = 0;
            }
        });
        button_stop.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                CloseRecordFile();
                mystatustext.setText("Close recorded file");
                Log.d(TAG,"Stop recording");
            }
        });
        mHandler = new Handler();
        ActivityCompat.requestPermissions(MyIbeaconCollector.this,new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_CODE_COARSE);
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        Log.d(TAG, "getting BT adapter....");
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        Resources res = getResources();
        String beacons_array[] = res.getStringArray(R.array.beacons);
        for (String beacon : beacons_array) {
            beacon_filter = new ScanFilter.Builder().setDeviceAddress(beacon).build();
            if (mBluetoothAdapter.checkBluetoothAddress(beacon)) {
                Log.d(TAG, "valid beacon addr");
            } else {
                Log.d(TAG, "invalid beacon addr");
            }
            Log.d(TAG, "Adding filter<" + beacon + ">");
            mystatustext.setText("Adding filter<" + beacon + ">");
            filters.add(beacon_filter);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart...");
        //  scanLeDevice(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume...");
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "BT is not enabled, requesting to enable");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
            finish();
        } else {
            if(Build.VERSION.SDK_INT >= 23) {
                mystatustext.setText("SDK>=23, checking location permission");
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mystatustext.setText("SDK>=23 and location permission granted");
                    scanLeDevice(true);
                }
            } else {
                mystatustext.setText("SDK<23,no location permission required");
                scanLeDevice(true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onDestroy() {
        if(FileInOpen) {
            try {
                Log.d(TAG, "Closing file before destroying activity");
                myfile.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == Activity.RESULT_CANCELED) {
            Log.d(TAG, "BT cannot be enabled");
            finish();
            return;
        }
    }

    private void scanLeDevice(boolean enable) {
        Log.d(TAG, "scanLeDevice enable=" + enable);
        if (enable) {
            Log.d(TAG, "Starting scan");
            mystatustext.setText("Starting scan");
            mLEScanner.startScan(filters, settings, mLEScanCallback_new);
            // mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            Log.d(TAG, "Stopping scan");
            mystatustext.setText("Scan stopped");
            mLEScanner.stopScan(mLEScanCallback_new);
            // mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private ScanCallback mLEScanCallback_new = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            SharedScanResult = result;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    String payload_for_log = String.format(
                            "[TimeTAG]" + SharedScanResult.getTimestampNanos()
                                    + " BLEDevice:" + SharedScanResult.getDevice().toString()
                                    + " Record:" + bytesToHex(SharedScanResult.getScanRecord().getBytes())
                                    + " rssi:" + SharedScanResult.getRssi()
                                    + "\n");
                    String payload_for_mqtt = String.format(
                            SharedScanResult.getTimestampNanos()
                                    + "," + SharedScanResult.getDevice().toString()
                                    + "," + bytesToHex(SharedScanResult.getScanRecord().getBytes())
                                    + "," + SharedScanResult.getRssi());
                }
            });
            Log.d(TAG, "BLEDevice:" + result.getDevice().toString() + " Record:" + bytesToHex(result.getScanRecord().getBytes()) + " rssi:" + result.getRssi());
            mytext.setText("Found beacon:"+SharedScanResult.getDevice().toString());
            Calendar c = Calendar.getInstance();
            WriteBeaconData(c.getTime().toString(),
                    String.format("%d",myposition),
                    SharedScanResult.getDevice().toString(),
                    String.format("%d",SharedScanResult.getRssi()));
            if(FileInOpen && progress < 100) {
                mybar.setProgress(progress++);
                mystatustext.setText("Progress:%" + String.format("%d", progress));
            } else if(!FileInOpen) {
                mystatustext.setText("Stopped. File in path "+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
            } else {
                mystatustext.setText("Progress > 100%, you may stop anytime");
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.d(TAG, "[Batch]BLEDevice:" + sr.getDevice().toString() + " Record:" + bytesToHex(sr.getScanRecord().getBytes()) + " rssi:" + sr.getRssi());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan error code:" + errorCode);
        }
    };
    private static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("0x%02x-", b));
        }
        return builder.toString();
    }

    private void OpenRecordFile(String filename) {
        if(!FileInOpen) {
            try {
                isStoragePermissionGranted();
                Log.d(TAG, "Opening file..");
                myfile = new FileOutputStream(filename, true);
                FileInOpen = true;
            } catch (Exception e) {
                FileInOpen = false;
                e.printStackTrace();
            }
        } else {
            Log.d(TAG,"File already in open!");
        }
    }
    private void CloseRecordFile() {
        if(FileInOpen) {
            try {
                Log.d(TAG, "Closing file..");
                myfile.close();
                FileInOpen = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void WriteBeaconData(String SystemTime,String MyLocation,String BeaconAddr,String Rssi) {
        if(FileInOpen) {
            try {
                String payload = SystemTime + "," + MyLocation + "," + BeaconAddr + "," + Rssi + "\n";
                Log.d(TAG, "Writing payload to file: " + payload);
                myfile.write(payload.getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                mystatustext.setText("Storage write permission granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                mystatustext.setText("Requesting storage write permission");
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
            Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
            //resume tasks needing this permission
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }
}
