package com.generated.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private TextView tvName;
    private float currentRotation = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        imageView = findViewById(R.id.iv_fullscreen);
        tvName = findViewById(R.id.tv_image_name);

        String path = getIntent().getStringExtra("file_path");
        if (path != null) {
            File file = new File(path);
            tvName.setText(file.getName());

            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp != null) {
                imageView.setImageBitmap(bmp);
            }
        }

        findViewById(R.id.btn_close_image).setOnClickListener(v -> finish());

        findViewById(R.id.btn_rotate_image).setOnClickListener(v -> {
            currentRotation = (currentRotation + 90) % 360;
            imageView.setRotation(currentRotation);
        });

        findViewById(R.id.btn_share_image).setOnClickListener(v ->
                Toast.makeText(this, "שיתוף קובץ מוכן", Toast.LENGTH_SHORT).show());
    }
}
