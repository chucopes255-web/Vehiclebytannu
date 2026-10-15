package com.sysupd;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    TextView vehicleValue, mobileValue, chassisValue, statusText;
    LinearLayout resultCard;
    ProgressBar loading;
    Button searchBtn;
    String lastV = "", lastM = "", lastC = "";
    boolean isFetching = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        final EditText input = findViewById(R.id.vehicleInput);
        searchBtn = findViewById(R.id.searchBtn);
        TextView settingsIcon = findViewById(R.id.settingsIcon);
        resultCard = findViewById(R.id.resultCard);
        loading = findViewById(R.id.loading);
        vehicleValue = findViewById(R.id.vehicleValue);
        mobileValue = findViewById(R.id.mobileValue);
        chassisValue = findViewById(R.id.chassisValue);
        statusText = findViewById(R.id.statusText);

        showTelegramDialog();

        settingsIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { askPassword(); }
        });

        searchBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isFetching) return;
                String vnum = input.getText().toString().trim().toUpperCase();
                if (vnum.length() < 6) {
                    Toast.makeText(MainActivity.this,
                        "Enter valid vehicle number", Toast.LENGTH_SHORT).show();
                    return;
                }
                new FetchTask().execute(vnum);
            }
        });

        vehicleValue.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { copy("Vehicle", lastV); }
        });
        mobileValue.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { copy("Mobile", lastM); }
        });
        chassisValue.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { copy("Chassis", lastC); }
        });
    }

    private void showTelegramDialog() {
        new AlertDialog.Builder(this)
            .setTitle("✈️ Join Our Channel")
            .setMessage("Join @fbxshop on Telegram for updates, support and exclusive offers.")
            .setPositiveButton("Join Now", (d, w) -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://t.me/fbxshop"));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(this, "Telegram not installed",
                        Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Skip", null)
            .setCancelable(false)
            .show();
    }

    private void askPassword() {
        EditText input = new EditText(this);
        input.setHint("Enter password");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
            .setTitle("🔐 Settings")
            .setMessage("Enter password to access owner panel")
            .setView(input)
            .setPositiveButton("Unlock", (d, w) -> {
                if (input.getText().toString().equals("Tannu")) {
                    showOwnerPanel();
                } else {
                    Toast.makeText(this, "Wrong password",
                        Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showOwnerPanel() {
        new AlertDialog.Builder(this)
            .setTitle("👑 Owner Panel")
            .setMessage("Welcome, Tanvir\n\nApplication: Vehicle Info\nVersion: 1.0\nChannel: @fbxshop")
            .setPositiveButton("OK", null)
            .show();
    }

    private void copy(String label, String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, "✅ Copied " + label, Toast.LENGTH_SHORT).show();
    }

    class FetchTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            isFetching = true;
            searchBtn.setEnabled(false);
            searchBtn.setText("Searching...");
            loading.setVisibility(View.VISIBLE);
            resultCard.setVisibility(View.GONE);
            statusText.setText("");
        }

        @Override
        protected String doInBackground(String... p) {
            try {
                URL url = new URL("https://masking-pannel.vercel.app/api?veh=" + p[0]);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(20000);
                c.setReadTimeout(20000);
                c.setRequestProperty("Accept", "application/json");
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                return sb.toString();
            } catch (Exception e) {
                return "ERROR:" + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String resp) {
            isFetching = false;
            searchBtn.setEnabled(true);
            searchBtn.setText("🔍 Search Owner Details");
            loading.setVisibility(View.GONE);

            if (resp == null) {
                statusText.setText("⚠️ Network error. Try again.");
                return;
            }

            if (resp.startsWith("ERROR:")) {
                statusText.setText("⚠️ Connection failed. Check internet.");
                return;
            }

            try {
                JSONObject o = new JSONObject(resp);

                if (!o.optBoolean("success", false)) {
                    statusText.setText("❌ Vehicle not found in database");
                    return;
                }

                String regNo = o.optString("reg_no", "").trim();
                String mobile = o.optString("mobile", "").trim();
                String chassis = o.optString("chassis_last5", "").trim();

                // Agar mobile ya chassis empty hai
                if (mobile.isEmpty() && chassis.isEmpty()) {
                    statusText.setText("⚠️ Owner details not available for this vehicle");
                    return;
                }

                lastV = regNo.isEmpty() ? "—" : regNo;
                lastM = mobile.isEmpty() ? "Not available" : mobile;
                lastC = chassis.isEmpty() ? "Not available" : chassis;

                vehicleValue.setText(lastV);
                mobileValue.setText(lastM);
                chassisValue.setText(lastC);
                resultCard.setVisibility(View.VISIBLE);
                statusText.setText("👆 Tap any field to copy");

            } catch (Exception e) {
                statusText.setText("⚠️ Invalid response from server");
            }
        }
    }
}
