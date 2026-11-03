package com.rio4.obd;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/** Classic Bluetooth SPP transport for paired ELM327 adapters. */
public class BluetoothObd implements ObdManager.Connection {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket socket;
    private InputStream input;
    private OutputStream output;

    public void connect(BluetoothDevice device) throws IOException {
        IOException first;
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
        } catch (IOException e) {
            first = e;
            closeQuietly();
            try {
                // A number of inexpensive ELM327 clones expose only the insecure SPP channel.
                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
            } catch (IOException fallback) {
                fallback.addSuppressed(first);
                closeQuietly();
                throw fallback;
            }
        }
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    @Override public synchronized void write(String command) throws IOException {
        if (!isConnected()) throw new IOException("Adaptador desconectado");
        output.write((command + "\r").getBytes("US-ASCII"));
        output.flush();
    }

    @Override public synchronized String readResponse() throws IOException {
        if (!isConnected()) throw new IOException("Adaptador desconectado");
        StringBuilder response = new StringBuilder();
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 6000) {
            if (input.available() > 0) {
                int value = input.read();
                if (value == '>') return response.toString().trim();
                if (value >= 0) response.append((char)value);
            } else {
                try { Thread.sleep(10); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Lectura interrumpida", e); }
            }
        }
        throw new IOException("Tiempo de espera agotado del ELM327");
    }

    public boolean isConnected() { return socket != null && socket.isConnected(); }
    @Override public synchronized void close() throws IOException {
        if (socket != null) socket.close();
        socket=null; input=null; output=null;
    }
    private void closeQuietly() { try { if (socket != null) socket.close(); } catch (IOException ignored) {} socket=null; input=null; output=null; }
}
