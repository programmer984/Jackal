package com.example.dataprovider.hw;

import android.annotation.TargetApi;
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
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.example.serviceComponents.DataConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static android.content.Context.BLUETOOTH_SERVICE;

final class CommunicationManagerBLE extends BluetoothGattCallback {

    private final Logger logger = LoggerFactory.getLogger(CommunicationManagerBLE.class);
    public static String HM_10_Service = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static String HM_10_Module = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static final UUID NOTIFY_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final String deviceName = "HWControl";
    private BluetoothLeScanner bluetoothLeScannerAbove21;
    private BluetoothAdapter btAdapter;
    private BluetoothDevice btDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService service;
    private BluetoothGattCharacteristic gattCharacterc;
    private volatile boolean socketConnected = false;
    private Context context;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DataConsumer receiveCallback;

    public CommunicationManagerBLE(Context context, DataConsumer receiveCallback) {
        this.context = context;
        this.receiveCallback = receiveCallback;
    }


    public synchronized boolean isReady() {
        return socketConnected;
    }

    public void closeSocket() {
        try {
            socketConnected = false;
            if (mBluetoothGatt != null) {
                mBluetoothGatt.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public synchronized void connect() {
        //close existing connection
        closeSocket();


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mainHandler.post(() -> {
                ScanSettings scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                BluetoothManager btManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
                btAdapter = btManager.getAdapter();

                bluetoothLeScannerAbove21 = btAdapter.getBluetoothLeScanner();
                bluetoothLeScannerAbove21.startScan(null, scanSettings, above21Scanner);
            });
        } else {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null) {
                logger.debug("Bluetooth adapter is not available.");
                return;
            }
            logger.debug("Bluetooth adapter is found.");

            if (!btAdapter.isEnabled()) {
                logger.debug("Bluetooth is disabled. Check configuration.");
                return;
            }
            logger.debug("Bluetooth is enabled.");

            btAdapter.startLeScan(below21Scanner);
        }
    }

    @TargetApi(21)
    private ScanCallback above21Scanner = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            onScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult scanResult : results) {
                if (onScanResult(scanResult)) {
                    break;
                }
            }
        }

        private boolean onScanResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (testDevice(device)) {
                bluetoothLeScannerAbove21.stopScan(new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);
                    }
                });
                connectToDevice(device);
                return true;
            }
            return false;
        }

        @Override
        public void onScanFailed(int errorCode) {
            logger.error("Scan failed with errorCode {}", errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback below21Scanner = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (testDevice(device)) {
                btAdapter.stopLeScan(this);
                connectToDevice(device);
            }
        }
    };


    private boolean testDevice(BluetoothDevice device) {
        if (btDevice == null) {
            String name = device.getName();
            logger.debug("Device: " + name + " (" + device.getAddress() + ")");
            if (deviceName.equals(name)) {
                btDevice = device;
                return true;
            }
        }
        return false;
    }

    private void connectToDevice(BluetoothDevice btDevice) {
        try {
            mBluetoothGatt = btDevice.connectGatt(context, true, this);
        } catch (Exception e) {
            logger.debug("Error creating socket");
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            mBluetoothGatt.discoverServices();
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            service = gatt.getService(UUID.fromString(HM_10_Service));
            if (service != null) {
                gattCharacterc = service.getCharacteristic(UUID.fromString(HM_10_Module));
                gatt.setCharacteristicNotification(gattCharacterc, true);
                BluetoothGattDescriptor descriptor = gattCharacterc.getDescriptor(NOTIFY_DESCRIPTOR);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
                synchronized (this) {
                    socketConnected = true;
                }
                gatt.readCharacteristic(gattCharacterc);
            }
        } else {
            logger.debug("onServicesDiscovered received: " + status);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            receiveCallback.accept(data, 0, data.length);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

    }

    public void sendData(byte[] data, int offset, int length) {
        byte[] output = length == data.length ? data :
                Arrays.copyOfRange(data, offset, offset + length + 1);
        gattCharacterc.setValue(output);
        mBluetoothGatt.writeCharacteristic(gattCharacterc);
        mBluetoothGatt.setCharacteristicNotification(gattCharacterc, true);
    }

}
