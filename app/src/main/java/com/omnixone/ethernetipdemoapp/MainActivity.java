package com.omnixone.ethernetipdemoapp;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.omnixone.ethernetiplibrary.CppDataListener;
import com.omnixone.ethernetiplibrary.EtherNetIPLibrary;
import com.omnixone.ethernetiplibrary.OpenerIdentity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements CppDataListener {

    private EtherNetIPLibrary etherNetIPLibrary;
    private TextView txtResult,txtIPAddress, txtLog, dataListener;
    EditText input_value;
    boolean isStarted = false;
    // Declare SensorManager and Sensor for accelerometer
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isAccelerometerRunning = false;

    TextView tvInputAssemblySensorData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String ipAddress = NetworkUtils.getWlan0IpAddress();

        etherNetIPLibrary = new EtherNetIPLibrary();

        // Register listener
        EtherNetIPLibrary.setCppDataListener(this);

        txtResult = findViewById(R.id.txtResult);
        txtLog = findViewById(R.id.txtLog);
        txtIPAddress = findViewById(R.id.txtIpAddress);
        input_value = findViewById(R.id.input_value);
        dataListener = findViewById(R.id.dataListener);
        tvInputAssemblySensorData = findViewById(R.id.tvInputAssemblySensorData);


        txtIPAddress.setText("IP : "+ipAddress);
        Button btnGetVersion = findViewById(R.id.btnGetVersion);
        Button btnStart = findViewById(R.id.btnStartStack);
        Button btnStop = findViewById(R.id.btnStopStack);

        btnGetVersion.setOnClickListener(v -> {

            if(isStarted)
            {

                // Start reading accelerometer data continuously if it's not already running
                String inputText = input_value.getText().toString();

                byte[] inputBytes = inputText.getBytes(StandardCharsets.US_ASCII); // max 7-bit

                // Fill the g_assembly_data064[32]
                for (int i = 0; i < 32; i++) {
                    byte value = (i < inputBytes.length) ? inputBytes[i] : 0x00;
                    //etherNetIPLibrary.setInputValue(i, value);
                }

                Toast.makeText(getApplicationContext(), "Data written to g_assembly_data064[0–31]", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(getApplicationContext(), "Please click on start button", Toast.LENGTH_SHORT).show();
            }

        });





        btnStart.setOnClickListener(v -> {
            try {
                isStarted = false;
                String iface = "wlan0"; // 🔁 Replace with actual Android interface name
                String result = etherNetIPLibrary.startStack(iface);
                txtLog.setText(result);

                OpenerIdentity identity = etherNetIPLibrary.getIdentity();
                StringBuilder resultText = new StringBuilder();
                resultText.append("Device Details ").append("\n");
                resultText.append("Vendor ID: ").append(identity.vendorId).append("\n");
                resultText.append("Device Type: ").append(identity.deviceType).append("\n");
                resultText.append("Product Code: ").append(identity.productCode).append("\n");
                resultText.append("Revision: ").append(identity.majorRevision)
                        .append(".").append(identity.minorRevision).append("\n");
                resultText.append("Serial Number: ").append(identity.serialNumber).append("\n");
                resultText.append("Product Name: ").append(identity.productName).append("\n");
                if(identity.productName!=null)
                {
                    isStarted = true;
                }

                txtResult.setText(resultText.toString());
                if (!isAccelerometerRunning) {
                    startAccelerometer();
                }
            }catch (Exception e)
            {
                isStarted = false;
            }


        });

        btnStop.setOnClickListener(v -> {
            etherNetIPLibrary.stopStack();
            txtLog.setText("OpENer stopped.");
            if (isAccelerometerRunning) {
                stopAccelerometer();
            }
        });
    }


    @Override
    public void onCppDataReceived(byte[] data) {
        runOnUiThread(() -> {
            StringBuilder hexBuilder = new StringBuilder();
            for (int i = 4; i < data.length; i++) {
                if ((i-4) % 16 ==0)
                    hexBuilder.append(String.format("\n"));
                hexBuilder.append(String.format("%02X ", data[i]));
            }
            dataListener.setText("Received (HEX): \n" + hexBuilder.toString().trim());
        });
    }

    @Override
    protected void onDestroy() {
        etherNetIPLibrary.stopStack();
        super.onDestroy();
    }


    // Start accelerometer to continuously capture data
    private void startAccelerometer() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Register the sensor event listener to listen to accelerometer changes
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);

        isAccelerometerRunning = true;
    }

    private long lastTimestamp = 0; // Variable to store the last timestamp
    private static final long MIN_INTERVAL_MS = 1000; // Minimum interval of 50ms
    // SensorEventListener to capture accelerometer data
    private SensorEventListener sensorEventListener = new SensorEventListener() {
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


    // Send the accelerometer data (x, y, z)
    private void sendAccelerometerData(float x, float y, float z) {
        // Format the float values to 4 decimal places and create a comma-separated string

        String accelerometerDataString = String.format("%.2f,%.2f,%.2f", x, y, z);

        // Convert the string into a byte array (UTF-8 encoding)
        byte[] accelerometerData = accelerometerDataString.getBytes(StandardCharsets.UTF_8);

        // Ensure the byte array fits within the required 128 bytes
        byte[] dataToSend = new byte[128];
        System.arraycopy(accelerometerData, 0, dataToSend, 0, Math.min(accelerometerData.length, 128));

        // Send the byte array using the existing JNI method
        etherNetIPLibrary.setInputValues(dataToSend);
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
        if (isAccelerometerRunning) {
            sensorManager.unregisterListener(sensorEventListener);
            isAccelerometerRunning = false;
        }
    }
}
