package com.rio4.obd;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

public class ObdManager {

    public interface Connection {
        void write(String command) throws IOException;
        String readResponse() throws IOException;
        void close() throws IOException;
    }

    private Connection connection;

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public boolean isConnected() {
        return connection != null;
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) {
            }
            connection = null;
        }
    }

    public String sendCommand(String command) throws IOException {
        if (connection == null) {
            throw new IOException("No hay adaptador OBD conectado");
        }

        connection.write(command);
        return connection.readResponse();
    }

    public void initialize() throws IOException {
        sendCommand("ATZ");
        sendCommand("ATE0");
        sendCommand("ATL0");
        sendCommand("ATS0");
        sendCommand("ATH0");
        sendCommand("ATSP0");
    }

    public double getRPM() throws IOException {
        String response = sendCommand("010C");
        int[] data = parsePid(response, 2);

        return ((data[0] * 256) + data[1]) / 4.0;
    }

    public double getCoolantTemperature() throws IOException {
        String response = sendCommand("0105");
        int[] data = parsePid(response, 1);

        return data[0] - 40;
    }

    public double getControlModuleVoltage() throws IOException {
        String response = sendCommand("ATRV");
        String value = response.replaceAll("[^0-9.]", "");

        if (value.isEmpty()) {
            throw new IOException("Voltaje no disponible");
        }

        return Double.parseDouble(value);
    }

    private int[] parsePid(String response, int bytes)
            throws IOException {

        String clean = response
                .toUpperCase(Locale.US)
                .replaceAll("[^0-9A-F]", "");

        int position = clean.indexOf("41");

        if (position < 0 ||
            clean.length() < position + 4 + (bytes * 2)) {
            throw new IOException("Respuesta OBD inválida: " + response);
        }

        int[] result = new int[bytes];

        for (int i = 0; i < bytes; i++) {
            result[i] = Integer.parseInt(
                    clean.substring(
                        position + 4 + (i * 2),
                        position + 6 + (i * 2)
                    ), 16);
        }

        return result;
    }
}
