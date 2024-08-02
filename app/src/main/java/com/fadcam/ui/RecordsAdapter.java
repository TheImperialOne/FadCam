package com.fadcam.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fadcam.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RecordsAdapter extends RecyclerView.Adapter<RecordsAdapter.RecordViewHolder> {

    private final Context context;
    private List<File> records;
    private final OnVideoClickListener clickListener;
    private final OnVideoLongClickListener longClickListener;
    private final List<File> selectedVideos = new ArrayList<>();

    public RecordsAdapter(Context context, List<File> records, OnVideoClickListener clickListener, OnVideoLongClickListener longClickListener) {
        this.context = context;
        this.records = records;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public interface OnVideoClickListener {
        void onVideoClick(File video);
    }

    public interface OnVideoLongClickListener {
        void onVideoLongClick(File video, boolean isSelected);
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        File video = records.get(position);
        setThumbnail(holder, video);
        holder.textViewRecord.setText(video.getName());

        holder.itemView.setOnClickListener(v -> clickListener.onVideoClick(video));
        holder.itemView.setOnLongClickListener(v -> {
            boolean isSelected = !selectedVideos.contains(video);
            toggleSelection(holder, video, isSelected);
            longClickListener.onVideoLongClick(video, isSelected);
            return true;
        });

        holder.menuButton.setOnClickListener(v -> showPopupMenu(v, video));
        updateSelectionState(holder, selectedVideos.contains(video));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    private void setThumbnail(RecordViewHolder holder, File video) {
        Glide.with(context)
                .load(video.getAbsolutePath())
                .placeholder(R.drawable.ic_video_placeholder)
                .into(holder.imageViewThumbnail);
    }

    private void toggleSelection(RecordViewHolder holder, File video, boolean isSelected) {
        if (isSelected) {
            selectedVideos.add(video);
        } else {
            selectedVideos.remove(video);
        }
        int position = records.indexOf(video);
        notifyItemChanged(position);
    }

    private void updateSelectionState(RecordViewHolder holder, boolean isSelected) {
        holder.itemView.setActivated(isSelected);
        holder.checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
    }

    private void showPopupMenu(View v, File video) {
        PopupMenu popup = new PopupMenu(v.getContext(), v);
        popup.getMenuInflater().inflate(R.menu.video_item_menu, popup.getMenu());

        popup.getMenu().findItem(R.id.action_delete).setIcon(R.drawable.ic_delete);
        popup.getMenu().findItem(R.id.action_save_to_gallery).setIcon(R.drawable.ic_save);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete) {
                confirmDelete(v.getContext(), video);
                return true;
            }
            if (item.getItemId() == R.id.action_save_to_gallery) {
                saveToGallery(v.getContext(), video);
                return true;
            }
            return false;
        });

        try {
            Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popup);
            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        popup.show();
    }

    private void confirmDelete(Context context, File video) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Delete Forever?")
                .setMessage("Are you sure you want to delete this video?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (video.delete()) {
                        int position = records.indexOf(video);
                        records.remove(video);
                        notifyItemRemoved(position);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveToGallery(Context context, File video) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToGalleryAndroid10Plus(context, video);
        } else {
            saveToGalleryLegacy(context, video);
        }
    }

    private void saveToGalleryAndroid10Plus(Context context, File video) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, video.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "FadCam");

        ContentResolver resolver = context.getContentResolver();
        Uri collection = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        Uri itemUri = resolver.insert(collection, values);

        if (itemUri != null) {
            try (InputStream in = new FileInputStream(video);
                 OutputStream out = resolver.openOutputStream(itemUri)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                Toast.makeText(context, "Video saved to FadCam folder in Downloads", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(context, "Failed to save video", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            Toast.makeText(context, "Failed to save video", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToGalleryLegacy(Context context, File video) {
        File destDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FadCam");
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        File destFile = new File(destDir, video.getName());
        try (FileInputStream in = new FileInputStream(video);
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            Toast.makeText(context, "Video saved to FadCam folder in Downloads", Toast.LENGTH_SHORT).show();
            MediaScannerConnection.scanFile(context, new String[]{destFile.getAbsolutePath()}, null, null);
        } catch (IOException e) {
            Toast.makeText(context, "Failed to save video", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {
        ImageView imageViewThumbnail;
        TextView textViewRecord;
        ImageView checkIcon;
        ImageView menuButton;

        RecordViewHolder(View itemView) {
            super(itemView);
            imageViewThumbnail = itemView.findViewById(R.id.image_view_thumbnail);
            textViewRecord = itemView.findViewById(R.id.text_view_record);
            checkIcon = itemView.findViewById(R.id.check_icon);
            menuButton = itemView.findViewById(R.id.menu_button);
        }
    }

    public void updateRecords(List<File> newRecords) {
        DiffUtil.Callback diffCallback = new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return records.size();
            }

            @Override
            public int getNewListSize() {
                return newRecords.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return records.get(oldItemPosition).equals(newRecords.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return records.get(oldItemPosition).equals(newRecords.get(newItemPosition));
            }
        };

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.records = newRecords;
        diffResult.dispatchUpdatesTo(this);
    }

    public void updateThumbnail(String videoFilePath) {
        notifyDataSetChanged();
    }
}
