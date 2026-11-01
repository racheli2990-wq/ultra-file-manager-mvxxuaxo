package com.generated.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

public class MainActivity extends Activity {

    private static final int REQ_CODE_STORAGE = 1001;
    private static final int REQ_CODE_WIFI_SETTINGS = 1002;
    private static final int REQ_CODE_LOCATION_PERMISSION = 1003;

    private ListView listView;
    private GridView gridView;
    private FileListAdapter adapter;
    private TextView tvCurrentPath, tvStorageInfo, tvEmpty;
    private ProgressBar pbStorage;
    private EditText etSearch;
    private LinearLayout layoutPermission;

    private TextView chipAll, chipImages, chipVideos, chipAudio, chipDocs, chipDownloads;

    private File currentDirectory;
    private final Stack<File> historyStack = new Stack<>();
    private List<FileItem> currentFileList = new ArrayList<>();
    private List<FileItem> filteredFileList = new ArrayList<>();

    private WifiManager wifiManager;
    private WifiScanReceiver wifiScanReceiver;
    private List<ScanResult> wifiNetworks = new ArrayList<>();
    private boolean isScanningWifi = false;
    private final Handler wifiScanHandler = new Handler(Looper.getMainLooper());

    private boolean isGridView = false;
    private int currentCategory = 0; // 0: All, 1: Images, 2: Videos, 3: Audio, 4: Docs, 5: Downloads

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        checkPermissionsAndLoad();

        // Initialize WiFi related components
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiScanReceiver = new WifiScanReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            layoutPermission.setVisibility(View.GONE);
            if (currentDirectory != null) {
                loadDirectory(currentDirectory);
            }
        }

        // Register WiFi scan receiver
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, filter);

        // Check and request location permission if needed for WiFi scanning
        checkLocationPermissionForWifi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister WiFi scan receiver
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }
        // Stop WiFi scanning if active
        if (isScanningWifi) {
            wifiScanHandler.removeCallbacks(wifiScanRunnable);
            isScanningWifi = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure receiver is unregistered
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }
    }

    private void initViews() {
        listView = findViewById(R.id.list_view);
        gridView = findViewById(R.id.grid_view);
        tvCurrentPath = findViewById(R.id.tv_current_path);
        tvStorageInfo = findViewById(R.id.tv_storage_info);
        tvEmpty = findViewById(R.id.tv_empty);
        pbStorage = findViewById(R.id.pb_storage);
        etSearch = findViewById(R.id.et_search);
        layoutPermission = findViewById(R.id.layout_permission);

        chipAll = findViewById(R.id.chip_all);
        chipImages = findViewById(R.id.chip_images);
        chipVideos = findViewById(R.id.chip_videos);
        chipAudio = findViewById(R.id.chip_audio);
        chipDocs = findViewById(R.id.chip_docs);
        chipDownloads = findViewById(R.id.chip_downloads);

        updateAdapter();
    }

    private void updateAdapter() {
        adapter = new FileListAdapter(this, isGridView);
        if (isGridView) {
            gridView.setAdapter(adapter);
            gridView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            listView.setAdapter(adapter);
            listView.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.GONE);
        }
        adapter.setData(filteredFileList);
    }

    private void setupListeners() {
        ImageButton btnToggle = findViewById(R.id.btn_view_toggle);
        btnToggle.setOnClickListener(v -> {
            isGridView = !isGridView;
            btnToggle.setImageResource(isGridView ? R.drawable.ic_list : R.drawable.ic_grid);
            updateAdapter();
        });

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        findViewById(R.id.btn_about).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AboutActivity.class)));

        findViewById(R.id.btn_back_folder).setOnClickListener(v -> navigateBack());

        findViewById(R.id.fab_add).setOnClickListener(v -> showCreateFolderDialog());

        findViewById(R.id.btn_grant_permission).setOnClickListener(v -> requestStoragePermission());

        findViewById(R.id.btn_wifi_scan).setOnClickListener(v -> {
            Intent wifiIntent = new Intent(this, WifiScannerActivity.class);
            startActivity(wifiIntent);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFiles(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        AdapterView.OnItemClickListener itemClickListener = (parent, view, position, id) -> {
            FileItem item = adapter.getItem(position);
            if (item.isDirectory()) {
                historyStack.push(currentDirectory);
                loadDirectory(item.getFile());
            } else {
                openFile(item);
            }
        };

        AdapterView.OnItemLongClickListener itemLongClickListener = (parent, view, position, id) -> {
            FileItem item = adapter.getItem(position);
            showFileOptionsDialog(item);
            return true;
        };

        listView.setOnItemClickListener(itemClickListener);
        listView.setOnItemLongClickListener(itemLongClickListener);
        gridView.setOnItemClickListener(itemClickListener);
        gridView.setOnItemLongClickListener(itemLongClickListener);

        setupCategoryChips();
    }

    private void setupCategoryChips() {
        View.OnClickListener chipListener = v -> {
            resetCategoryChips();
            TextView tv = (TextView) v;
            tv.setBackgroundResource(R.drawable.bg_chip_selected);
            tv.setTextColor(getResources().getColor(R.color.primary));

            if (v.getId() == R.id.chip_all) currentCategory = 0;
            else if (v.getId() == R.id.chip_images) currentCategory = 1;
            else if (v.getId() == R.id.chip_videos) currentCategory = 2;
            else if (v.getId() == R.id.chip_audio) currentCategory = 3;
            else if (v.getId() == R.id.chip_docs) currentCategory = 4;
            else if (v.getId() == R.id.chip_downloads) {
                currentCategory = 5;
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir != null && downloadsDir.exists()) {
                    loadDirectory(downloadsDir);
                    return;
                }
            }
            filterByCategory();
        };

        chipAll.setOnClickListener(chipListener);
        chipImages.setOnClickListener(chipListener);
        chipVideos.setOnClickListener(chipListener);
        chipAudio.setOnClickListener(chipListener);
        chipDocs.setOnClickListener(chipListener);
        chipDownloads.setOnClickListener(chipListener);
    }

    private void resetCategoryChips() {
        int textSecondary = getResources().getColor(R.color.text_secondary);
        TextView[] chips = {chipAll, chipImages, chipVideos, chipAudio, chipDocs, chipDownloads};
        for (TextView c : chips) {
            c.setBackgroundResource(R.drawable.bg_chip_normal);
            c.setTextColor(textSecondary);
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkPermissionsAndLoad() {
        if (!hasStoragePermission()) {
            layoutPermission.setVisibility(View.VISIBLE);
        } else {
            layoutPermission.setVisibility(View.GONE);
            updateStorageStats();
            loadDirectory(Environment.getExternalStorageDirectory());
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_CODE_STORAGE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQ_CODE_STORAGE);
            }
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_CODE_STORAGE);
        }
    }

    private void updateStorageStats() {
        try {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            long totalBytes = totalBlocks * blockSize;
            long freeBytes = availableBlocks * blockSize;
            long usedBytes = totalBytes - freeBytes;

            int progress = (int) ((usedBytes * 100) / totalBytes);
            pbStorage.setProgress(progress);

            String formattedUsed = formatSize(usedBytes);
            String formattedTotal = formatSize(totalBytes);
            tvStorageInfo.setText(getString(R.string.storage_used, formattedUsed, formattedTotal));
        } catch (Exception e) {
            tvStorageInfo.setText("נתוני אחסון לא זמינים");
            Log.e("MainActivity", "Error updating storage stats", e);
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format(Locale.getDefault(), "%.1f %cB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private void loadDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            Log.e("MainActivity", "Attempted to load invalid directory: " + dir);
            return;
        }

        this.currentDirectory = dir;
        tvCurrentPath.setText(dir.getAbsolutePath());

        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean showHidden = prefs.getBoolean("show_hidden", false);
        String sortBy = prefs.getString("sort_by", "name");

        File[] files = dir.listFiles();
        currentFileList.clear();

        if (files != null) {
            for (File f : files) {
                if (!showHidden && f.getName().startsWith(".")) continue;
                currentFileList.add(new FileItem(f));
            }

            // Sort files
            Collections.sort(currentFileList, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;

                if ("size".equals(sortBy)) {
                    return Long.compare(b.getSize(), a.getSize());
                } else if ("date".equals(sortBy)) {
                    return Long.compare(b.getLastModified(), a.getLastModified());
                } else {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
        } else {
            Log.w("MainActivity", "Directory is null or empty: " + dir.getAbsolutePath());
        }

        filterByCategory();
    }

    private void filterByCategory() {
        filteredFileList.clear();
        for (FileItem item : currentFileList) {
            if (currentCategory == 0) { // All
                filteredFileList.add(item);
            } else if (currentCategory == 1 && item.getType() == FileItem.TYPE_IMAGE) {
                filteredFileList.add(item);
            } else if (currentCategory == 2 && item.getType() == FileItem.TYPE_VIDEO) {
                filteredFileList.add(item);
            } else if (currentCategory == 3 && item.getType() == FileItem.TYPE_AUDIO) {
                filteredFileList.add(item);
            } else if (currentCategory == 4 && item.getType() == FileItem.TYPE_DOC) {
                filteredFileList.add(item);
            } else if (currentCategory == 5 && item.getFile().getParentFile() != null &&
                       item.getFile().getParentFile().getName().equals(Environment.DIRECTORY_DOWNLOADS)) {
                // Special handling for Downloads category - requires checking parent, might not be perfect
                filteredFileList.add(item);
            }
        }
        adapter.setData(filteredFileList);
        tvEmpty.setVisibility(filteredFileList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void filterFiles(String query) {
        if (query.trim().isEmpty()) {
            filterByCategory(); // Reset to category filter
            return;
        }
        List<FileItem> searchResults = new ArrayList<>();
        for (FileItem item : filteredFileList) { // Search within the currently filtered list
            if (item.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                searchResults.add(item);
            }
        }
        adapter.setData(searchResults);
    }

    private void openFile(FileItem item) {
        File file = item.getFile();
        int type = item.getType();

        try {
            if (type == FileItem.TYPE_IMAGE) {
                Intent intent = new Intent(this, ImageViewerActivity.class);
                intent.putExtra("file_path", file.getAbsolutePath());
                startActivity(intent);
            } else if (type == FileItem.TYPE_VIDEO) {
                Intent intent = new Intent(this, VideoViewerActivity.class);
                intent.putExtra("file_path", file.getAbsolutePath());
                startActivity(intent);
            } else if (type == FileItem.TYPE_DOC || type == FileItem.TYPE_OTHER) {
                Intent intent = new Intent(this, TextViewerActivity.class);
                intent.putExtra("file_path", file.getAbsolutePath());
                startActivity(intent);
            } else if (type == FileItem.TYPE_FOLDER) {
                historyStack.push(currentDirectory);
                loadDirectory(item.getFile());
            } else {
                Toast.makeText(this, "פתיחת קובץ מסוג זה אינה נתמכת ישירות.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בפתיחת הקובץ", Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "Error opening file: " + file.getName(), e);
        }
    }

    private void showFileOptionsDialog(FileItem item) {
        String[] options = {getString(R.string.open), getString(R.string.rename), getString(R.string.delete), getString(R.string.details)};

        new AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (item.isDirectory()) {
                                historyStack.push(currentDirectory);
                                loadDirectory(item.getFile());
                            } else {
                                openFile(item);
                            }
                            break;
                        case 1:
                            showRenameDialog(item);
                            break;
                        case 2:
                            confirmDelete(item);
                            break;
                        case 3:
                            showDetailsDialog(item);
                            break;
                    }
                })
                .show();
    }

    private void showRenameDialog(FileItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.rename_title);

        final EditText input = new EditText(this);
        input.setText(item.getName());
        builder.setView(input);

        builder.setPositiveButton(R.string.rename, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                File target = new File(currentDirectory, newName);
                if (item.getFile().renameTo(target)) {
                    loadDirectory(currentDirectory); // Reload current directory to reflect changes
                    Toast.makeText(this, "השם שונה בהצלחה", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "כישלון בשינוי השם", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "השם לא יכול להיות ריק", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void confirmDelete(FileItem item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.confirm_delete, item.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (item.getFile().delete()) {
                        Toast.makeText(this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show();
                        loadDirectory(currentDirectory); // Reload current directory
                        updateStorageStats();
                    } else {
                        Toast.makeText(this, getString(R.string.delete_error), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDetailsDialog(FileItem item) {
        String info = String.format(Locale.getDefault(),
                "שם: %s\n\nנתיב: %s\n\nגודל: %s\n\nתאריך: %s",
                item.getName(), item.getFile().getAbsolutePath(), item.getFormattedSize(), item.getFormattedDate());

        new AlertDialog.Builder(this)
                .setTitle(R.string.file_details_title)
                .setMessage(info)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void showCreateFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.new_folder);

        final EditText input = new EditText(this);
        input.setHint(R.string.enter_folder_name_hint);
        builder.setView(input);

        builder.setPositiveButton(R.string.create, (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                File newFolder = new File(currentDirectory, folderName);
                if (newFolder.mkdir()) {
                    loadDirectory(currentDirectory); // Reload to show new folder
                    Toast.makeText(this, "התיקייה נוצרה בהצלחה", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "שגיאה ביצירת התיקייה", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "שם התיקייה לא יכול להיות ריק", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void navigateBack() {
        if (!historyStack.isEmpty()) {
            loadDirectory(historyStack.pop());
        }
    }

    @Override
    public void onBackPressed() {
        if (!historyStack.isEmpty()) {
            navigateBack();
        } else {
            super.onBackPressed();
        }
    }

    // --- WiFi Scanning Logic ---

    private void checkLocationPermissionForWifi() {
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
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            if (!isScanningWifi) {
                isScanningWifi = true;
                wifiManager.startScan();
                Log.d("MainActivity", "WiFi scan initiated.");
                // Schedule a callback to stop scanning and process results after a delay
                wifiScanHandler.postDelayed(wifiScanRunnable, 5000); // Scan results usually available within a few seconds
            }
        } else {
            Toast.makeText(this, "אנא הפעל את ה-Wi-Fi כדי לסרוק רשתות", Toast.LENGTH_LONG).show();
            // Optionally, offer to open WiFi settings
            findViewById(R.id.btn_wifi_scan).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        }
    }

    private final Runnable wifiScanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScanningWifi) {
                isScanningWifi = false;
                Log.d("MainActivity", "Processing WiFi scan results.");
                // The broadcast receiver will handle processing, this is just a timeout
            }
        }
    };

    private class WifiScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                Log.d("MainActivity", "SCAN_RESULTS_AVAILABLE_ACTION received.");
                if (isScanningWifi) { // Only process if we initiated the scan
                    isScanningWifi = false; // Stop considering it active
                    wifiScanHandler.removeCallbacks(wifiScanRunnable); // Remove any pending timeout
                    List<ScanResult> results = wifiManager.getScanResults();
                    if (results != null) {
                        wifiNetworks.clear();
                        wifiNetworks.addAll(results);
                        Log.d("MainActivity", "Found " + wifiNetworks.size() + " WiFi networks.");
                        // You can now use wifiNetworks list, e.g., display it or filter it
                        // For now, just log the count.
                    } else {
                        Log.w("MainActivity", "WiFi scan results were null.");
                    }
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_STORAGE) {
            if (hasStoragePermission()) {
                checkPermissionsAndLoad();
            } else {
                Toast.makeText(this, "הרשאת אחסון נדחתה. לא ניתן לגשת לקבצים.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_CODE_LOCATION_PERMISSION) {
            if (hasLocationPermission()) {
                startWifiScan();
            } else {
                Toast.makeText(this, "הרשאת מיקום נדחתה. סריקת Wi-Fi אינה זמינה.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
