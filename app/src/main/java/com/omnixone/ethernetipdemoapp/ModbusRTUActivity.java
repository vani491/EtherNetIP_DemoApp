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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.omnixone.modbuslibrary.procimg.SimpleProcessImage;
import com.omnixone.modbuslibrary.procimg.SimpleRegister;
import com.omnixone.modbuslibrary.slave.ModbusSlave;
import com.omnixone.modbuslibrary.slave.ModbusSlaveFactory;
import com.omnixone.modbuslibrary.util.SerialParameters;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class ModbusRTUActivity extends AppCompatActivity {

    Button startRTUConnection, stopRTUConnection;
    TextView txtIPAddress, txtSlaveInfo, txtCommand, txtCommandOne, txtCommandTwo;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isAccelerometerRunning = false;

    private SimpleRegister[] accelRegisters = new SimpleRegister[6]; // 0-5 for x/y/z

    private ObservableRegister commandRegister; // for data written from Simply Modbus
    private ObservableRegister commandOneRegister; // for data written from Simply Modbus
    private ObservableRegister commandTwoRegister; // for data written from Simply Modbus


    private ModbusSlave slave;
    private int slavePort = 0;
    private int slaveUnitId = 0;

    TextView tvInputAssemblySensorData;

    private int lastCommandValue = -1;
    private int lastCommandValueOne = -1;
    private int lastCommandValueTwo = -1;
    String serialPortPath;
    int baudRate;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modbus_rtu);
        startRTUConnection = findViewById(R.id.startRTUConnection);
        stopRTUConnection = findViewById(R.id.stopRTUConnection);
        tvInputAssemblySensorData = findViewById(R.id.tvInputAssemblySensorData);
        txtSlaveInfo = findViewById(R.id.txtSlaveInfo);
        txtCommand = findViewById(R.id.txtCommand);
        txtCommandOne = findViewById(R.id.txtCommandOne);
        txtCommandTwo = findViewById(R.id.txtCommandTwo);

        Spinner spinner = findViewById(R.id.spinnerPorts);
        populateSpinner(spinner);

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        txtIPAddress = findViewById(R.id.txtIpAddress);
        txtIPAddress.setText("IP : " + ip);

        startRTUConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startModbusRtuSlave();
                startAccelerometer();
            }
        });

        stopRTUConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRtuConnection();
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



    public void startModbusRtuSlave() {
        try {

            serialPortPath = "/dev/ttyS0";
            baudRate = 9600;

            SerialParameters params = new SerialParameters();
            params.setPortName(serialPortPath); // your serial port path, e.g., USB or RS485 adapter
            params.setBaudRate(baudRate);
            params.setDatabits(8);
            params.setParity("None");
            params.setStopbits(1);
            params.setEncoding("rtu");  // very important for Modbus RTU
            params.setEcho(false);



            // Create Serial Slave instead of TCP Slave
            slave = ModbusSlaveFactory.createSerialSlave(params);

            int unitId = 1; // Slave ID
            this.slaveUnitId = unitId;

            SimpleProcessImage spi = new SimpleProcessImage(unitId);

            // Accelerometer registers
            for (int i = 0; i < 6; i++) {
                accelRegisters[i] = new SimpleRegister(0);
                spi.addRegister(accelRegisters[i]);
            }

            // Command registers
            commandRegister = new ObservableRegister(0);
            commandOneRegister = new ObservableRegister(0);
            commandTwoRegister = new ObservableRegister(0);

            spi.addRegister(commandRegister);
            spi.addRegister(commandOneRegister);
            spi.addRegister(commandTwoRegister);

            slave.addProcessImage(unitId, spi);
            slave.open(); // Start serial slave

            startCommandPolling();
            updateSlaveStatusUI(true);
            Log.d("MODBUS", "Modbus RTU Slave started on port " + params.getPortName());

        } catch (Exception e) {
            Log.e("MODBUS", "Error starting RTU slave", e);
            updateSlaveStatusUI(false);
        }
    }



    private void updateSlaveStatusUI(boolean isRunning) {
        String status = isRunning ? "RUNNING" : "STOPPED";

        // Ensure serialPortPath and baudRate are shown safely
        String port = (serialPortPath != null) ? serialPortPath : "N/A";
        String baud = String.valueOf(baudRate);

        if (isRunning) {
            txtSlaveInfo.setText(
                    "Modbus RTU Slave: " + status + " ✅\n" +
                            "Slave ID: " + slaveUnitId + "\n" +
                            "Serial Port: " + port + "\n" +
                            "Baud Rate: " + baud
            );
        } else {
            txtSlaveInfo.setText(
                    "Modbus RTU Slave: " + status + " ❌\n" +
                            "Slave ID: " + slaveUnitId
            );
        }
    }


    private void stopRtuConnection() {
        try {
            if (slave != null) {
                slave.close();
                Log.d("MODBUS", "Modbus RTU Slave stopped.");
                slave = null;
                updateSlaveStatusUI(false);
            }
        } catch (Exception e) {
            Log.e("MODBUS", "Error stopping RTU slave", e);
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
        stopRtuConnection();
        stopAccelerometer();
    }



    private int[] floatToRegisters(float value) {
        int intBits = Float.floatToIntBits(value);
        int high = (intBits >> 16) & 0xFFFF;
        int low = intBits & 0xFFFF;
        return new int[]{high, low};
    }


    private void startCommandPolling() {
        new Thread(() -> {
            while (slave != null) {
                try {
                    int currentValue = commandRegister.getValue();
                    if (currentValue != lastCommandValue) {
                        lastCommandValue = currentValue;

                        runOnUiThread(() -> {
                            txtCommand.setText("" + currentValue);
                            Log.d("SHIVANI", "Received from One : " + currentValue);
                        });
                    }

                    int currentValueOne = commandOneRegister.getValue();
                    if (currentValueOne != lastCommandValueOne) {
                        lastCommandValueOne = currentValueOne;

                        runOnUiThread(() -> {
                            txtCommandOne.setText("" + currentValueOne);
                            Log.d("SHIVANI", "Received from Two: " + currentValueOne);
                        });
                    }

                    int currentValueTwo = commandTwoRegister.getValue();
                    if (currentValueTwo != lastCommandValueTwo) {
                        lastCommandValueTwo = currentValueTwo;

                        runOnUiThread(() -> {
                            txtCommandTwo.setText("" + currentValueTwo);
                            Log.d("SHIVANI", "Received from Three: " + currentValueTwo);
                        });
                    }

                    Thread.sleep(500); // Check every 500ms
                } catch (Exception e) {
                    Log.e("SHIVANI", "Polling error", e);
                    break;
                }
            }
        }).start();
    }


    private void populateSpinner(Spinner spinner) {
        List<String> ports = getTtyPorts();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                ports
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }


    public List<String> getTtyPorts() {
        List<String> portList = new ArrayList<>();
        File devDirectory = new File("/dev");

        if (devDirectory.exists() && devDirectory.isDirectory()) {
            File[] files = devDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    // Match common serial device names
                    if (name.startsWith("tty") || name.startsWith("ttyUSB") || name.startsWith("ttyACM")) {
                        portList.add("/dev/" + name);
                    }
                }
            }
        }
        return portList;
    }


}
