package com.omnixone.ethernetipdemoapp;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.omnixone.modbuslibrary.procimg.SimpleProcessImage;
import com.omnixone.modbuslibrary.procimg.SimpleRegister;
import com.omnixone.modbuslibrary.slave.ModbusSlave;
import com.omnixone.modbuslibrary.slave.ModbusSlaveFactory;

import java.net.InetAddress;

public class ModbusActivity extends AppCompatActivity {

    Button startTCPConnection, stopTCPConnection;
    TextView txtIPAddress, txtSlaveInfo, txtCommand;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isAccelerometerRunning = false;

    private SimpleRegister[] accelRegisters = new SimpleRegister[6]; // 0-5 for x/y/z

    private ObservableRegister commandRegister; // for data written from Simply Modbus


    private ModbusSlave slave;
    private int slavePort = 0;
    private int slaveUnitId = 0;

    TextView tvInputAssemblySensorData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modbus);
        startTCPConnection = findViewById(R.id.startTCPConnection);
        stopTCPConnection = findViewById(R.id.stopTCPConnection);
        tvInputAssemblySensorData = findViewById(R.id.tvInputAssemblySensorData);
        txtSlaveInfo = findViewById(R.id.txtSlaveInfo);
        txtCommand = findViewById(R.id.txtCommand);

        String ipAddress = NetworkUtils.getWlan0IpAddress();

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        txtIPAddress = findViewById(R.id.txtIpAddress);
        txtIPAddress.setText("IP : " + ip);

        startTCPConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startModbusTcpSlave();
                startAccelerometer();
            }
        });

        stopTCPConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTcpConnection();
            }
        });

    }


    private void startAccelerometer() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelerometer != null) {
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
            isAccelerometerRunning = true;
        }
    }




    // SensorEventListener to capture accelerometer data
    private final  SensorEventListener sensorEventListener = new SensorEventListener() {

        private long lastTimestamp = 0;
        private static final long MIN_INTERVAL_MS = 1000; // 1 sec interval
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                long currentTime = System.currentTimeMillis(); // Get the current timestamp
                if (currentTime - lastTimestamp >= MIN_INTERVAL_MS) { // Check if 50ms has passed
                    lastTimestamp = currentTime; // Update the last timestamp

                    // Get accelerometer data
                    float x = Math.round(event.values[0] * 100f) / 100f;
                    float y = Math.round(event.values[1] * 100f) / 100f;
                    float z = Math.round(event.values[2] * 100f) / 100f;

                    String accelerometerDataString = String.format("%.2f,%.2f,%.2f", x, y, z);
                    tvInputAssemblySensorData.setText("Accel: "+accelerometerDataString);

                    sendAccelerometerData(x, y, z);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // You can handle sensor accuracy changes if needed
        }
    };

    private void sendAccelerometerData(float x, float y, float z) {
        try {
            int[] xRegs = floatToRegisters(x);
            int[] yRegs = floatToRegisters(y);
            int[] zRegs = floatToRegisters(z);

            accelRegisters[0].setValue(xRegs[0]); // X high
            accelRegisters[1].setValue(xRegs[1]); // X low

            accelRegisters[2].setValue(yRegs[0]); // Y high
            accelRegisters[3].setValue(yRegs[1]); // Y low

            accelRegisters[4].setValue(zRegs[0]); // Z high
            accelRegisters[5].setValue(zRegs[1]); // Z low
        } catch (Exception e) {
            Log.e("MODBUS", "Error updating float registers", e);
        }
    }




    public void startModbusTcpSlave() {
        try {
            InetAddress address = InetAddress.getByName("0.0.0.0");
            int port = 1502;

            slave = ModbusSlaveFactory.createTCPSlave(address, port, 5, false);

            int unitId = 1;

            // Store for UI
            this.slavePort = port;
            this.slaveUnitId = unitId;

            SimpleProcessImage spi = new SimpleProcessImage(unitId);

            // Create 6 registers for 3 floats
            for (int i = 0; i < 6; i++) {
                accelRegisters[i] = new SimpleRegister(0);
                spi.addRegister(accelRegisters[i]);
            }

            // Add one extra register at address 6 for receiving command from master
            commandRegister = new ObservableRegister(0);
            commandRegister.setOnValueChangedListener(new ObservableRegister.OnValueChangedListener() {
                @Override
                public void onValueChanged(int newValue) {
                    runOnUiThread(() -> {
                        txtCommand.setText("Received from Master: " + newValue);
                    });
                }
            });
            spi.addRegister(commandRegister);


            slave.addProcessImage(unitId, spi);
            slave.open();
            updateSlaveStatusUI(true);

            Log.d("MODBUS", "Modbus TCP Slave started on port " + port);

        } catch (Exception e) {
            Log.e("MODBUS", "Error starting slave", e);
            updateSlaveStatusUI(false);
        }
    }


    private void updateSlaveStatusUI(boolean isRunning) {
        String ip = Formatter.formatIpAddress(
                ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE))
                        .getConnectionInfo().getIpAddress()
        );

        String status = isRunning ? "RUNNING" : "STOPPED";

        if(status.equals("RUNNING"))
        {
            txtSlaveInfo.setText(
                    "Modbus TCP Slave: " + status +" ✅ " + "\n" +
                            "Slave ID: " + slaveUnitId + "\n" +
                            "IP Address: " + ip + "\n" +
                            "Port: " + slavePort + "\n" +
                            "Registers Used: 3 (X, Y, Z)"
            );
        }
        else {
            txtSlaveInfo.setText(
                    "Modbus TCP Slave: " + status +" ❌ " + "\n" +
                            "Slave ID: " + slaveUnitId + "\n" +
                            "IP Address: " + ip
            );
        }


    }


    private void stopTcpConnection() {
        try {
            if (slave != null) {
                slave.close();
                Log.d("MODBUS", "Modbus TCP Slave stopped.");
                slave = null; // Optional: clear reference
                updateSlaveStatusUI(false);
            }
        } catch (Exception e) {
            Log.e("MODBUS", "Error stopping Modbus slave", e);
        }
    }

    // Stop accelerometer when no longer needed (to save battery or stop unnecessary data reading)
    private void stopAccelerometer() {
        if (isAccelerometerRunning) {
            sensorManager.unregisterListener(sensorEventListener);
            isAccelerometerRunning = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTcpConnection();
        stopAccelerometer();
    }



    private int[] floatToRegisters(float value) {
        int intBits = Float.floatToIntBits(value);
        int high = (intBits >> 16) & 0xFFFF;
        int low = intBits & 0xFFFF;
        return new int[]{high, low};
    }


}
