package com.generated.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileListAdapter extends BaseAdapter {

    private final Context context;
    private final List<FileItem> items = new ArrayList<>();
    private final boolean isGrid;
    private final LayoutInflater inflater;
    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FileListAdapter(Context context, boolean isGrid) {
        this.context = context;
        this.isGrid = isGrid;
        this.inflater = LayoutInflater.from(context);

        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        this.memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void setData(List<FileItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public FileItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            int layoutId = isGrid ? R.layout.item_file_grid : R.layout.item_file_list;
            convertView = inflater.inflate(layoutId, parent, false);
            holder = new ViewHolder();
            holder.ivIcon = convertView.findViewById(R.id.iv_file_icon);
            holder.tvName = convertView.findViewById(R.id.tv_file_name);
            if (!isGrid) {
                holder.tvDetails = convertView.findViewById(R.id.tv_file_details);
            }
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        FileItem item = getItem(position);
        holder.tvName.setText(item.getName());

        if (!isGrid && holder.tvDetails != null) {
            holder.tvDetails.setText(item.getFormattedSize() + " • " + item.getFormattedDate());
        }

        holder.ivIcon.setImageResource(item.getIconResId());

        if (item.getType() == FileItem.TYPE_IMAGE) {
            loadThumbnailAsync(item.getFile(), holder.ivIcon);
        }

        return convertView;
    }

    private void loadThumbnailAsync(File file, ImageView imageView) {
        String path = file.getAbsolutePath();
        Bitmap cached = memoryCache.get(path);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        executor.execute(() -> {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 8;
                Bitmap bmp = BitmapFactory.decodeFile(path, options);
                if (bmp != null) {
                    memoryCache.put(path, bmp);
                    mainHandler.post(() -> imageView.setImageBitmap(bmp));
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static class ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvDetails;
    }
}
