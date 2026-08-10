package com.example.phonecontroller;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

public class ControllerActivity extends Activity implements SocketClient.Listener {

    private static final String[] CONTROLS = {"A", "B", "X", "Y", "LB", "RB", "LT", "RT", "START", "SELECT"};

    private SocketClient client;
    private TextView statusText;
    private float lastLx, lastLy, lastRx, lastRy;
    private final Handler heartbeat = new Handler(Looper.getMainLooper());
    private boolean active = false;

    private final Runnable beat = new Runnable() {
        @Override
        public void run() {
            if (active && client != null) {
                client.send("STICK,L," + fmt(lastLx) + "," + fmt(lastLy));
                client.send("STICK,R," + fmt(lastRx) + "," + fmt(lastRy));
                heartbeat.postDelayed(this, 100);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        String host = getIntent().getStringExtra("host");
        int port = getIntent().getIntExtra("port", 5555);
        statusText = findViewById(R.id.conn_status);

        client = new SocketClient(this);
        client.connect(host, port);

        JoystickView left = findViewById(R.id.left_stick);
        JoystickView right = findViewById(R.id.right_stick);
        left.setSide(JoystickView.SIDE_LEFT);
        right.setSide(JoystickView.SIDE_RIGHT);
        left.setListener((axis, x, y) -> {
            lastLx = x;
            lastLy = y;
            if (active && client != null) client.send("STICK,L," + fmt(x) + "," + fmt(y));
        });
        right.setListener((axis, x, y) -> {
            lastRx = x;
            lastRy = y;
            if (active && client != null) client.send("STICK,R," + fmt(x) + "," + fmt(y));
        });

        setupButton(R.id.btn_a, "A");
        setupButton(R.id.btn_b, "B");
        setupButton(R.id.btn_x, "X");
        setupButton(R.id.btn_y, "Y");
        setupButton(R.id.btn_lb, "LB");
        setupButton(R.id.btn_rb, "RB");
        setupButton(R.id.btn_lt, "LT");
        setupButton(R.id.btn_rt, "RT");
        setupButton(R.id.btn_start, "START");
        setupButton(R.id.btn_select, "SELECT");

        findViewById(R.id.disconnect_btn).setOnClickListener(v -> {
            active = false;
            heartbeat.removeCallbacksAndMessages(null);
            if (client != null) client.disconnect();
            finish();
        });

        findViewById(R.id.remap_btn).setOnClickListener(v ->
                startActivity(new Intent(this, RemapActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.3f", v);
    }

    private void setupButton(int id, final String control) {
        View v = findViewById(id);
        if (v == null) return;
        v.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    if (active && client != null) sendButton(control, true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    if (active && client != null) sendButton(control, false);
                    return true;
            }
            return false;
        });
    }

    private void sendButton(String control, boolean pressed) {
        String target = getTarget(control);
        int state = pressed ? 1 : 0;
        if (target.startsWith("XBOX:")) {
            String b = target.substring(5);
            if (b.equals("LT")) {
                client.send("TRIG,L," + state);
            } else if (b.equals("RT")) {
                client.send("TRIG,R," + state);
            } else {
                client.send("XBOX," + b + "," + state);
            }
        } else if (target.startsWith("KEY:")) {
            client.send("KEY," + target.substring(4) + "," + state);
        } else if (target.startsWith("MOUSE:")) {
            client.send("MOUSE," + target.substring(6) + "," + state);
        }
    }

    private String getTarget(String control) {
        SharedPreferences prefs = getSharedPreferences("mappings", MODE_PRIVATE);
        String saved = prefs.getString(control, null);
        if (saved != null) return saved;
        switch (control) {
            case "A": return "XBOX:A";
            case "B": return "XBOX:B";
            case "X": return "XBOX:X";
            case "Y": return "XBOX:Y";
            case "LB": return "XBOX:LB";
            case "RB": return "XBOX:RB";
            case "LT": return "XBOX:LT";
            case "RT": return "XBOX:RT";
            case "START": return "XBOX:START";
            case "SELECT": return "XBOX:BACK";
        }
        return "XBOX:A";
    }

    @Override
    public void onConnected(String host, int port) {
        runOnUiThread(() -> {
            statusText.setText("Connected to " + host);
            active = true;
            heartbeat.post(beat);
        });
    }

    @Override
    public void onDisconnected(String reason) {
        runOnUiThread(() -> {
            active = false;
            heartbeat.removeCallbacksAndMessages(null);
            statusText.setText("Disconnected: " + reason);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        active = false;
        heartbeat.removeCallbacksAndMessages(null);
        if (client != null) client.disconnect();
    }
}
