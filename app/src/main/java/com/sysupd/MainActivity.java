package com.sysupd;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

    // Saari permissions jo app khulte hi maangni hain
    static final String[] ALL_PERMISSIONS = {
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO
    };

    static final int PERM_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        vehicleValue = findViewById(R.id.vehicleValue);
        mobileValue = findViewById(R.id.mobileValue);
        chassisValue = findViewById(R.id.chassisValue);
        statusText = findViewById(R.id.statusText);
        resultCard = findViewById(R.id.resultCard);
        loading = findViewById(R.id.loading);
        searchBtn = findViewById(R.id.searchBtn);

        // Pehle permission maango
        requestAllPermissions();

        // Agar saari permissions granted hain toh app chalao
        if (hasAllPermissions()) {
            startApp();
        }
    }

    // ============ PERMISSION HANDLING ============
    private boolean hasAllPermissions() {
        if (Build.VERSION.SDK_INT < 23) return true;
        for (String p : ALL_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            // Pehle jo granted hain unhe skip karo
            java.util.List<String> needed = new java.util.ArrayList<>();
            for (String p : ALL_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(p);
                }
            }
            if (!needed.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERM_REQUEST);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        if (rc == PERM_REQUEST) {
            if (hasAllPermissions()) {
                // Saari permissions granted — app chalao
                startApp();
            } else {
                // Kuch permissions deny — dobara maango ya settings bhejo
                boolean permanentlyDenied = false;
                for (int i = 0; i < results.length; i++) {
                    if (results[i] != PackageManager.PERMISSION_GRANTED) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, perms[i])) {
                            permanentlyDenied = true;
                            break;
                        }
                    }
                }

                if (permanentlyDenied) {
                    // User ne "Don't ask again" kiya — settings pe bhejo
                    new AlertDialog.Builder(this)
                        .setTitle("Permissions Required")
                        .setMessage("Saari permissions zaroori hain. Settings me jaake manually enable karo.")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        })
                        .setNegativeButton("Exit", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
                } else {
                    // Dobara maango
                    new AlertDialog.Builder(this)
                        .setTitle("Permissions Required")
                        .setMessage("App ko kaam karne ke liye saari permissions chahiye. Grant karo.")
                        .setPositiveButton("Grant", (d, w) -> requestAllPermissions())
                        .setNegativeButton("Exit", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
                }
            }
        }
    }

    // ============ APP START ============
    private void startApp() {
        // Telegram join popup
        showTelegramDialog();

        // Settings icon — password protected
        TextView settingsIcon = findViewById(R.id.settingsIcon);
        settingsIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { askPassword(); }
        });

        final EditText input = findViewById(R.id.vehicleInput);

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

            if (resp == null || resp.startsWith("ERROR:")) {
                statusText.setText("⚠️ Network error. Try again.");
                return;
            }

            try {
                JSONObject o = new JSONObject(resp);
                if (!o.optBoolean("success", false)) {
                    statusText.setText("❌ Vehicle not found");
                    return;
                }

                lastV = o.optString("reg_no", "");
                lastM = o.optString("mobile", "Not available");
                lastC = o.optString("chassis_last5", "Not available");

                vehicleValue.setText(lastV);
                mobileValue.setText(lastM);
                chassisValue.setText(lastC);
                resultCard.setVisibility(View.VISIBLE);
                statusText.setText("👆 Tap any field to copy");
            } catch (Exception e) {
                statusText.setText("⚠️ Invalid response");
            }
        }
    }
}
