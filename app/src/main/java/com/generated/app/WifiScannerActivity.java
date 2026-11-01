package com.generated.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings; // Added import for Settings
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale; // Added import for Locale

public class WifiScannerActivity extends Activity {

    private static final String TAG = "WifiScannerActivity";
    private static final int REQ_CODE_LOCATION_PERMISSION = 1003;
    private static final int REQ_CODE_WIFI_SETTINGS = 1002;


    private WifiManager wifiManager;
    private WifiScanReceiver wifiScanReceiver;
    private List<ScanResult> wifiNetworks = new ArrayList<>();
    private ArrayAdapter<String> wifiListAdapter;
    private ListView wifiListView;
    private TextView tvScanningStatus;
    private Button btnRescan;

    private boolean isScanning = false;
    private final Handler scanHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_scanner);

        wifiListView = findViewById(R.id.wifi_list_view);
        tvScanningStatus = findViewById(R.id.tv_scanning_status);
        btnRescan = findViewById(R.id.btn_rescan);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiScanReceiver = new WifiScanReceiver();

        setupUI();
        checkLocationPermissionAndStartScan();

        btnRescan.setOnClickListener(v -> checkLocationPermissionAndStartScan());
    }

    private void setupUI() {
        findViewById(R.id.btn_back_wifi).setOnClickListener(v -> finish());

        wifiListAdapter = new ArrayAdapter<String>(this, R.layout.item_wifi_list, R.id.tv_wifi_ssid) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView ssidTextView = view.findViewById(R.id.tv_wifi_ssid);
                TextView capabilitiesTextView = view.findViewById(R.id.tv_wifi_capabilities);
                ScanResult result = wifiNetworks.get(position);

                ssidTextView.setText(result.SSID.isEmpty() ? getString(R.string.unknown_ssid) : result.SSID);
                capabilitiesTextView.setText(getString(R.string.security) + " " + result.capabilities);

                // Indicate if the network is currently connected
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null && result.BSSID.equals(wifiInfo.getBSSID())) {
                    ssidTextView.setTextColor(getResources().getColor(R.color.wifi_connected_highlight)); // Highlight connected network
                } else {
                    ssidTextView.setTextColor(getResources().getColor(R.color.text_primary));
                }

                return view;
            }

            @Override
            public int getCount() {
                return wifiNetworks.size();
            }
        };
        wifiListView.setAdapter(wifiListAdapter);

        wifiListView.setOnItemClickListener((parent, view, position, id) -> {
            ScanResult selectedNetwork = wifiNetworks.get(position);
            connectToWifiNetwork(selectedNetwork);
        });
    }

    private void checkLocationPermissionAndStartScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_CODE_LOCATION_PERMISSION);
            } else {
                startWifiScan();
            }
        } else {
            startWifiScan();
        }
    }

    private void startWifiScan() {
        if (wifiManager == null) {
            Toast.makeText(this, "שירות ה-Wi-Fi אינו זמין.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            tvScanningStatus.setText(getString(R.string.wifi_disabled_message));
            btnRescan.setText("פתח הגדרות Wi-Fi");
            btnRescan.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
            return;
        }

        if (!isScanning) {
            isScanning = true;
            tvScanningStatus.setText(getString(R.string.scanning));
            btnRescan.setEnabled(false); // Disable rescan while scanning
            wifiNetworks.clear();
            wifiListAdapter.notifyDataSetChanged();

            if (wifiManager.startScan()) {
                Log.d(TAG, "WiFi scan initiated successfully.");
                // Schedule a timeout for the scan results
                scanHandler.postDelayed(scanTimeoutRunnable, 5000); // 5 seconds timeout
            } else {
                Log.e(TAG, "Failed to initiate WiFi scan.");
                tvScanningStatus.setText("נכשל בסריקת Wi-Fi. נסה שוב.");
                isScanning = false;
                btnRescan.setEnabled(true);
                Toast.makeText(this, "כשל ביוזמת סריקת Wi-Fi. אנא בדוק הרשאות.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final Runnable scanTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScanning) { // Check if still marked as scanning
                isScanning = false;
                Log.w(TAG, "WiFi scan timed out.");
                tvScanningStatus.setText("זמן קצוב לסריקת Wi-Fi.");
                btnRescan.setEnabled(true);
                if(wifiNetworks.isEmpty()){
                    tvScanningStatus.setText(getString(R.string.no_wifi_networks_found));
                }
            }
        }
    };

    private class WifiScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                Log.d(TAG, "SCAN_RESULTS_AVAILABLE_ACTION received.");
                if (isScanning) { // Process only if we initiated the scan
                    isScanning = false; // Mark scan as complete
                    scanHandler.removeCallbacks(scanTimeoutRunnable); // Remove timeout callback

                    List<ScanResult> results = wifiManager.getScanResults();
                    if (results != null) {
                        wifiNetworks.clear();
                        wifiNetworks.addAll(results);
                        wifiListAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Found " + wifiNetworks.size() + " WiFi networks.");
                        tvScanningStatus.setText(String.format(getString(R.string.no_wifi_networks_found) + " (%d)", wifiNetworks.size())); // Update count
                        if(wifiNetworks.isEmpty()){
                            tvScanningStatus.setText(getString(R.string.no_wifi_networks_found));
                        } else {
                            tvScanningStatus.setText(getString(R.string.tap_to_connect));
                        }

                    } else {
                        Log.w(TAG, "WiFi scan results were null.");
                        tvScanningStatus.setText(getString(R.string.no_wifi_networks_found));
                    }
                    btnRescan.setEnabled(true); // Re-enable rescan button
                }
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (state == WifiManager.WIFI_STATE_ENABLED) {
                    Log.d(TAG, "WiFi state changed to ENABLED.");
                    // If WiFi was turned on, attempt to scan
                    if (btnRescan.isEnabled()) { // Only if not currently scanning
                       checkLocationPermissionAndStartScan();
                    }
                } else if (state == WifiManager.WIFI_STATE_DISABLED) {
                    Log.d(TAG, "WiFi state changed to DISABLED.");
                    tvScanningStatus.setText(getString(R.string.wifi_disabled_message));
                    btnRescan.setText("הפעל Wi-Fi");
                    btnRescan.setEnabled(true);
                    btnRescan.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
                }
            }
        }
    }

    private void connectToWifiNetwork(ScanResult network) {
        if (network == null) return;

        Log.d(TAG, "Attempting to connect to: " + network.SSID);

        // Check if already connected to this network
        WifiInfo currentWifiInfo = wifiManager.getConnectionInfo();
        if (currentWifiInfo != null && network.BSSID.equals(currentWifiInfo.getBSSID())) {
            Toast.makeText(this, getString(R.string.connected_to, network.SSID), Toast.LENGTH_SHORT).show();
            return;
        }

        // Determine security type
        boolean isSecured = network.capabilities.contains("WEP") || network.capabilities.contains("WPA") || network.capabilities.contains("WPA2") || network.capabilities.contains("WPA3");

        if (isSecured) {
            // Ask for password
            showPasswordDialog(network);
        } else {
            // Open network (no password needed)
            connectToOpenNetwork(network);
        }
    }

    private void showPasswordDialog(ScanResult network) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.enter_password_for, network.SSID));

        final EditText passwordEditText = new EditText(this);
        passwordEditText.setHint(getString(R.string.password));
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(passwordEditText);

        builder.setPositiveButton(getString(R.string.connect), (dialog, which) -> {
            String password = passwordEditText.getText().toString();
            if (password.isEmpty()) {
                 Toast.makeText(this, "הסיסמה אינה יכולה להיות ריקה.", Toast.LENGTH_SHORT).show();
                 return;
            }
            connectToSecuredNetwork(network, password);
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void connectToOpenNetwork(ScanResult network) {
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + network.SSID + "\"";
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        // Try to add/update the network configuration
        int netId = wifiManager.addNetwork(conf);
        if (netId == -1) {
            // Try to update existing network
            List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
            if (existingConfigs != null) {
                for (WifiConfiguration existingConfig : existingConfigs) {
                    if (existingConfig.SSID.equals("\"" + network.SSID + "\"")) {
                        netId = existingConfig.networkId;
                        break;
                    }
                }
            }
        }

        if (netId != -1) {
            wifiManager.disconnect();
            if (wifiManager.enableNetwork(netId, true)) {
                wifiManager.reconnect();
                Toast.makeText(this, "מתחבר לרשת " + network.SSID + "...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "שגיאה בהפעלת רשת Wi-Fi.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "שגיאה בחיבור לרשת.", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToSecuredNetwork(ScanResult network, String password) {
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + network.SSID + "\"";

        // Determine security type and configure accordingly
        if (network.capabilities.contains("WPA2-PSK")) {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            conf.preSharedKey = "\"" + password + "\"";
            conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        } else if (network.capabilities.contains("WPA-PSK")) {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            conf.preSharedKey = "\"" + password + "\"";
            conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        } else if (network.capabilities.contains("WEP")) {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            conf.wepKeys[0] = "\"" + password + "\"";
            conf.wepTxKeyIndex = 0;
        } else {
            Toast.makeText(this, "סוג אבטחה לא נתמך.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Try to add/update the network configuration
        int netId = wifiManager.addNetwork(conf);
        if (netId == -1) {
            List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
            if (existingConfigs != null) {
                for (WifiConfiguration existingConfig : existingConfigs) {
                    if (existingConfig.SSID.equals("\"" + network.SSID + "\"")) {
                        netId = existingConfig.networkId;
                        break;
                    }
                }
            }
        }

        if (netId != -1) {
            wifiManager.disconnect();
            if (wifiManager.enableNetwork(netId, true)) {
                wifiManager.reconnect();
                Toast.makeText(this, "מתחבר לרשת " + network.SSID + "...", Toast.LENGTH_SHORT).show();
            } else {
                 Toast.makeText(this, "שגיאה בהפעלת רשת Wi-Fi.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "שגיאה בחיבור לרשת.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(wifiScanReceiver, filter);
        checkLocationPermissionAndStartScan(); // Ensure scan starts on resume if permissions are granted
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }
        scanHandler.removeCallbacks(scanTimeoutRunnable); // Stop any pending timeouts
        isScanning = false; // Reset scanning state
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWifiScan();
            } else {
                Toast.makeText(this, getString(R.string.permission_location_needed_for_wifi), Toast.LENGTH_LONG).show();
                tvScanningStatus.setText(getString(R.string.permission_location_needed_for_wifi));
            }
        }
    }
}
