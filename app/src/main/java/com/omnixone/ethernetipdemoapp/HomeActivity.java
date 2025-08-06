package com.omnixone.ethernetipdemoapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    Button ethernetIP, Modbus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ethernetIP =  findViewById(R.id.ethernetIP);
        Modbus =  findViewById(R.id.Modbus);

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

                Intent modbusActivity = new Intent(HomeActivity.this, ModbusActivity.class);
                startActivity(modbusActivity);


            }
        });


    }

}
