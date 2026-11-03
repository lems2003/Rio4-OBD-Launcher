package com.rio4.obd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;

import java.io.IOException;
import java.util.UUID;

public class BleObd {

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;

    private volatile String response = "";
    private volatile boolean responseReady = false;

    public void connect(Context context, BluetoothDevice device) {
        gatt = device.connectGatt(
            context,
            false,
            new BluetoothGattCallback() {

                @Override
                public void onConnectionStateChange(
                    BluetoothGatt g,
                    int status,
                    int newState
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS &&
                        newState == BluetoothProfile.STATE_CONNECTED) {
                        g.discoverServices();
                    } else if (
                        newState == BluetoothProfile.STATE_DISCONNECTED
                    ) {
                        close();
                    }
                }

                @Override
                public void onServicesDiscovered(
                    BluetoothGatt g,
                    int status
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) return;

                    for (BluetoothGattService service :
                            g.getServices()) {

                        for (BluetoothGattCharacteristic c :
                                service.getCharacteristics()) {

                            int properties = c.getProperties();

                            if ((properties &
                                BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (properties &
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                tx = c;
                            }

                            if ((properties &
                                BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                                (properties &
                                BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                                rx = c;
                            }
                        }
                    }

                    if (rx != null) {
                        g.setCharacteristicNotification(rx, true);
                    }
                }

                @Override
                public void onCharacteristicChanged(
                    BluetoothGatt g,
                    BluetoothGattCharacteristic c,
                    byte[] value
                ) {
                    if (c.equals(rx)) {
                        response += new String(value);
                        if (response.contains(">")) {
                            responseReady = true;
                        }
                    }
                }
            }
        );
    }

    public boolean isConnected() {
        return gatt != null;
    }

    public synchronized void write(String command)
            throws IOException {

        if (gatt == null || tx == null) {
            throw new IOException("BLE no está listo");
        }

        byte[] data = (command + "\r").getBytes();

        if (Build.VERSION.SDK_INT >= 33) {
            int result = gatt.writeCharacteristic(
                tx,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            );

            if (result != BluetoothStatusCodes.SUCCESS) {
                throw new IOException("No se pudo enviar el comando");
            }
        } else {
            tx.setValue(data);
            tx.setWriteType(
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            );

            if (!gatt.writeCharacteristic(tx)) {
                throw new IOException("Error de escritura BLE");
            }
        }
    }

    public synchronized String readResponse() throws IOException {
        long start = System.currentTimeMillis();

        while (!responseReady &&
               System.currentTimeMillis() - start < 5000) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Lectura interrumpida");
            }
        }

        if (!responseReady) {
            throw new IOException("Tiempo de espera BLE agotado");
        }

        String result = response;
        response = "";
        responseReady = false;

        return result.replace(">", "").trim();
    }

    public void close() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }

        tx = null;
        rx = null;
    }
}
