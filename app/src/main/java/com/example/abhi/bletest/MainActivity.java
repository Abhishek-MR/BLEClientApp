package com.example.abhi.bletest;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
import static android.bluetooth.BluetoothGattDescriptor.PERMISSION_READ;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    BluetoothLeScanner mLEScanner;

    private static final long SCAN_PERIOD = 10000;

    ListView deviceListView;

    ArrayList<BluetoothDevice> devices;
    ArrayList<String> items;
    ArrayAdapter<String> itemsAdapter;

    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    String uuidAccelService = "795090c7-420d-4048-a24e-18e60180e23c";

    UUID HEART_RATE_SERVICE_UUID = convertFromInteger(0x180D);
    UUID HEART_RATE_MEASUREMENT_CHAR_UUID = convertFromInteger(0x2A37);
    UUID HEART_RATE_CONTROL_POINT_CHAR_UUID = convertFromInteger(0x2A39);
    UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = convertFromInteger(0x2902);

    TextView statusTextView;

    public UUID[] uuids;

    BluetoothGattCharacteristic writeBluetoothGattCharacteristic ;
    BluetoothGattCharacteristic readBluetoothGattCharacteristic ;
    BluetoothGattDescriptor bluetoothGattDescriptor;

    private ScanSettings settings;
    private ArrayList<ScanFilter> filters;

    ProgressDialog progress;

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mBluetoothAdapter.disable();
            if (mBluetoothGatt == null) {
                return;
            }
            mBluetoothGatt.close();
            mBluetoothGatt = null;

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 100);
        }
        else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
            }
            scanLeDevice(true);
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();

        checkforLocation();

        writeBluetoothGattCharacteristic = new BluetoothGattCharacteristic(UUID.fromString("31517c58-66bf-470c-b662-e352a6c80cba"), PROPERTY_READ | PROPERTY_NOTIFY, PERMISSION_READ);
        readBluetoothGattCharacteristic = new BluetoothGattCharacteristic(UUID.fromString("31517c58-66bf-470c-b662-e352a6c80cba"), PROPERTY_READ | PROPERTY_NOTIFY, PERMISSION_READ);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 100);
        }

        progress = new ProgressDialog(this);

        findViewById(R.id.disconnect).setAlpha(0);
        findViewById(R.id.disconnect).setClickable(false);

        findViewById(R.id.scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                progress.setMessage("Scanning...");
                progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progress.setIndeterminate(true);
                progress.show();
                items.clear();
                devices.clear();
                itemsAdapter.notifyDataSetChanged();
                scanLeDevice(true);
            }
        });

        findViewById(R.id.disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                items.clear();
                devices.clear();
                itemsAdapter.notifyDataSetChanged();
                mBluetoothGatt.disconnect();
            }
        });

        findViewById(R.id.writedesind).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte a[] = new byte[2];
                a[1] = 2;
                a[0] = 0;
                WriteDescriptor(ENABLE_INDICATION_VALUE,mBluetoothGatt,bluetoothGattDescriptor);
            }
        });
        findViewById(R.id.writedesnot).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte a[] = new byte[2];
                a[1] = 1;
                a[0] = 0;
                WriteDescriptor(ENABLE_NOTIFICATION_VALUE,mBluetoothGatt,bluetoothGattDescriptor);
            }
        });



        items = new ArrayList<>();
        devices = new ArrayList<>();

        deviceListView = (ListView) findViewById(R.id.list);
        itemsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items);
        deviceListView.setAdapter(itemsAdapter);

        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                progress.setMessage("Connecting...");
                progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progress.setIndeterminate(true);
                progress.show();
                statusTextView.setText("");
                BluetoothDevice bluetoothDevice = devices.get(position);
                //Toast.makeText(getBaseContext(),position+items.get(position)+devices.get(position).getName(),Toast.LENGTH_SHORT).show();
                mBluetoothGatt = bluetoothDevice.connectGatt(getBaseContext(), false, mGattCallback);


            }
        });

        statusTextView = (TextView) findViewById(R.id.statusText);

        final int[] i = {0};

        Button write = (Button) findViewById(R.id.write);
        write.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte some[] = new byte[1];

                some[0] ='0';
                Toast.makeText(getBaseContext(),""+some[0],Toast.LENGTH_SHORT).show();
//                some[0]= (byte) i[0];
                WriteCharacteristic(some,mBluetoothGatt,writeBluetoothGattCharacteristic);
//                i[0]++;
//                for (byte i=0;i<255;i++)
//                {
//                    final byte finalI[] = new byte[1];
//                    finalI[0]= i;
//                    new Handler().postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            //Toast.makeText(getBaseContext(),"Sending "+(finalI+"").getBytes(),Toast.LENGTH_SHORT).show();
//                            WriteCharacteristic(finalI,mBluetoothGatt,writeBluetoothGattCharacteristic);
//                        }
//                    },500);
//
//                }
            }
        });

        Button read = (Button) findViewById(R.id.read);
        read.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReadCharacteristic(writeBluetoothGattCharacteristic,mBluetoothGatt);
            }
        });

    }

    private void scanLeDevice(final boolean enable) {
        devices = new ArrayList<>();
        itemsAdapter.notifyDataSetChanged();

        mLEScanner.startScan(mScanCallback);

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;

                        mLEScanner.stopScan(mScanCallback);

                }
            }, SCAN_PERIOD);

            mScanning = true;

                mLEScanner.startScan(mScanCallback);

        } else {
            mScanning = false;

                mLEScanner.stopScan(mScanCallback);

        }

    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            if(progress.isShowing()) progress.dismiss();

            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            ScanRecord scanRecord= result.getScanRecord();
            String name = null;
            try {
                assert scanRecord != null;
                name = scanRecord.getDeviceName()+"-->"+btDevice.getAddress();
            } catch (Exception e) {
                e.printStackTrace();
            }


            if (btDevice.getName() != null) {
                if (!devices.contains(btDevice)) {

                    items.add(name);
                    devices.add(btDevice);
                }

            }
            itemsAdapter.notifyDataSetChanged();

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };


    private void checkforLocation() {
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        4);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        4);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 4: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 100) {
            statusTextView.append("\n"+resultCode+data);
            //Toast.makeText(getBaseContext(), "" + resultCode + data, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkForBle() {
        PackageManager pm = getBaseContext().getPackageManager();
        final boolean ble = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);

        if (!ble) {
            Log.e("TAG", "Device has no ble");
            Toast.makeText(getApplicationContext(), "Device has no ble", Toast.LENGTH_SHORT).show();
        } else {
            Log.e("TAG", "Device has ble");
            Toast.makeText(getApplicationContext(), "Device has ble", Toast.LENGTH_SHORT).show();
        }
    }


    public UUID convertFromInteger(int i) {
        final long MSB = 0x0000000000001000L;
        final long LSB = 0x800000805f9b34fbL;
        long value = i & 0xFFFFFFFF;
        return new UUID(MSB | (value << 32), LSB);
    }


    public Boolean ReadCharacteristic (BluetoothGattCharacteristic readCharacteristic, BluetoothGatt gatt) {
        return gatt.readCharacteristic(readCharacteristic);
    }

    private void WriteDescriptor(byte[] buffer, BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
        statusTextView.append("Writing to "+descriptor.getUuid().toString()+"---"+buffer+"\n");
        //Set value that will be written

        descriptor.setValue(buffer);
        gatt.writeDescriptor(descriptor);

    }


    private void WriteCharacteristic(byte[] buffer, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        statusTextView.append("Writing to "+characteristic.getUuid().toString()+"---"+buffer+"\n");
        //Set value that will be written
        characteristic.setValue(buffer);
        //Set writing type
        characteristic.setWriteType(WRITE_TYPE_NO_RESPONSE);
        gatt.writeCharacteristic(characteristic);
    }


    public void SubscribeCharacteristic(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        statusTextView.append("Subscribing to "+characteristic.getUuid().toString()+"\n");
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }



    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public synchronized void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            String val="";
            final byte[] dataInput = characteristic.getValue();
            try {
                 val = new String(dataInput,"UTF-8");

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            statusTextView.append("change - " +val+"\n");

        }


        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, final int status,
                                            final int newState) {
            if(progress.isShowing()) progress.dismiss();
            //Toast.makeText(getBaseContext(),""+status+" "+newState,Toast.LENGTH_SHORT).show();
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusTextView.append("" + status + " " + newState + "\n");
                }
            });
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                //broadcastUpdate(intentAction);
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusTextView.append("connected\n");
                        findViewById(R.id.disconnect).setAlpha(1);
                        findViewById(R.id.disconnect).setClickable(true);
                        findViewById(R.id.scan).setAlpha(0);
                        findViewById(R.id.scan).setClickable(false);
                    }
                });
                Log.e("TAG", "Connected to GATT server.");
                Log.e("TAG", "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusTextView.append("disconnected\n");
                        findViewById(R.id.disconnect).setAlpha(0);
                        findViewById(R.id.disconnect).setClickable(false);
                        findViewById(R.id.scan).setAlpha(1);
                        findViewById(R.id.scan).setClickable(true);
                    }
                });
                broadcastUpdate(intentAction);
            }
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                displayGattServices(mBluetoothGatt.getServices());

            } else {
                Log.e("TAG", "onServicesDiscovered received: " + status);
            }

        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //broadcastUpdate(ACTION_DATA_AVAILABLE);

                String val="error read";
                try {
                    val=new String(descriptor.getValue(),"UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                statusTextView.append("Result of a descriptor read operation"+val);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //broadcastUpdate(ACTION_DATA_AVAILABLE);

                String val="error read";
                try {
                    val=new String(descriptor.getValue(),"UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                statusTextView.append("Result of a descriptor write operation"+val);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                //broadcastUpdate(ACTION_DATA_AVAILABLE);

                String val="error read";
                try {
                     val=new String(characteristic.getValue(),"UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                statusTextView.append("Result of a characteristic read operation"+val);
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /*
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.
                    ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the
                // user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };
    */

    public static boolean isCharacteristicWriteable(BluetoothGattCharacteristic pChar) {
        return (pChar.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    public static boolean isCharacteristicIndiacateable(BluetoothGattCharacteristic pChar) {
        return (pChar.getProperties() & (BluetoothGattCharacteristic.PROPERTY_INDICATE )) != 0;
    }


    public static boolean isCharacterisitcReadable(BluetoothGattCharacteristic pChar) {
        return ((pChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
    }


    public boolean isCharacterisiticNotifiable(BluetoothGattCharacteristic pChar) {
        return (pChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
    }


    int i=0,j=0;

    private void displayGattServices(List<BluetoothGattService> gattServices) {

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            String uuid = gattService.getUuid().toString();
            final String finalUuid = uuid;
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusTextView.append("Service discovered" + finalUuid + "\n");
                }
            });

            new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();

            // Loops through available Characteristics.
            for (final BluetoothGattCharacteristic gattCharacteristic :
                    gattCharacteristics) {

                uuid = gattCharacteristic.getUuid().toString();
                final String finalUuid1 = uuid;
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusTextView.append("   charcteristic discovered" + finalUuid1 + "\n");
                        if(isCharacterisitcReadable(gattCharacteristic))
                        {
                            //mBluetoothGatt.readCharacteristic(gattCharacteristic);
                            statusTextView.append("Reading"+ "\n");
                        }
                        if(isCharacterisiticNotifiable(gattCharacteristic))
                        {
                            SubscribeCharacteristic(gattCharacteristic,mBluetoothGatt);
                            statusTextView.append("Notify"+ "\n");

                        }
                        if(isCharacteristicWriteable(gattCharacteristic))
                        {
                            final int finali = i;
                            i++;
                            if(i==2) {
                                //mBluetoothGatt.writeCharacteristic(gattCharacteristic);
                                writeBluetoothGattCharacteristic = gattCharacteristic;
                                statusTextView.append("Writing" + "\n");
                            }

                        }
                        if(isCharacteristicIndiacateable(gattCharacteristic))
                        {
                            SubscribeCharacteristic(gattCharacteristic,mBluetoothGatt);
                            readBluetoothGattCharacteristic = gattCharacteristic;
                            statusTextView.append("Indicatable"+ "\n");

                        }


                    }
                });

                List<BluetoothGattDescriptor> gattDescriptors =
                        gattCharacteristic.getDescriptors();

                for (final BluetoothGattDescriptor gattDescriptor :
                        gattDescriptors)
                {
                    uuid = gattDescriptor.getUuid().toString();
                    final String finalUuid2 = uuid;
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusTextView.append("        descriptor discovered" + finalUuid2 + "\n");
                            gattDescriptor.getPermissions();
                            bluetoothGattDescriptor=gattDescriptor;
                        }

                    });
                }

            }
        }
    }
}
