
package com.rio4.obd;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.MediaMetadata;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_BT = 100;
    private static final int RED = Color.rgb(255, 24, 42);
    private static final int CYAN = Color.rgb(0, 220, 245);
    private static final int WHITE = Color.WHITE;
    private static final int GREY = Color.rgb(185, 185, 195);

    private final Handler handler = new Handler();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObdManager obd = new ObdManager();

    private SharedPreferences prefs;

    private TextView clock, date, coolant, rpm, voltage, obdStatus, track;
    private ImageView carImage;
    private MediaController activeMediaController;

    private volatile boolean connected = false;
    private volatile boolean active = true;
    private volatile int connectionGeneration = 0;

    private final Runnable clockTask = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();

            if (clock != null) {
                clock.setText(new SimpleDateFormat(
                        "HH:mm", Locale.getDefault()).format(now));
            }

            if (date != null) {
                String value = new SimpleDateFormat(
                        "EEEE, d 'de' MMMM",
                        new Locale("es", "CL")).format(now);

                date.setText(value.substring(0, 1).toUpperCase(
                        new Locale("es", "CL")) + value.substring(1));
            }

            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable mediaTask = new Runnable() {
        @Override
        public void run() {
            updateMediaInfo();
            handler.postDelayed(this, 1500);
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        prefs = getSharedPreferences("dock", MODE_PRIVATE);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        buildScreen();
        handler.post(clockTask);
        handler.post(mediaTask);

        String savedAddress = prefs.getString("obd_address", null);

        if (savedAddress != null && hasBluetoothPermission()) {
            connectAddress(savedAddress);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMediaInfo();
    }

    @Override
    public void onDestroy() {
        active = false;
        connected = false;
        connectionGeneration++;

        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        obd.disconnect();

        super.onDestroy();
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < 31 ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private GradientDrawable panelBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(5, 5, 8));
        d.setCornerRadius(dp(18));
        d.setStroke(dp(2), RED);
        return d;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);

        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(5), dp(3), dp(5), dp(3));
        l.setBackground(panelBg());
        return l;
    }

    private LinearLayout.LayoutParams weighted(int weight) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(0, -1, weight);

        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        return p;
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5), dp(4), dp(5), dp(4));
        root.setBackgroundColor(Color.BLACK);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        buildCar(content);
        buildLiveData(content);
        buildMedia(content);
        buildDock(root);

        setContentView(root);
    }

    private void buildCar(LinearLayout parent) {
        LinearLayout box = card();

        carImage = new ImageView(this);
        carImage.setImageResource(R.drawable.rio_car_lights_on);
        carImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

        box.addView(carImage, new LinearLayout.LayoutParams(-1, -1));
        parent.addView(box, weighted(46));
    }

    private void buildLiveData(LinearLayout parent) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        parent.addView(col, weighted(26));

        LinearLayout time = card();
        time.addView(label("▦", 22, CYAN, true));

        clock = label("--:--", 34, WHITE, true);
        time.addView(clock);

        date = label("Cargando fecha", 12, WHITE, false);
        time.addView(date);

        col.addView(time, new LinearLayout.LayoutParams(-1, 0, 1));

        coolant = addMetric(col, "🌡  Temp. Motor", "— °C", 30);
        rpm = addMetric(col, "◉  RPM", "—", 32);
        voltage = addMetric(col, "▣  Voltaje", "—.- V", 29);
    }

    private TextView addMetric(
            LinearLayout parent,
            String heading,
            String initial,
            int size) {

        LinearLayout c = card();
        c.addView(label(heading, 15, WHITE, true));

        TextView value = label(initial, size, WHITE, true);
        c.addView(value);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, 0, 1);

        p.setMargins(0, dp(4), 0, dp(4));
        parent.addView(c, p);

        return value;
    }

    private void buildMedia(LinearLayout parent) {
        LinearLayout p = card();
        parent.addView(p, weighted(28));

        p.addView(label("ᛒ  Bluetooth", 17, WHITE, true));
        p.addView(label("♫", 86, RED, true));

        track = label("Reproductor multimedia", 17, WHITE, true);
        track.setOnClickListener(v -> {
            if (!hasNotificationAccess()) {
                startActivity(new Intent(
                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } else {
                updateMediaInfo();
            }
        });
        p.addView(track);

        View line = new View(this);
        line.setBackgroundColor(RED);

        LinearLayout.LayoutParams lineP =
                new LinearLayout.LayoutParams(-1, dp(2));

        lineP.setMargins(dp(12), dp(12), dp(12), dp(12));
        p.addView(line, lineP);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);

        controls.addView(mediaButton("|◀", KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        controls.addView(mediaButton("Ⅱ", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        controls.addView(mediaButton("▶|", KeyEvent.KEYCODE_MEDIA_NEXT));

        p.addView(controls);

        obdStatus = label("OBD DESCONECTADO", 11, GREY, true);
        p.addView(obdStatus);

        TextView connect = label("CONECTAR OBD2", 13, WHITE, true);
        connect.setPadding(dp(12), dp(8), dp(12), dp(8));
        connect.setBackground(panelBg());
        connect.setOnClickListener(v -> requestBluetoothPermission());

        p.addView(connect);
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        ComponentName component =
                new ComponentName(this, RioNotificationListener.class);

        return enabled != null &&
                enabled.contains(component.flattenToString());
    }

    private void updateMediaInfo() {
        if (track == null) return;

        if (!hasNotificationAccess()) {
            activeMediaController = null;
            track.setText("ACTIVA ACCESO MULTIMEDIA");
            return;
        }

        try {
            MediaSessionManager manager =
                    (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);

            if (manager == null) return;

            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(this, RioNotificationListener.class));

            MediaController chosen = null;
            MediaController fallback = null;

            for (MediaController controller : sessions) {
                MediaMetadata metadata = controller.getMetadata();

                if (metadata == null) continue;
                if (fallback == null) fallback = controller;

                PlaybackState state = controller.getPlaybackState();

                if (state != null &&
                        state.getState() == PlaybackState.STATE_PLAYING) {
                    chosen = controller;
                    break;
                }
            }

            if (chosen == null) chosen = fallback;
            activeMediaController = chosen;

            if (chosen == null || chosen.getMetadata() == null) {
                track.setText("Sin reproducción multimedia");
                return;
            }

            MediaMetadata metadata = chosen.getMetadata();

            String title = metadata.getString(
                    MediaMetadata.METADATA_KEY_TITLE);

            String artist = metadata.getString(
                    MediaMetadata.METADATA_KEY_ARTIST);

            if (artist == null || artist.trim().isEmpty()) {
                artist = metadata.getString(
                        MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }

            if (title == null || title.trim().isEmpty()) {
                title = metadata.getString(
                        MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            }

            if (title == null || title.trim().isEmpty()) {
                title = "Reproduciendo";
            }

            track.setText(artist == null || artist.trim().isEmpty()
                    ? title : title + " — " + artist);

        } catch (SecurityException e) {
            activeMediaController = null;
            track.setText("ACTIVA ACCESO MULTIMEDIA");

        } catch (Exception e) {
            activeMediaController = null;
            track.setText("Reproductor multimedia");
        }
    }

    private TextView mediaButton(String text, int key) {
        TextView b = label(text, 23, WHITE, true);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));

        b.setOnClickListener(v -> {
            MediaController controller = activeMediaController;

            if (controller != null) {
                try {
                    MediaController.TransportControls controls =
                            controller.getTransportControls();

                    if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                        controls.skipToPrevious();
                        return;
                    }

                    if (key == KeyEvent.KEYCODE_MEDIA_NEXT) {
                        controls.skipToNext();
                        return;
                    }

                    if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        PlaybackState state = controller.getPlaybackState();

                        if (state != null &&
                                state.getState() == PlaybackState.STATE_PLAYING) {
                            controls.pause();
                        } else {
                            controls.play();
                        }
                        return;
                    }

                } catch (Exception ignored) {
                }
            }

            AudioManager a =
                    (AudioManager) getSystemService(AUDIO_SERVICE);

            if (a != null) {
                a.dispatchMediaKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, key));

                a.dispatchMediaKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_UP, key));
            }
        });

        return b;
    }

    private void buildDock(LinearLayout root) {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);

        String[] defaults = {
                "Apps", "Bluetooth", "Radio",
                "YouTube", "Teléfono", "Ajustes"
        };

        String[] icons = {"▦", "ᛒ", "▣", "▶", "☎", "⚙"};

        for (int i = 0; i < defaults.length; i++) {
            final int slot = i;
            String title = prefs.getString("label" + i, defaults[i]);

            LinearLayout tile = card();
            tile.addView(label(icons[i], 26, WHITE, true));

            TextView titleView = label(title, 13, WHITE, true);
            tile.addView(titleView);

            tile.setOnClickListener(
                    v -> launchDock(slot, defaults[slot]));

            tile.setOnLongClickListener(v -> {
                chooseDockApp(slot, defaults[slot], titleView);
                return true;
            });

            LinearLayout.LayoutParams bp =
                    new LinearLayout.LayoutParams(0, dp(86), 1);

            bp.setMargins(dp(3), dp(4), dp(3), dp(2));
            dock.addView(tile, bp);
        }

        root.addView(dock);
    }

    private void launchDock(int slot, String defaultName) {
        String pkg = prefs.getString("pkg" + slot, null);

        if (pkg != null) {
            openPackage(pkg);
            return;
        }

        try {
            switch (defaultName) {
                case "Apps":
                    showApps();
                    break;

                case "Bluetooth":
                    requestBluetoothPermission();
                    break;

                case "Radio":
                    chooseDockApp(slot, defaultName, null);
                    break;

                case "YouTube":
                    openPackage("com.google.android.youtube");
                    break;

                case "Teléfono":
                    startActivity(new Intent(Intent.ACTION_DIAL));
                    break;

                case "Ajustes":
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                    break;
            }
        } catch (Exception e) {
            toast("No se pudo abrir " + defaultName);
        }
    }

    private void chooseDockApp(
            int slot,
            String defaultName,
            TextView titleView) {

        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> found =
                getPackageManager().queryIntentActivities(query, 0);

        final ArrayList<String> packages = new ArrayList<>();
        final ArrayList<String> labels = new ArrayList<>();

        for (ResolveInfo r : found) {
            String pkg = r.activityInfo.packageName;

            if (!packages.contains(pkg)) {
                packages.add(pkg);
                labels.add(
                        r.loadLabel(getPackageManager()).toString());
            }
        }

        String[] items = labels.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Asignar acceso · " + defaultName)
                .setItems(items, (d, which) -> {
                    prefs.edit()
                            .putString("pkg" + slot, packages.get(which))
                            .putString("label" + slot, labels.get(which))
                            .apply();

                    if (titleView != null) {
                        titleView.setText(labels.get(which));
                    }

                    toast("Acceso actualizado");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showApps() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> found =
                getPackageManager().queryIntentActivities(query, 0);

        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> packages = new ArrayList<>();

        for (ResolveInfo r : found) {
            String pkg = r.activityInfo.packageName;

            if (!packages.contains(pkg)) {
                packages.add(pkg);
                labels.add(
                        r.loadLabel(getPackageManager()).toString());
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Aplicaciones")
                .setItems(labels.toArray(new String[0]),
                        (d, w) -> openPackage(packages.get(w)))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);

        if (i != null) {
            startActivity(i);
        } else {
            toast("La aplicación ya no está instalada");
        }
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_BT);

            return;
        }

        showPairedAdapters();
    }

    @Override
    public void onRequestPermissionsResult(
            int request,
            String[] permissions,
            int[] results) {

        super.onRequestPermissionsResult(request, permissions, results);

        if (request == REQ_BT &&
                results.length > 0 &&
                results[0] == PackageManager.PERMISSION_GRANTED) {

            showPairedAdapters();

        } else if (request == REQ_BT) {
            toast("Autoriza Bluetooth para conectar el ELM327");
        }
    }

    private void showPairedAdapters() {
        try {
            BluetoothAdapter adapter =
                    BluetoothAdapter.getDefaultAdapter();

            if (adapter == null) {
                toast("Este equipo no tiene Bluetooth clásico");
                return;
            }

            if (!adapter.isEnabled()) {
                startActivity(
                        new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));

                toast("Activa y empareja el ELM327 primero");
                return;
            }

            Set<BluetoothDevice> paired = adapter.getBondedDevices();

            final ArrayList<BluetoothDevice> devices =
                    new ArrayList<>(paired);

            if (devices.isEmpty()) {
                toast("Empareja primero el ELM327 desde Ajustes Bluetooth");

                startActivity(
                        new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));

                return;
            }

            String[] names = new String[devices.size()];

            for (int i = 0; i < devices.size(); i++) {
                BluetoothDevice d = devices.get(i);
                String n = d.getName();

                names[i] =
                        (n == null ? "Dispositivo Bluetooth" : n)
                                + "\n" + d.getAddress();
            }

            new AlertDialog.Builder(this)
                    .setTitle("Seleccionar adaptador OBD2")
                    .setItems(names,
                            (d, w) -> connect(devices.get(w)))
                    .setNegativeButton("Cancelar", null)
                    .show();

        } catch (SecurityException e) {
            toast("Permiso Bluetooth denegado");
        }
    }

    private void connect(BluetoothDevice device) {
        if (device == null) return;

        String address;

        try {
            address = device.getAddress();
        } catch (SecurityException e) {
            toast("Permiso Bluetooth denegado");
            return;
        }

        prefs.edit().putString("obd_address", address).apply();
        connectAddress(address);
    }

    private void connectAddress(String address) {
        if (!active || address == null) return;

        final int generation = ++connectionGeneration;

        connected = false;
        obd.disconnect();

        setStatus("CONECTANDO…", GREY);

        executor.execute(() -> {
            long nextCoolant = 0;
            long nextVoltage = 0;

            while (active &&
                    generation == connectionGeneration &&
                    !Thread.currentThread().isInterrupted()) {

                try {
                    if (!obd.isConnected()) {
                        setStatus("CONECTANDO AL V GATE…", GREY);

                        BluetoothAdapter adapter =
                                BluetoothAdapter.getDefaultAdapter();

                        if (adapter == null || !adapter.isEnabled()) {
                            throw new Exception("Bluetooth no disponible");
                        }

                        BluetoothDevice device =
                                adapter.getRemoteDevice(address);

                        BluetoothObd bt = new BluetoothObd();
                        bt.connect(device);

                        obd.setConnection(bt);

                        Thread.sleep(700);

                        obd.initialize();

                        connected = true;
                        nextCoolant = 0;
                        nextVoltage = 0;

                        setStatus("OBD CONECTADO", Color.GREEN);
                    }

                    // RPM tiene prioridad: se consulta en cada vuelta.
                    double r = obd.getRPM();

                    long now = System.currentTimeMillis();

                    final double[] coolantValue = {Double.NaN};
                    final double[] voltageValue = {Double.NaN};

                    // Temperatura: cada 2 segundos.
                    if (now >= nextCoolant) {
                        coolantValue[0] =
                                obd.getCoolantTemperature();

                        nextCoolant = now + 2000;
                    }

                    // Voltaje: cada 5 segundos.
                    if (now >= nextVoltage) {
                        voltageValue[0] =
                                obd.getControlModuleVoltage();

                        nextVoltage = now + 5000;
                    }

                    final double finalRpm = r;
                    final double finalCoolant = coolantValue[0];
                    final double finalVoltage = voltageValue[0];

                    runOnUiThread(() -> {
                        if (!active || generation != connectionGeneration) {
                            return;
                        }

                        if (rpm != null) {
                            rpm.setText(String.format(
                                    Locale.US, "%.0f", finalRpm));
                        }

                        if (!Double.isNaN(finalCoolant) &&
                                coolant != null) {

                            coolant.setText(String.format(
                                    Locale.US, "%.0f °C", finalCoolant));
                        }

                        if (!Double.isNaN(finalVoltage) &&
                                voltage != null) {

                            voltage.setText(String.format(
                                    Locale.US, "%.1f V", finalVoltage));
                        }
                    });

                    // Pausa corta para no saturar el adaptador.
                    Thread.sleep(80);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;

                } catch (Exception e) {
                    connected = false;
                    obd.disconnect();

                    setStatus("OBD DESCONECTADO · REINTENTANDO", GREY);

                    runOnUiThread(() -> {
                        if (generation == connectionGeneration) {
                            if (rpm != null) rpm.setText("—");
                            if (coolant != null) coolant.setText("— °C");
                            if (voltage != null) voltage.setText("—.- V");
                        }
                    });

                    if (active &&
                            generation == connectionGeneration) {

                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (generation == connectionGeneration) {
                connected = false;
                obd.disconnect();
            }
        });
    }

    private void setStatus(String s, int color) {
        runOnUiThread(() -> {
            if (active && obdStatus != null) {
                obdStatus.setText(s);
                obdStatus.setTextColor(color);
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
