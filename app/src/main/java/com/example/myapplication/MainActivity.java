package com.example.myapplication;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_ENABLE_BT = 1; // 블루투스 실행 확인 값
    private final static int REQUEST_FINE_LOCATION = 2; //위치 정보 제공 확인 값

    Button btn_start;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //UI 생성
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestEnableBLE();
        requestLocationPermission();

        btn_start = (Button) findViewById(R.id.btn_start);
        btn_start.setOnClickListener(view -> {
                    Intent intent = new Intent(MainActivity.this, ScanAndConnectDevice.class);
                    startActivity(intent);
                }
        );
    }


    private void requestEnableBLE(){ //블루투스 연결하게 설정
        Intent ble_enable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(ble_enable_intent, REQUEST_ENABLE_BT);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestLocationPermission(){//위치 정보 제공하라고 설정
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }
}