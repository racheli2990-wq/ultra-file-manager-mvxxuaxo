package com.generated.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
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

    private boolean isGridView = false;
    private int currentCategory = 0; // 0: All, 1: Images, 2: Videos, 3: Audio, 4: Docs, 5: Downloads

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        checkPermissionsAndLoad();
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
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format(Locale.getDefault(), "%.1f %cB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private void loadDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) return;

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
        }

        filterByCategory();
    }

    private void filterByCategory() {
        filteredFileList.clear();
        for (FileItem item : currentFileList) {
            if (currentCategory == 0) {
                filteredFileList.add(item);
            } else if (currentCategory == 1 && item.getType() == FileItem.TYPE_IMAGE) {
                filteredFileList.add(item);
            } else if (currentCategory == 2 && item.getType() == FileItem.TYPE_VIDEO) {
                filteredFileList.add(item);
            } else if (currentCategory == 3 && item.getType() == FileItem.TYPE_AUDIO) {
                filteredFileList.add(item);
            } else if (currentCategory == 4 && item.getType() == FileItem.TYPE_DOC) {
                filteredFileList.add(item);
            }
        }
        adapter.setData(filteredFileList);
        tvEmpty.setVisibility(filteredFileList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void filterFiles(String query) {
        if (query.trim().isEmpty()) {
            filterByCategory();
            return;
        }
        List<FileItem> searchResults = new ArrayList<>();
        for (FileItem item : filteredFileList) {
            if (item.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                searchResults.add(item);
            }
        }
        adapter.setData(searchResults);
    }

    private void openFile(FileItem item) {
        File file = item.getFile();
        int type = item.getType();

        if (type == FileItem.TYPE_IMAGE) {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
            startActivity(intent);
        } else if (type == FileItem.TYPE_VIDEO) {
            Intent intent = new Intent(this, VideoViewerActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
            startActivity(intent);
        } else if (type == FileItem.TYPE_DOC) {
            Intent intent = new Intent(this, TextViewerActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
            startActivity(intent);
        } else {
            Toast.makeText(this, "פתיחת קובץ מסוג זה נתמכת דרך אפליקציות חיצוניות", Toast.LENGTH_SHORT).show();
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
                    loadDirectory(currentDirectory);
                    Toast.makeText(this, "השם שונה בהצלחה", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "כישלון בשינוי השם", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void confirmDelete(FileItem item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (item.getFile().delete()) {
                        Toast.makeText(this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show();
                        loadDirectory(currentDirectory);
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
        input.setHint("שם התיקייה");
        builder.setView(input);

        builder.setPositiveButton(R.string.create, (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                File newFolder = new File(currentDirectory, folderName);
                if (newFolder.mkdir()) {
                    loadDirectory(currentDirectory);
                    Toast.makeText(this, "התיקייה נוצרה", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "שגיאה ביצירת התיקייה", Toast.LENGTH_SHORT).show();
                }
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
}
