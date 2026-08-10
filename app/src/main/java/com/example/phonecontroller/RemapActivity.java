package com.example.phonecontroller;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class RemapActivity extends Activity {

    private static final String[] CONTROLS = {"A", "B", "X", "Y", "LB", "RB", "LT", "RT", "START", "SELECT"};
    private static final String[] TARGETS = {
            "XBOX:A", "XBOX:B", "XBOX:X", "XBOX:Y",
            "XBOX:LB", "XBOX:RB", "XBOX:LT", "XBOX:RT",
            "XBOX:START", "XBOX:BACK", "XBOX:LS", "XBOX:RS",
            "KEY:W", "KEY:A", "KEY:S", "KEY:D",
            "KEY:E", "KEY:Q", "KEY:R", "KEY:F",
            "KEY:SPACE", "KEY:SHIFT", "KEY:CTRL", "KEY:TAB", "KEY:ESC", "KEY:ENTER",
            "KEY:UP", "KEY:DOWN", "KEY:LEFT", "KEY:RIGHT",
            "MOUSE:L", "MOUSE:R", "MOUSE:M"
    };

    private final Spinner[] spinners = new Spinner[CONTROLS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remap);

        final SharedPreferences prefs = getSharedPreferences("mappings", MODE_PRIVATE);
        LinearLayout container = findViewById(R.id.container);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TARGETS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        for (int i = 0; i < CONTROLS.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(18, 10, 18, 10);

            TextView label = new TextView(this);
            label.setText(CONTROLS[i]);
            label.setTextSize(18);
            label.setTextColor(0xFFC7D5E0);
            label.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f));

            Spinner spinner = new Spinner(this);
            spinner.setAdapter(adapter);
            String current = prefs.getString(CONTROLS[i], null);
            int sel = indexOf(TARGETS, current);
            if (sel < 0) sel = indexOf(TARGETS, defaultFor(CONTROLS[i]));
            spinner.setSelection(sel);
            spinners[i] = spinner;
            row.addView(spinner, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f));

            container.addView(row);
        }

        Button save = findViewById(R.id.save_btn);
        save.setOnClickListener(v -> {
            SharedPreferences.Editor ed = prefs.edit();
            for (int i = 0; i < CONTROLS.length; i++) {
                ed.putString(CONTROLS[i], TARGETS[spinners[i].getSelectedItemPosition()]);
            }
            ed.apply();
            Toast.makeText(this, "Mapping saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private static int indexOf(String[] arr, String val) {
        if (val == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(val)) return i;
        }
        return -1;
    }

    private static String defaultFor(String control) {
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
}
