package com.example.ble_app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private TextView mTextMessage;
    private ListView listDevice;
    private TextView dataView;
    private TextView lastView;
    private Button scan;
    private Button getData;
    private TextView getLast;
    private Context context;
    BluetoothManager bluetoothManager;
    private boolean wrote=true;
    private boolean controlled=true;
    private int nWrote=0;
    private BluetoothAdapter bluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_LOCATION=2;
    private boolean mScanning;
    private Handler handler;
    private boolean enableScan;
    private boolean accettato=true;
    private boolean startReceive;
    private List<String>temps;
    int nTime=0;
    private View viewDevice=null;
    private String noData="No Data Available Now!";
    //private LeDeviceListAdapter leDeviceListAdapter;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID TEMPERATURE_SERVICE_UUID=UUID.fromString("00001809-0000-1000-8000-00805F9B34FB");
    public static final UUID CHAR_TEMPERATURE_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805F9B34FB");
    public static final UUID CHAR_TEMPERATURE_UUID_DATA= UUID.fromString("f4eaba63-3a74-4a99-b932-2a4ea823bbe4");
    // Use this check to determine whether BLE is supported on the device. Then
// you can selectively disable BLE-related features.
    private ArrayList<String>               bleDeviceArrayString = null;
    private ArrayList<BluetoothDevice>      bleDeviceArrayList   = null;
    private ArrayAdapter<String> bleDeviceAdapterString;
    private ArrayAdapter<String> dataArrayAdapter;
    private ArrayList<Float> valueList;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic temperatureCharData;
    private BluetoothGattCharacteristic temperatureChar;
    private LocationManager locationManager;
    boolean gps_enabled;
    private Button.OnClickListener scanListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // Ensures Bluetooth is available on the device and it is enabled. If not,
            // displays a dialog requesting user permission to enable Bluetooth.

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled() || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
                        builder.setTitle("Location Permission");
                        builder.setMessage("The app needs location permissions. Please grant this permission to continue using the features of the app.");
                        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_LOCATION);
                                }
                            }
                        });
                    }
                        Intent enableLocationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult(enableLocationIntent, REQUEST_ENABLE_LOCATION);
                }
            }
            else {
                if (mScanning)
                    scanLeDevice(false);
                else
                    scanLeDevice(true);
            }

        }
    };
    private final BluetoothGattCallback mGattCallback= new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState== BluetoothProfile.STATE_CONNECTED) {

                if (mBluetoothGatt == null) {
                    mBluetoothGatt = gatt;
                }
                if (viewDevice!=null){
                    System.out.println("CONNECTED...");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            viewDevice.setBackgroundColor(Color.GREEN);
                        }
                    });
                }
                else{
                    System.out.println("VIEW DEVICE NULL 0...");
                }
                System.out.println("bool "+mBluetoothGatt.discoverServices());

            }
            else if (newState==BluetoothProfile.STATE_CONNECTING){
                System.out.println("CING...");
                if (viewDevice!=null){
                    System.out.println("CONNECTING...");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            viewDevice.setBackgroundColor(Color.YELLOW);
                        }
                    });
                }
                else{
                    System.out.println("DEVICE NULL");
                }
            }
            else if (newState==BluetoothProfile.STATE_DISCONNECTED){
                System.out.println("ROSSO");
                if (viewDevice!=null){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            viewDevice.setBackgroundColor(Color.RED);
                        }
                    });
                }
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            System.out.println("SERVICE DISCOVERED");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService temperatureService=mBluetoothGatt.getService(TEMPERATURE_SERVICE_UUID);
                if (temperatureService!=null){
                    System.out.println("NO NULL 1");
                    temperatureChar=temperatureService.getCharacteristic(CHAR_TEMPERATURE_UUID);
                    temperatureCharData=temperatureService.getCharacteristic(CHAR_TEMPERATURE_UUID_DATA);
                    //BluetoothGattDescriptor descritpor=temperatureCharData.getDescriptor(CCCD);
                    //descritpor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    //mBluetoothGatt.writeDescriptor(descritpor);
                    if (temperatureChar!=null){
                        System.out.println("NO NULL 2");
                        //System.out.println("Valore: "+temperatureChar.getValue().toString());
                        //System.out.println("Valore: "+temperatureChar.getStringValue(1));
                        mBluetoothGatt.readCharacteristic(temperatureChar);
                        mBluetoothGatt.setCharacteristicNotification(temperatureChar,true);
                        //final BluetoothGattDescriptor descriptor = temperatureChar.getDescriptor(CCCD);
                        //descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        //mBluetoothGatt.writeDescriptor(descriptor);
                        //System.out.println("Valore: "+temperatureChar.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT,1));
                    }
                    if (temperatureCharData!=null){
                        System.out.println("NO NULL 3");
                        //System.out.println("Valore: "+temperatureChar.getValue().toString());
                        //System.out.println("Valore: "+temperatureChar.getStringValue(1));
                        mBluetoothGatt.setCharacteristicNotification(temperatureCharData,true);
                        //final BluetoothGattDescriptor descriptor = temperatureChar.getDescriptor(CCCD);
                        //descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        //mBluetoothGatt.writeDescriptor(descriptor);
                        //System.out.println("Valore: "+temperatureChar.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT,1));
                    }
                }
            }
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            final UUID uuid=characteristic.getUuid();
            if (uuid.equals(CHAR_TEMPERATURE_UUID)) {
                mBluetoothGatt.readCharacteristic(temperatureCharData);
                updateData(characteristic);
            }
            if (uuid.equals(CHAR_TEMPERATURE_UUID_DATA)){
                //System.out.println("CHR");
                if (!startReceive) {
                    updateAvailability(characteristic);
                }
                else {
                    getDataSD(characteristic,gatt);
                }
            }


        }
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            //mBluetoothGatt.close();

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            final UUID uuid=characteristic.getUuid();
            if (uuid.equals(CHAR_TEMPERATURE_UUID))
                updateData(characteristic);
            else if (uuid.equals(CHAR_TEMPERATURE_UUID_DATA)) {

                    if (!startReceive && accettato) {
                        //System.out.println("CHCH START FALSE");
                        updateAvailability(characteristic);

                    } else {
                        //System.out.println("CHCH START TRUE");
                        getDataSD(characteristic,gatt);
                    }
            }

        }
    };


    public void updateAvailability(BluetoothGattCharacteristic characteristic){
        float value = ByteBuffer.wrap(characteristic.getValue()).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            /*if (startReceive){
                if (value==0.01){
                    startReceive=false;
                }
                else {
                    //temps.add(String.valueOf(value));
                    //dataArrayAdapter.notifyDataSetChanged();
                }

            }
            else if (value==0.01){
                startReceive=true;
            }
            else{
                //temps.add(noData);
                //dataArrayAdapter.notifyDataSetChanged();
                System.out.println(noData);
            }
            characteristic.setValue("acc");
            System.out.println("CIAOOOO");
            mBluetoothGatt.writeCharacteristic(characteristic);*/
        if (Float.compare(Float.valueOf("0.01"),value)==0){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dataView.setText("Data Available");
                    getData.setEnabled(true);
                    getData.setClickable(true);
                    //getData.performClick();
                }
            });

        }
        else if (Float.compare(Float.valueOf("0.02"),value)==0){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dataView.setText(noData);
                    getData.setEnabled(false);
                    getData.setClickable(false);
                }
            });

        }
        else{
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dataView.setText("Uploading Data");
                    getData.setEnabled(false);
                    getData.setClickable(false);
                }
            });
        }
    }
    public void updateData(BluetoothGattCharacteristic characteristic){
        final float value = ByteBuffer.wrap(characteristic.getValue()).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lastView.setText(String.valueOf(value)+" C°");
            }
        });


    }
    private ListView.OnItemClickListener listDeviceListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
            BluetoothDevice devicePos = (BluetoothDevice) bleDeviceArrayList.get(position);
            BluetoothDevice deviceAdd = bluetoothAdapter.getRemoteDevice(devicePos.getAddress());
            view.setBackgroundColor(Color.YELLOW);
            viewDevice=view;
            mBluetoothGatt=deviceAdd.connectGatt(view.getContext(),false,mGattCallback);
        }
    };

    private Button.OnClickListener getDataListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            accettato=false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dataView.setText("Uploading Data");
                    getData.setEnabled(false);
                    getData.setClickable(false);
                }
            });
            System.out.println("CLICK");
            valueList.clear();
            temperatureCharData.setValue("req");
            mBluetoothGatt.writeCharacteristic(temperatureCharData);
            startReceive=true;
        }
    };

    public void postAllData(){
        //System.out.println(valueList.size()+" - "+valueList.toString());
        nWrote=0;
        final String clientId="esp32Mattia";
        final String topic="esp32Mattia/Temperature";
        final MqttAndroidClient client =
                new MqttAndroidClient(getApplicationContext(), "tcp://test.mosquitto.org",
                        clientId);
        try{
            final IMqttToken token = client.connect();

            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    temperatureCharData.setValue("acc");
                    mBluetoothGatt.writeCharacteristic(temperatureCharData);

                    for (int i=0;i<valueList.size();i++){
                        publish(client,topic,valueList.get(i));
                        if (i>10) {
                            try {
                                Thread.sleep(15);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    //System.out.println("SIZE: "+valueList.size());


                    try {
                        IMqttToken token2 = client.disconnect();
                        token2.setActionCallback(new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                                //System.out.println("ACCETTATO");
                                accettato=true;

                                //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                            }

                            @Override
                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                System.out.println("FAILURE2 :"+exception);
                                accettato=true;
                                updateAvailability(temperatureCharData);

                            }
                        });
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }


                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("FAILURE token connect:"+exception);

                }
            });



        } catch (MqttException e) {
            e.printStackTrace();
            System.out.println("EXCEPTION");
        }
    }
    public void publish(MqttAndroidClient client, String topic, float value){
        MqttMessage message=new MqttMessage(String.valueOf(value).getBytes());
        try {
            client.publish(topic, message);
        } catch (MqttPersistenceException e) {
            e.printStackTrace();
            System.out.println("E1");
        } catch (MqttException e) {
            e.printStackTrace();
            System.out.println("E2");

        }
    }
    public void getDataSD(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt){
        //System.out.println("ciao");
        float value;
        int value2;
        value = ByteBuffer.wrap(characteristic.getValue()).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        value2 = ByteBuffer.wrap(characteristic.getValue()).order(ByteOrder.LITTLE_ENDIAN).getInt();
        nWrote++;
        if (Float.compare(Float.valueOf("0.02"),value)==0){
            if (startReceive) {
                startReceive = false;
                nWrote = 0;
                System.out.println("A");
                postAllData();
            /*runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //post.setEnabled(true);
                    //post.setClickable(true);
                }
            });*/
            }


        }
        else if (Float.compare(Float.valueOf("0.03"),value)==0){
            System.out.println("req2");
            temperatureCharData.setValue("req");
            mBluetoothGatt.writeCharacteristic(temperatureCharData);
        }
        else {

            System.out.println(value+" NWROTE: "+nWrote);
            //System.out.println(nWrote);
            valueList.add(value);


            /*int millis=0;
            final Handler handler1=new Handler(getMainLooper());
            handler1.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (mBluetoothGatt){

                        temperatureCharData.setValue("ok");
                        System.out.println("STATO CONN "+bluetoothManager.getConnectionState(mBluetoothGatt.getDevice(),BluetoothGatt.GATT));
                        mBluetoothGatt.writeCharacteristic(temperatureCharData);

                    }
                }
            },1000);*/

            /*final Handler handler1=new Handler(getMainLooper());
            handler1.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!wrote){
                        System.out.println("WROTE FALSE");
                        synchronized (mBluetoothGatt){
                            temperatureCharData.setValue("ok");
                            mBluetoothGatt.writeCharacteristic(temperatureCharData);
                        }
                    }
                    else{
                        System.out.println("WROTE TRUE");
                    }
                    synchronized (controlled){
                        controlled=true;
                    }
                }
            },500);*/

            /*boolean isWrote=wrote;
            while (!isWrote && millis<1000){
                try {
                    isWrote=wrote;
                    millis+=10;
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (!isWrote){
                System.out.println("WRITE FALSE");
                gatt.writeCharacteristic(characteristic);
            }*/


            //System.out.println(temperatureCharData.getStringValue(0) + "wr= "+wrote);
            //mBluetoothGatt.writeCharacteristic(temperatureCharData);
            //System.out.println("OK");
        }
    }

    private void scanLeDevice(final boolean enable) {
        final BluetoothLeScanner bluetoothLeScanner=bluetoothAdapter.getBluetoothLeScanner();
        if (enable) {
            // Stops scanning after a pre-defined scan period
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            mScanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }


    }
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            final BluetoothDevice device=result.getDevice();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(!bleDeviceArrayList.contains(device)) {
                        bleDeviceArrayList.add(device);
                        bleDeviceArrayString.add(device.getName() + " - " + device.getAddress());
                        System.out.println(device.getName() + " - " + device.getAddress());
                        bleDeviceAdapterString.notifyDataSetChanged();
                    }
                }
            });
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            final List<BluetoothDevice> devices=null;
            for (int i=0;i<results.size();i++){
                devices.add(results.get(i).getDevice());
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (int i=0;i<devices.size();i++) {
                        if (!bleDeviceArrayList.contains(devices.get(i))) {
                            bleDeviceArrayList.add(devices.get(i));
                            bleDeviceArrayString.add(devices.get(i).getName() + " - " + devices.get(i).getAddress());
                            System.out.println(devices.get(i).getName() + " - " + devices.get(i).getAddress());
                            bleDeviceAdapterString.notifyDataSetChanged();
                        }
                    }
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mTextMessage = findViewById(R.id.message);
        listDevice=findViewById(R.id.listDevice);
        dataView=findViewById(R.id.dataView);
        lastView=findViewById(R.id.lastView);
        scan=findViewById(R.id.scan);
        getData=findViewById(R.id.getData);
        getLast=findViewById(R.id.getLast);
        scan.setOnClickListener(scanListener);
        getData.setOnClickListener(getDataListener);

        //post.setOnClickListener(postListener);
        getData.setEnabled(false);
        getData.setClickable(false);
        mScanning=false;
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        locationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleDeviceArrayList=new ArrayList<>();
        bleDeviceArrayString=new ArrayList<>();
        bleDeviceAdapterString=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,bleDeviceArrayString);
        listDevice.setAdapter(bleDeviceAdapterString);
        listDevice.setOnItemClickListener(listDeviceListener);
        handler=new Handler();
        valueList=new ArrayList<>();
        startReceive=false;
    }
    /*private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.activity_main, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (ListView)findViewById(R.id.listDevice);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText("Unknown device");
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }*/

}
