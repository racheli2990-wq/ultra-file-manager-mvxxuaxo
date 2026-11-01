package com.generated.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        findViewById(R.id.btn_back_about).setOnClickListener(v -> finish());

        ImageView ivAvatar = findViewById(R.id.iv_avatar);
        ivAvatar.setOnClickListener(v -> {
            ivAvatar.animate().rotationBy(360f).setDuration(600).start();
        });

        Button btnOle = findViewById(R.id.btn_ole);
        btnOle.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.ole_toast), Toast.LENGTH_LONG).show());
    }
}
