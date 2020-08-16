package com.example.myapplication;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.Manifest;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleReadCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.clj.fastble.utils.HexUtil;
import com.Sleepdoc_ext_interface_data_type;
import com.Sleepdoc_10_min_data_type;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class ScanAndConnectDevice extends AppCompatActivity {
    private static boolean isSyncStarted = true;
    private final static String TAG = ".Central"; // Log 태그
    private BluetoothAdapter ble_adapter_; // 블루트스 어뎁터
    private boolean is_scanning_ = false; // 스캔중인지 확인하는 불
    private boolean connected_ = false;

    private Map<String, BluetoothDevice> scan_results_; // 스캔된 기기값들 저장하는 배열 -> 중복 저장 가능
    private ScanCallback scan_cb_; // 스캔 할 때 일어나는 경우의 수에 대한 행동을 저장
    private BluetoothLeScanner ble_scanner_; // 스캐너
    private BluetoothGatt ble_gatt_; // 블루투스 연결
    private Handler scan_handler_; // 스캔 핸들러 -> 스캔 종료할 때 사용
    private BluetoothManager ble_manager;

    private BleManager newBleM;
    private BleDevice newBleD;
    private NotificationCompat.Builder builder;
    private NotificationManager notiManager;
    private NotificationChannel notificationChannel;


    private Button btn_scan_;


    private static String ACTION_GET_CMD = "bingo_action_get_cmd";
    private static String ACTION_VIEW_PROGRESS = "bingo_action_view_progress";
    private static String CHANNEL_ID = "BINGO_SERVICE_CHANNEL";
    private static String CHANNEL_NAME = "BINGO_EXECUTE_SERVICE";
    private static final byte SYNC_CONTROL_START = 0x01; //시작 20바이트 읽어오기
    private static final byte SYNC_CONTROL_PREPARE_NEXT = 0x02; //다음 20바이트 읽어오기
    private static final byte SYNC_CONTROL_DONE = 0x03; // ?
    private ByteArrayOutputStream syncDataStream;

    private static final byte SYNC_NOTI_READY = 0x11;
    private static final byte SYNC_NOTI_NEXT_READY = 0x12;
    private static final byte SYNC_NOTI_DONE = 0x13;
    private static final byte SYNC_NOTI_ERROR = (byte)0xFF;
    private static int count;
    private static final int NOTIFICATION_ID = 999;
    private int totalSyncBytes;
    private ArrayList<Sleepdoc_10_min_data_type> dataList;

    //UUID들 (Poppy Doc)
    private UUID SYNC_SERVICE_UUID = UUID.fromString("0000fffa-0000-1000-8000-00805f9b34fb");//Sync Service UUID
    private UUID SYNC_CONTROL_CHAR_UUID = UUID.fromString("0000FFFA-0000-1000-8000-00805f9b34fb");//Sync characteristic1 UUID
    private UUID SYNC_DATA_CHAR_UUID = UUID.fromString("0000FFFB-0000-1000-8000-00805f9b34fb");//Sync characteristic2 UUID
    private UUID[] serviceUuids = new UUID[1];
    private List<BleDevice> myDevices;

    ArrayList<FindDeviceInfo> lists = new ArrayList<FindDeviceInfo>();
    ListView listView;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //UI 생성
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan_and_connect_device);

        totalSyncBytes = 0;
        serviceUuids[0] = SYNC_SERVICE_UUID; //퍼피닥 기기만 찾기 위해 생성
        setTitle("연결");

        listView = (ListView)findViewById(R.id.mainlist);

        //블루투스 매니저 생성

        BleManager.getInstance().init(getApplication());
        newBleM = BleManager.getInstance();


        ble_manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE); // 해당 기기의 블루투스 서비스를 받아와서 저장
        ble_adapter_ = ble_manager.getAdapter(); // 해당 기기의 블루투스 어뎁터 가져옴

        // btn_scan_.setOnClickListener((v)->{startScan(v);}); //스캔 버튼 클릭시 실행
        //btn_stop_.setOnClickListener((v)->{stopScan();}); // 스캔 종료버튼 클릭 시 실행

        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
                .setServiceUuids(serviceUuids)
                .setAutoConnect(true)
                .setScanTimeOut(5000)
                .build();
        newBleM.initScanRule(scanRuleConfig);
        ScanSettings settings = new ScanSettings.Builder()//블루투스 스캔 방법 설정
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();

        startScan();

    }

    @Override
    protected void onResume(){
        //재실행 할 경우
        super.onResume();
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){//기기가 블루투스 지원이 안되면 종료
            finish();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startScan(){

        newBleM.scan(new BleScanCallback() {
            @Override
            public void onScanFinished(List<BleDevice> scanResultList) { //스캔 완료시
                Log.d(TAG, "scan F");
                myDevices = scanResultList;
                for(BleDevice d : myDevices){ //찾은 디바이스 이름 및 주소 출력
                    lists.add(new FindDeviceInfo(d.getName(),d));
                }
                ArrayAdapter<FindDeviceInfo> adapter = new DeviceAdapter(ScanAndConnectDevice.this, lists);
                listView.setAdapter(adapter);


            }

            @Override
            public void onScanStarted(boolean success) {
                Log.d(TAG, "scan S");
            }

            @Override
            public void onScanning(BleDevice bleDevice) { //찾는 중 발견한 디바이스 정보 출력
                Log.d(TAG, "scanning");
                Log.d(TAG, bleDevice.getName() + bleDevice.getMac());
                newBleD = bleDevice;
            }
        });


    }



}

class FindDeviceInfo implements Serializable{
    private String name;
    private BleDevice device;

    public FindDeviceInfo(String name, BleDevice device) {
        this.name = name;
        this.device = device;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BleDevice getDevice() {
        return device;
    }

    public void setDevice(BleDevice device) {
        this.device = device;
    }
}

class DeviceAdapter extends ArrayAdapter<FindDeviceInfo>{
    ArrayList<FindDeviceInfo> lists = new ArrayList<FindDeviceInfo>();
    Activity activity;

    public DeviceAdapter(Activity activity, ArrayList<FindDeviceInfo> lists){
        super(activity, R.layout.list_view, lists);
        this.lists = lists;
        this.activity = activity;

    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent){
        ViewHolder viewHolder = null;
        FindDeviceInfo info = lists.get(position);
        View itemView = convertView;

        if(itemView == null){
            viewHolder = new ViewHolder();
            itemView = activity.getLayoutInflater().inflate(R.layout.list_view, parent,false);
            viewHolder.tv_name = (TextView)itemView.findViewById(R.id.tv_devicename);
            viewHolder.btn_connect = (Button)itemView.findViewById(R.id.btn_connect);
            itemView.setTag(viewHolder);
        }else{
            viewHolder = (ViewHolder)itemView.getTag();
        }

        viewHolder.tv_name.setText(info.getName());
        viewHolder.btn_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BleManager.getInstance().connect(info.getDevice(), new BleGattCallback() { //연결 -> 젤 처음 찾은 device
                    @Override
                    public void onStartConnect() {
                        Log.d("connect","start");
                    }

                    @Override
                    public void onConnectFail(BleDevice bleDevice, BleException exception) {
                        Log.d("err","Fail");
                    }

                    @Override
                    public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) { //해당 디바이스 연결 완료시
                        Log.d("connect","good");
                        ConnectDevice cd = new ConnectDevice(bleDevice,gatt);
                        cd.connectedDevice(bleDevice);
                    }

                    @Override
                    public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                        Log.d("connect","MissConnection");
                    }
                });
            }
        });
        return itemView;
    }


}

class ViewHolder{
    public TextView tv_name;
    public Button btn_connect;
}

class ConnectDevice{
    private static boolean isSyncStarted = true;
    private final static String TAG = ".Central"; // Log 태그
    private BluetoothAdapter ble_adapter_; // 블루트스 어뎁터
    private boolean is_scanning_ = false; // 스캔중인지 확인하는 불
    private boolean connected_ = false;

    private Map<String, BluetoothDevice> scan_results_; // 스캔된 기기값들 저장하는 배열 -> 중복 저장 가능
    private ScanCallback scan_cb_; // 스캔 할 때 일어나는 경우의 수에 대한 행동을 저장
    private BluetoothLeScanner ble_scanner_; // 스캐너
    private BluetoothGatt ble_gatt_; // 블루투스 연결
    private Handler scan_handler_; // 스캔 핸들러 -> 스캔 종료할 때 사용
    private BluetoothManager ble_manager;

    private BleManager newBleM;
    private BleDevice newBleD;
    private NotificationCompat.Builder builder;
    private NotificationManager notiManager;
    private NotificationChannel notificationChannel;


    private Button btn_scan_;


    private static String ACTION_GET_CMD = "bingo_action_get_cmd";
    private static String ACTION_VIEW_PROGRESS = "bingo_action_view_progress";
    private static String CHANNEL_ID = "BINGO_SERVICE_CHANNEL";
    private static String CHANNEL_NAME = "BINGO_EXECUTE_SERVICE";
    private static final byte SYNC_CONTROL_START = 0x01; //시작 20바이트 읽어오기
    private static final byte SYNC_CONTROL_PREPARE_NEXT = 0x02; //다음 20바이트 읽어오기
    private static final byte SYNC_CONTROL_DONE = 0x03; // ?
    private ByteArrayOutputStream syncDataStream;

    private static final byte SYNC_NOTI_READY = 0x11;
    private static final byte SYNC_NOTI_NEXT_READY = 0x12;
    private static final byte SYNC_NOTI_DONE = 0x13;
    private static final byte SYNC_NOTI_ERROR = (byte)0xFF;
    private static int count;
    private static final int NOTIFICATION_ID = 999;
    private int totalSyncBytes;
    private ArrayList<Sleepdoc_10_min_data_type> dataList;

    //UUID들 (Poppy Doc)
    private UUID SYNC_SERVICE_UUID = UUID.fromString("0000fffa-0000-1000-8000-00805f9b34fb");//Sync Service UUID
    private UUID SYNC_CONTROL_CHAR_UUID = UUID.fromString("0000FFFA-0000-1000-8000-00805f9b34fb");//Sync characteristic1 UUID
    private UUID SYNC_DATA_CHAR_UUID = UUID.fromString("0000FFFB-0000-1000-8000-00805f9b34fb");//Sync characteristic2 UUID
    private UUID[] serviceUuids = new UUID[1];
    private List<BleDevice> myDevices;


    public ConnectDevice(BleDevice newBleD, BluetoothGatt ble_gatt_){
        this.newBleD = newBleD;
        this.ble_gatt_ = ble_gatt_;
    }


    public void connectedDevice(BleDevice _device) {//기기 연결
        BleManager bleManager = BleManager.getInstance();
        String name = _device.getName();
        String mac = _device.getMac();
        Log.d("Name", name + " " + mac);

        BluetoothGatt gatt = bleManager.getBluetoothGatt(_device);
        BluetoothGattCharacteristic syncControlChar = gatt.getService(SYNC_SERVICE_UUID).getCharacteristic(SYNC_CONTROL_CHAR_UUID);
        syncDataStream = new ByteArrayOutputStream();

        final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = syncControlChar.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);

        bleManager.notify(_device, SYNC_SERVICE_UUID.toString(), SYNC_CONTROL_CHAR_UUID.toString(), new BleNotifyCallback() {
            //Sync Notify ON Success
            @Override
            public void onNotifySuccess() { //노티 성공시
                Log.i("bManager.notify", "Success");
                //Sync Data Read Start
                bleManager.write(_device, SYNC_SERVICE_UUID.toString(), SYNC_CONTROL_CHAR_UUID.toString(), new byte[]{SYNC_CONTROL_START}, syncControlWriteCallback);
                // byte 0x01 송신
            }

            @Override
            public void onNotifyFailure(BleException exception) { //실패시
                Log.i("bManager.notify", exception.toString());
            }

            @Override
            public void onCharacteristicChanged(byte[] data) { //char값 변경시
                Log.i("bManager.notify", HexUtil.formatHexString(data, true));
                if (data[0] != SYNC_NOTI_DONE) {
                    bleManager.read(_device, SYNC_SERVICE_UUID.toString(), SYNC_DATA_CHAR_UUID.toString(), syncControlReadCallback);
                }
            }
        });
    }

    BleWriteCallback syncControlWriteCallback = new BleWriteCallback() {
        @Override
        public void onWriteSuccess(int current, int total, byte[] justWrite) {
            Log.i("GET_DEVICE_DATA",  "write success, current: " + current
                    + " total: " + total
                    + " justWrite: " + HexUtil.formatHexString(justWrite, true));
        }
        @Override
        public void onWriteFailure(BleException exception) {
            Log.i("GET_DEVICE_DATA", "Fail\n"+exception.toString());

        }
    };

    BleReadCallback syncControlReadCallback = new BleReadCallback() {
        @Override
        public void onReadSuccess(byte[] values) {


            int len = values[0];
            if( len != 0 ) {
                syncDataStream.write(values, 1, len);
                Log.i("read", "Read Data len : " + len);
                Log.i("GET_DEVICE_DATA", "readDataSize :"+syncDataStream.size() + "  SleepdocDataSize : "+ Sleepdoc_ext_interface_data_type.size());

                if(syncDataStream.size()>=Sleepdoc_ext_interface_data_type.size()){
                    Sleepdoc_ext_interface_data_type extData;
                    byte[] stream = syncDataStream.toByteArray();
                    byte[] data = new byte[Sleepdoc_ext_interface_data_type.size()];
                    System.arraycopy(stream, stream.length-data.length, data, 0, data.length);
                    extData = new Sleepdoc_ext_interface_data_type(data);
                    if(totalSyncBytes == 0){
                        totalSyncBytes = extData.remainings * Sleepdoc_10_min_data_type.size();
                    }

                    if( syncDataStream.size() % Sleepdoc_ext_interface_data_type.size() == 0 ) {
                        Log.d("TAG", "sleepdoc_ext_interface_data_type 만큼 받음.");
                        for (int i = 0; i < 6; i++) {
                            Sleepdoc_10_min_data_type d = extData.d[i];
                            Log.d("TAG", String.format("  sleepdoc_10_min_data_type \t%d\t\t%d\t\t%d\t%d\t%d\t%d\t%d\t%d\t%d",
                                    d.s_tick, d.e_tick, d.steps, d.t_lux, d.avg_lux, d.avg_k, d.vector_x, d.vector_y, d.vector_z));
                            Log.d("TAG", "device timezone: " + extData.time_zone);

                        }
                    }
                }

                //다음 패킷 요청하기 0x02 송신
                BleManager.getInstance().write(newBleD, SYNC_SERVICE_UUID.toString(), SYNC_CONTROL_CHAR_UUID.toString(), new byte[]{SYNC_CONTROL_PREPARE_NEXT}, syncControlWriteCallback);
            }
        }
        @Override
        public void onReadFailure(BleException exception) {
            Log.i("GET_DEVICE_DATA", "read syncData Fail");
            isSyncStarted = false;

        }
    };
}