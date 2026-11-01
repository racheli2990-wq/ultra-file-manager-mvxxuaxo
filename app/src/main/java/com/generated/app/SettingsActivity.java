package com.generated.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);

        findViewById(R.id.btn_back_settings).setOnClickListener(v -> finish());

        Switch switchHidden = findViewById(R.id.switch_hidden);
        switchHidden.setChecked(prefs.getBoolean("show_hidden", false));
        switchHidden.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("show_hidden", isChecked).apply());

        RadioGroup rgSort = findViewById(R.id.rg_sort);
        String sortBy = prefs.getString("sort_by", "name");
        if ("size".equals(sortBy)) {
            rgSort.check(R.id.rb_sort_size);
        } else if ("date".equals(sortBy)) {
            rgSort.check(R.id.rb_sort_date);
        } else {
            rgSort.check(R.id.rb_sort_name);
        }

        rgSort.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_sort_size) {
                prefs.edit().putString("sort_by", "size").apply();
            } else if (checkedId == R.id.rb_sort_date) {
                prefs.edit().putString("sort_by", "date").apply();
            } else {
                prefs.edit().putString("sort_by", "name").apply();
            }
        });

        Button btnClearCache = findViewById(R.id.btn_clear_cache);
        btnClearCache.setOnClickListener(v ->
                Toast.stringToast(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT));
    }
}
