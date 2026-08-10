package com.example.phonecontroller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int DISCOVERY_PORT = 5556;
    private static final String BEACON_PREFIX = "PHONECONTROLLER;";

    private TextView statusText;
    private ListView deviceList;
    private EditText ipInput;
    private EditText portInput;
    private Button connectButton;
    private ArrayAdapter<String> listAdapter;
    private final ArrayList<String> devices = new ArrayList<>();
    private final Set<String> deviceSet = new HashSet<>();
    private final Map<String, Long> lastSeen = new HashMap<>();
    private static final long STALE_MS = 4000;

    private DatagramSocket udpSocket;
    private volatile boolean scanning = true;
    private WifiManager.MulticastLock multicastLock;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        deviceList = findViewById(R.id.device_list);
        ipInput = findViewById(R.id.ip_input);
        portInput = findViewById(R.id.port_input);
        connectButton = findViewById(R.id.connect_button);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devices);
        deviceList.setAdapter(listAdapter);

        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            String item = devices.get(position);
            String[] parts = item.split("  ");
            String host = parts[0];
            int port = 5555;
            try {
                port = Integer.parseInt(parts[1].replaceAll("\\D", ""));
            } catch (Exception ignored) {
            }
            openController(host, port);
        });

        connectButton.setOnClickListener(v -> {
            String host = ipInput.getText().toString().trim();
            if (host.isEmpty()) {
                Toast.makeText(this, "Enter your PC's IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            int port = 5555;
            String p = portInput.getText().toString().trim();
            if (!p.isEmpty()) {
                try {
                    port = Integer.parseInt(p);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            openController(host, port);
        });

        startDiscovery();
    }

    private void openController(String host, int port) {
        Intent i = new Intent(this, ControllerActivity.class);
        i.putExtra("host", host);
        i.putExtra("port", port);
        startActivity(i);
    }

    private void startDiscovery() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("phonecontroller");
            multicastLock.acquire();
        }
        statusText.setText("Scanning for nearby controllers...");
        Thread t = new Thread(this::discoverLoop, "discovery-thread");
        t.setDaemon(true);
        t.start();
    }

    private void discoverLoop() {
        try {
            udpSocket = new DatagramSocket(DISCOVERY_PORT);
            udpSocket.setSoTimeout(1000);
        } catch (Exception e) {
            handler.post(() -> statusText.setText("Discovery unavailable: " + e.getMessage()));
            return;
        }
        byte[] buf = new byte[256];
        while (scanning) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                udpSocket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                if (msg.startsWith(BEACON_PREFIX)) {
                    String host = packet.getAddress().getHostAddress();
                    String portStr = msg.substring(BEACON_PREFIX.length()).trim();
                    int port = 5555;
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException ignored) {
                    }
                    addDevice(host, port);
                }
            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception e) {
                break;
            }
            removeStale();
        }
        if (udpSocket != null) {
            try {
                udpSocket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void addDevice(final String host, final int port) {
        final String key = host + ":" + port;
        lastSeen.put(key, System.currentTimeMillis());
        handler.post(() -> {
            if (deviceSet.add(key)) {
                devices.add(host + "  (port " + port + ")");
                listAdapter.notifyDataSetChanged();
                statusText.setText("Found " + devices.size() + " controller" + (devices.size() == 1 ? "" : "s"));
            }
        });
    }

    private void removeStale() {
        long now = System.currentTimeMillis();
        final ArrayList<String> stale = new ArrayList<>();
        for (Map.Entry<String, Long> e : lastSeen.entrySet()) {
            if (now - e.getValue() > STALE_MS) stale.add(e.getKey());
        }
        if (stale.isEmpty()) return;
        for (String key : stale) lastSeen.remove(key);
        handler.post(() -> {
            for (String key : stale) {
                if (deviceSet.remove(key)) {
                    String host = key.substring(0, key.lastIndexOf(':'));
                    String port = key.substring(key.lastIndexOf(':') + 1);
                    devices.remove(host + "  (port " + port + ")");
                }
            }
            listAdapter.notifyDataSetChanged();
            statusText.setText(devices.isEmpty() ? "Scanning for nearby controllers..." : "Found " + devices.size() + " controller" + (devices.size() == 1 ? "" : "s"));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanning = false;
        if (udpSocket != null) {
            try {
                udpSocket.close();
            } catch (Exception ignored) {
            }
        }
        if (multicastLock != null) {
            try {
                multicastLock.release();
            } catch (Exception ignored) {
            }
        }
    }
}
