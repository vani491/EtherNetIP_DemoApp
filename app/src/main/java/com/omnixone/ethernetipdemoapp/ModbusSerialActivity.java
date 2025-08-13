package com.omnixone.ethernetipdemoapp;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.omnixone.modbuslibrary.android.AndroidUsbSerialPortIo;
import com.omnixone.modbuslibrary.android.UsbSerialPortOpener;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.omnixone.modbuslibrary.io.ModbusRTUTransport;
import com.omnixone.modbuslibrary.facade.ModbusSerialMaster;
import com.omnixone.modbuslibrary.msg.ModbusResponse;
import com.omnixone.modbuslibrary.util.ModbusUtil;
import com.omnixone.modbuslibrary.util.SerialParameters;
import com.omnixone.modbuslibrary.net.AbstractSerialConnection;
import com.omnixone.modbuslibrary.android.AndroidUsbSerialConnection;

import com.omnixone.modbuslibrary.io.ModbusSerialTransaction;
import com.omnixone.modbuslibrary.io.ModbusSerialTransport;
import com.omnixone.modbuslibrary.msg.ReadMultipleRegistersRequest;
import com.omnixone.modbuslibrary.msg.ReadMultipleRegistersResponse;

import com.omnixone.modbuslibrary.io.ModbusRTUTransport;
import com.omnixone.modbuslibrary.util.SerialParameters;
import com.omnixone.modbuslibrary.android.AndroidUsbSerialConnection;
import com.omnixone.modbuslibrary.slave.ModbusSlaveFactory;
import com.omnixone.modbuslibrary.slave.ModbusSlave;
import com.omnixone.modbuslibrary.net.AbstractSerialConnection;

import com.omnixone.modbuslibrary.procimg.SimpleProcessImage;
import com.omnixone.modbuslibrary.procimg.SimpleRegister;

import com.omnixone.modbuslibrary.io.AbstractSerialTransportListener;
import com.omnixone.modbuslibrary.msg.ModbusMessage;
import com.omnixone.modbuslibrary.msg.ModbusRequest;



public class ModbusSerialActivity extends AppCompatActivity {

    private TextView tvStatus, tvLog, tvInputAssemblySensorData, tvOutputData, tvStatusData;

    private Button btnConnect, btnDisconnect, btnSend;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isAccelerometerRunning = false;

    private AndroidUsbSerialPortIo io;          // from your library
    private ExecutorService executor;
    private volatile boolean reading = false;

    private ModbusSerialMaster mbMaster;

    private ModbusSlave slave;
    private AndroidUsbSerialConnection connRef;

    SimpleRegister regX_hi;
    SimpleRegister regX_lo;
    SimpleRegister regY_hi;
    SimpleRegister regY_lo;
    SimpleRegister regZ_hi;
    SimpleRegister regZ_lo;

    // at class level
    private SimpleProcessImage spi;
    private SimpleRegister reg1, reg2, reg3;





    private void log(String msg) {
        runOnUiThread(() -> {
            tvLog.append(msg + "\n");
            int scrollAmount = tvLog.getLayout() == null ? 0 :
                    tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
            if (scrollAmount > 0) tvLog.scrollTo(0, scrollAmount);
        });
        Log.i("SerialTest", msg);
    }

    private void setStatus(String s) {
        runOnUiThread(() -> tvStatus.setText("Status: " + s));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modbus_serial);

        tvStatus = findViewById(R.id.tvStatus);
        tvInputAssemblySensorData = findViewById(R.id.tvInputAssemblySensorData);
        tvOutputData = findViewById(R.id.tvOutputData);
        tvStatusData = findViewById(R.id.tvStatusData);
        tvLog = findViewById(R.id.tvLog);

        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);


        executor = Executors.newSingleThreadExecutor();

        btnConnect.setOnClickListener(v -> connect());
        btnDisconnect.setOnClickListener(v -> disconnect());

    }


    private void connect() {

        setStatus("SEARCHING");

        new UsbSerialPortOpener(this).openFirstSupported(new UsbSerialPortOpener.Callback() {
            @Override public void onReady(AndroidUsbSerialPortIo port) {
                io = port;
                try {
                    int baud = 9600;

                    setStatus("CONNECTING...");
                    startReadLoop(baud);

                } catch (Exception e) {
                    setStatus("ERROR");
                    log("Open/configure failed: " + e.getMessage());
                }
            }
            @Override public void onError(String message) {
                setStatus("ERROR");
                log("USB error: " + message);
            }
        });
    }





    private void startReadLoop(int baud) {
        final int baudForSlave = baud;

        executor.execute(() -> {
            try {
                // 1) Transport + logs
                ModbusRTUTransport transport = new ModbusRTUTransport();
                transport.setTimeout(2000);
                transport.addListener(new AbstractSerialTransportListener() {
                    @Override public void afterRequestRead(com.omnixone.modbuslibrary.net.AbstractSerialConnection cp, ModbusRequest req) {
                        if (req == null) return;
                        byte[] data = req.getMessage();
                        byte[] pdu  = new byte[2 + (data == null ? 0 : data.length)];
                        pdu[0] = (byte) req.getUnitID();
                        pdu[1] = (byte) req.getFunctionCode();
                        if (data != null) System.arraycopy(data, 0, pdu, 2, data.length);
                        int[] crc = ModbusUtil.calculateCRC(pdu, 0, pdu.length);
                        byte[] frame = Arrays.copyOf(pdu, pdu.length + 2);
                        frame[frame.length - 2] = (byte) crc[0];
                        frame[frame.length - 1] = (byte) crc[1];
                        log("REQ " + ModbusUtil.toHex(frame, 0, frame.length));
                    }
                    @Override public void afterMessageWrite(com.omnixone.modbuslibrary.net.AbstractSerialConnection cp, ModbusMessage msg) {
                        if (msg == null) return;
                        byte[] data = msg.getMessage();
                        byte[] pdu;
                        if (msg instanceof ModbusResponse) {
                            ModbusResponse r = (ModbusResponse) msg;
                            pdu = new byte[2 + (data == null ? 0 : data.length)];
                            pdu[0] = (byte) r.getUnitID();
                            pdu[1] = (byte) r.getFunctionCode();
                            if (data != null) System.arraycopy(data, 0, pdu, 2, data.length);
                        } else {
                            String hex = msg.getHexMessage().replace(" ", "");
                            pdu = new byte[hex.length()/2];
                            for (int i = 0; i < pdu.length; i++) pdu[i] = (byte) Integer.parseInt(hex.substring(2*i, 2*i+2), 16);
                        }
                        int[] crc = ModbusUtil.calculateCRC(pdu, 0, pdu.length);
                        byte[] frame = Arrays.copyOf(pdu, pdu.length + 2);
                        frame[frame.length - 2] = (byte) crc[0];
                        frame[frame.length - 1] = (byte) crc[1];
                        log("RES " + ModbusUtil.toHex(frame, 0, frame.length));


                        if (msg instanceof ModbusResponse) {
                            ModbusResponse r = (ModbusResponse) msg;
                            int fc = r.getFunctionCode();

                            // Only react to writes
                            if (fc == 6 || fc == 16) {
                                // read back from your process image
                                // assuming you kept fields: spi, reg1, reg2, reg3
                                final int v0 = reg1.getValue();
                                final int v1 = reg2.getValue();
                                final int v2 = reg3.getValue();


                                runOnUiThread(() -> {
                                    tvOutputData.setText( v0 + ", " + v1 + ", " + v2 );
                                });
                            }
                        }


                    }
                });

                // 2) Serial parameters (pick ONE mode—start with auto direction)
                SerialParameters sp = new SerialParameters();
                sp.setPortName("usb:" + System.currentTimeMillis());  // just a unique key for mapping
                sp.setBaudRate(baudForSlave);
                sp.setDatabits(8);
                sp.setStopbits(AbstractSerialConnection.ONE_STOP_BIT);
                sp.setParity(AbstractSerialConnection.NO_PARITY);
                sp.setEncoding("rtu");

                sp.setRs485Mode(false);
                // 3) Bind your already-open USB port + transport
                connRef = new AndroidUsbSerialConnection(io, sp, transport);

                // 4) Create SERIAL slave bound to this connection (no SerialParameters NPE)
                slave = ModbusSlaveFactory.createAndroidSerialSlave(connRef);


                // For reading data
                reg1 = new SimpleRegister(0);
                reg2 = new SimpleRegister(0);
                reg3 = new SimpleRegister(0);

                // 5) Process image for UnitID=1
                // For sending data
                regX_hi = new SimpleRegister(0);
                regX_lo = new SimpleRegister(0);
                regY_hi = new SimpleRegister(0);
                regY_lo = new SimpleRegister(0);
                regZ_hi = new SimpleRegister(0);
                regZ_lo = new SimpleRegister(0);



                spi = new SimpleProcessImage();

                spi.addRegister(reg1);
                spi.addRegister(reg2);
                spi.addRegister(reg3);

                spi.addRegister(regX_hi);
                spi.addRegister(regX_lo);
                spi.addRegister(regY_hi);
                spi.addRegister(regY_lo);
                spi.addRegister(regZ_hi);
                spi.addRegister(regZ_lo);

                slave.addProcessImage(1, spi);

                slave.open();
                setStatus("CONNECTED");
                startAccelerometer();
                tvStatusData.setText("Serial SLAVE listening (RTU)\n — SlaveID=1, \n — Baud=" + baudForSlave + " 8N1");
                log("Serial SLAVE listening (RTU) — SlaveID=1, Baud=" + baudForSlave + " 8N1");
            } catch (Exception e) {
                log("Slave start failed: " + e.getMessage());
            }
        });
    }




    private void disconnect() {
        stopSlaveSync();

    }

    @Override
    protected void onDestroy() {
        stopSlaveSync();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void stopSlaveSync() {
        try {
            if (slave != null)
                slave.close();
        }
        catch (Exception ignored) {
            log("Exception in Closed: "+ignored);
        }
        slave = null;

        try {
            if (connRef != null && connRef.isOpen())
                connRef.close();
        } catch (Exception ignored) {
            log("Exception in Closed (connRef)");
        }
        connRef = null;

        try {
            if (io != null)
                io.close();
        } catch (Exception ignored) {
            log("Exception in Closed (connRef)");
        }
        // give kernel time to release interface 0 (FTDI is picky)
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}

        setStatus("DISCONNECTED");
        log("Closed (sync)");
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
    private final SensorEventListener sensorEventListener = new SensorEventListener() {

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
            setFloatToRegisters(x, regX_hi, regX_lo);
            setFloatToRegisters(y, regY_hi, regY_lo);
            setFloatToRegisters(z, regZ_hi, regZ_lo);

        } catch (Exception e) {
            Log.e("MODBUS", "Error updating float registers", e);
        }
    }


    private void setFloatToRegisters(float value, SimpleRegister hi, SimpleRegister lo) {
        int bits = Float.floatToIntBits(value);
        hi.setValue((bits >> 16) & 0xFFFF);
        lo.setValue(bits & 0xFFFF);
    }





}


