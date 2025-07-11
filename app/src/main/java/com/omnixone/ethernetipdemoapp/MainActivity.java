package com.omnixone.ethernetipdemoapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.omnixone.ethernetiplibrary.EtherNetIPLibrary;
import com.omnixone.ethernetiplibrary.OpenerIdentity;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private EtherNetIPLibrary etherNetIPLibrary;
    private TextView txtResult,txtIPAddress, txtLog;
    EditText input_value;
    boolean isStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String ipAddress = NetworkUtils.getWlan0IpAddress();

        etherNetIPLibrary = new EtherNetIPLibrary();



        txtResult = findViewById(R.id.txtResult);
        txtLog = findViewById(R.id.txtLog);
        txtIPAddress = findViewById(R.id.txtIpAddress);
        input_value = findViewById(R.id.input_value);

        txtIPAddress.setText("IP : "+ipAddress);
        Button btnGetVersion = findViewById(R.id.btnGetVersion);
        Button btnStart = findViewById(R.id.btnStartStack);
        Button btnStop = findViewById(R.id.btnStopStack);

        btnGetVersion.setOnClickListener(v -> {

            if(isStarted)
            {
                String inputText = input_value.getText().toString();

                byte[] inputBytes = inputText.getBytes(StandardCharsets.US_ASCII); // max 7-bit

                // Fill the g_assembly_data064[32]
                for (int i = 0; i < 32; i++) {
                    byte value = (i < inputBytes.length) ? inputBytes[i] : 0x00;
                    etherNetIPLibrary.setInputValue(i, value);
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
            }catch (Exception e)
            {
                isStarted = false;
            }


        });

        btnStop.setOnClickListener(v -> {
            etherNetIPLibrary.stopStack();
            txtLog.setText("OpENer stopped.");
        });
    }



}
