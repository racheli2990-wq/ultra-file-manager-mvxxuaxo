package com.generated.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class TextViewerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_viewer);

        TextView tvTitle = findViewById(R.id.tv_text_title);
        TextView tvContent = findViewById(R.id.tv_text_content);
        TextView tvStats = findViewById(R.id.tv_text_stats);

        findViewById(R.id.btn_close_text).setOnClickListener(v -> finish());

        String path = getIntent().getStringExtra("file_path");
        if (path != null) {
            File file = new File(path);
            tvTitle.setText(file.getName());

            StringBuilder text = new StringBuilder();
            int lineCount = 0;
            int charCount = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line).append("\n");
                    lineCount++;
                    charCount += line.length();
                }
            } catch (Exception e) {
                text.append("שגיאה בקריאת הקובץ");
            }

            tvContent.setText(text.toString());
            tvStats.setText("שורות: " + lineCount + " | תווים: " + charCount);
        }
    }
}
