package com.generated.app;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;

public class VideoViewerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_viewer);

        VideoView videoView = findViewById(R.id.video_view);
        TextView tvTitle = findViewById(R.id.tv_video_title);

        findViewById(R.id.btn_close_video).setOnClickListener(v -> finish());

        String path = getIntent().getStringExtra("file_path");
        if (path != null) {
            File file = new File(path);
            tvTitle.setText(file.getName());

            MediaController mediaController = new MediaController(this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
            videoView.setVideoURI(Uri.fromFile(file));
            videoView.start();
        }
    }
}
