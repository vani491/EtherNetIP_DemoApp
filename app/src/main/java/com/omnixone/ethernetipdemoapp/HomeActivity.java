package com.omnixone.ethernetipdemoapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    Button ethernetIP, Modbus, ModbusRTU;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ethernetIP =  findViewById(R.id.ethernetIP);
        Modbus =  findViewById(R.id.Modbus);
        ModbusRTU =  findViewById(R.id.ModbusRTU);

        ethernetIP.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent in = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(in);


            }
        });

        Modbus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent modbusActivity = new Intent(HomeActivity.this, ModbusTCPActivity.class);
                startActivity(modbusActivity);


            }
        });
 ModbusRTU.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent modbusActivity = new Intent(HomeActivity.this, ModbusSerialActivity.class);
                startActivity(modbusActivity);


            }
        });


    }

}
