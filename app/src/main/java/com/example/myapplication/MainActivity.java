package com.example.myapplication;
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
import android.widget.Button;
import android.widget.TextView;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleReadCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.clj.fastble.utils.HexUtil;
import com.Sleepdoc_10_min_data_type;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
public class MainActivity extends AppCompatActivity {

    private final static String TAG = ".Central"; // Log 태그
    private final static int REQUEST_ENABLE_BT = 1; // 블루투스 실행 확인 값
    private final static int REQUEST_FINE_LOCATION = 2; //위치 정보 제공 확인 값
    private final static int SCAN_PERIOD = 5000; // 스캔 시간 5초
    private BluetoothAdapter ble_adapter_; // 블루트스 어뎁터
    private boolean is_scanning_ = false; // 스캔중인지 확인하는 불
    private boolean connected_ = false;

    private Map<String, BluetoothDevice> scan_results_; // 스캔된 기기값들 저장하는 배열 -> 중복 저장 가능
    private ScanCallback scan_cb_; // 스캔 할 때 일어나는 경우의 수에 대한 행동을 저장
    private BluetoothLeScanner ble_scanner_; // 스캐너
    private BluetoothGatt ble_gatt_; // 블루투스 연결
    private Handler scan_handler_; // 스캔 핸들러 -> 스캔 종료할 때 사용


    private TextView tv_status_;
    private Button btn_scan_;
    private Button btn_stop_;

    //UUID들 (Poppy Doc)
    private UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private UUID  BATTERY_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private UUID SYNC_SERVICE_UUID = UUID.fromString("0000fffa-0000-1000-8000-00805f9b34fb");//Sync Service UUID
    private UUID SYNC_CONTROL_CHAR_UUID = UUID.fromString("0000FFFA-0000-1000-8000-00805f9b34fb");//Sync characteristic1 UUID
    private UUID SYNC_DATA_CHAR_UUID = UUID.fromString("0000FFFB-0000-1000-8000-00805f9b34fb");//Sync characteristic2 UUID

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //UI 생성
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_status_ = (TextView)this.findViewById(R.id.helloText);
        btn_scan_ = (Button)this.findViewById(R.id.btn_scan);
        btn_stop_ = (Button)this.findViewById(R.id.btn_stop);

        BluetoothManager ble_manager; //블루투스 매니저 생성
        ble_manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE); // 해당 기기의 블루투스 서비스를 받아와서 저장
        ble_adapter_ = ble_manager.getAdapter(); // 해당 기기의 블루투스 어뎁터 가져옴
        btn_scan_.setOnClickListener((v)->{startScan(v);}); //스캔 버튼 클릭시 실행
        btn_stop_.setOnClickListener((v)->{stopScan();}); // 스캔 종료버튼 클릭 시 실행

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
    private void startScan(View v){
        tv_status_.setText("Scanning...");

        if(ble_adapter_ == null || !ble_adapter_.isEnabled()){ //어덥터가 없거나 블루투스가 켜져있지 않을 경우
            requestEnableBLE(); //블루투스 연결 요청
            tv_status_.setText("Scanning Failed: ble not enabled");
            return;
        }

        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){//위치 정보 제공이 막혀있을 경우
            requestLocationPermission();//위치정보 제공 요청
            tv_status_.setText("Scanning Failed: no find location");
            return;
        }
        disconnectGattServer(); //먼저 연결되어 있던 블루투스 제거

        List<ScanFilter> filters = new ArrayList<>(); //블루투스 스캔 시 규칙 설정(어느 UUID, 어느 MAC)
        ScanFilter scan_filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SYNC_CONTROL_CHAR_UUID)).build(); // 퍼피닥 UUID 넣어서 퍼피닥 디바이스만 찾을 수 있게 만듬
        filters.add(scan_filter);

        ScanSettings settings = new ScanSettings.Builder()//블루투스 스캔 방법 설정
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(); // 저전력으로 하겠다

        scan_results_ = new HashMap<>(); //스캔시 찾은 결과값 저장하는 map
        scan_cb_ = new BLEScanCallback(scan_results_); // BLEScanCallback으로 스캔 시 해당 기능 수행
        ble_scanner_ = ble_adapter_.getBluetoothLeScanner(); //저전력 스캐너 장착
        ble_scanner_.startScan(filters,settings,scan_cb_); //앞서 설정한 방법 및 규칙으로 BLESanCallback 실행
        is_scanning_ = true;

        scan_handler_ = new Handler();//종료 핸들러
        scan_handler_.postDelayed(this::stopScan, SCAN_PERIOD);//스캔 후 5초있다가 종료

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopScan(){//스캔 정지
        if(is_scanning_ && ble_adapter_ != null && ble_adapter_.isEnabled() && ble_scanner_ != null){//스캔중이거나 어뎁터 있고, 실행하며, 스캐너가 있을 경우
            ble_scanner_.stopScan(scan_cb_);//정지
            scanComplete();//스캔 결과값을 토대로 연결 진행
        }
        scan_cb_ = null;
        is_scanning_ = false;
        scan_handler_ = null;
        tv_status_.setText("scanning stopped");
    }

    private void scanComplete(){
        if(scan_results_.isEmpty()){//못찾을 경우
            tv_status_.setText("scan results is empty");
            Log.d(TAG, "scan results is emtpy");
            return;
        }
        String prevDevice = "";
        for(String device_addr : scan_results_.keySet()){
            Log.d(TAG, "Found device: " + device_addr);

            BluetoothDevice device = scan_results_.get(device_addr);
            if(prevDevice.equals(device_addr)){//전 디바이스와 같을 경우 종료
                break;
            }
            assert device != null; //비어있어도 종료
            connectedDevice(device);//해당 기기 연결!
            prevDevice = device_addr;
        }
    }

    private void connectedDevice(BluetoothDevice _device){//기기 연결
        tv_status_.setText("Connecting to "+ _device.getAddress());
        GattClientCallback gatt_client_cb = new GattClientCallback();//연결 콜백 설정
        ble_gatt_ = _device.connectGatt(this,false,gatt_client_cb);//해당 콜백을 토대로 연결 시작
    }

    private class GattClientCallback extends BluetoothGattCallback{//연결 콜백
        @Override
        public void onConnectionStateChange(BluetoothGatt _gatt, int _status, int _new_state){//연결 상태가 변할 경우 실행됨
            super.onConnectionStateChange(_gatt,_status,_new_state);
            if(_status == BluetoothGatt.GATT_FAILURE){//연결 실패시
                disconnectGattServer();//gatt서버 종료
                return;
            }else if(_status != BluetoothGatt.GATT_SUCCESS){//성공이 아닐 시
                disconnectGattServer();
                return;
            }
            if(_new_state == BluetoothProfile.STATE_CONNECTED){//연결 될 경우
                connected_ = true;
                Log.d(TAG,"Connected to the Gatt server");//서버 시작
                _gatt.discoverServices();//서버 시작 메소드
            }else if(_new_state == BluetoothProfile.STATE_DISCONNECTED){//끊겼을 경우
                disconnectGattServer(); //서버 종료
            }
        }

    }
    public void disconnectGattServer(){//연결 종료 시
        Log.d(TAG, "Closing Gatt connection");
        connected_ = false;

        if(ble_gatt_ != null){
            ble_gatt_.disconnect();
            ble_gatt_.close();
        }
    }

    private void requestEnableBLE(){ //블루투스 연결하게 설정
        Intent ble_enable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(ble_enable_intent, REQUEST_ENABLE_BT);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestLocationPermission(){//위치 정보 제공하라고 설정
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private class BLEScanCallback extends ScanCallback{//스캔시 사용되는 콜백
        private Map<String, BluetoothDevice> cb_scan_results_;

        BLEScanCallback(Map<String, BluetoothDevice> _scan_results){
            cb_scan_results_ = _scan_results;
        }

        @Override
        public void onScanResult(int _callback_type, ScanResult _result){//스캔 했을 때 발견했을 경우
            Log.d(TAG,"onScanResult");
            addScanResult(_result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> _results){//잘 모르겠습니다 ㅠㅠ
            for(ScanResult result : _results){
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int _error){//스캔 실패 될 경우 에러 메세지 출력
            Log.e(TAG,"BLE scan failed with coed"+_error);
        }
        private void addScanResult(ScanResult _result){//디바이스 찾을 경우 실행되는 함수
            BluetoothDevice device= _result.getDevice();//디바이스 가져옴
            String device_address = device.getAddress();//디바이스의 mac주소 가져옴
            cb_scan_results_.put(device_address,device);//최종 저장되는 배열에 저장
            Log.d(TAG, "scan results device: "+device+ "  Name: "+device.getName());//로그 출력

            tv_status_.setText("add scanned device: "+device_address);
        }

    }

}