package com.camera.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    
    private Context context;
    private List<PhotoItem> photoList;
    private List<PhotoItem> selectedPhotos; // 选中的照片列表
    private OnPhotoClickListener listener;
    private boolean isSelectionMode = false; // 是否处于选择模式
    
    public interface OnPhotoClickListener {
        void onPhotoClick(int position);
        void onPhotoLongClick(int position);
        void onSelectionChanged(int selectedCount); // 选择数量变化回调
    }
    
    public PhotoAdapter(Context context, List<PhotoItem> photoList, OnPhotoClickListener listener) {
        this.context = context;
        this.photoList = photoList;
        this.selectedPhotos = new ArrayList<>();
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        PhotoItem photoItem = photoList.get(position);
        
        // 加载缩略图
        Bitmap thumbnail = createThumbnail(photoItem.getPath());
        if (thumbnail != null) {
            holder.imageView.setImageBitmap(thumbnail);
        } else {
            holder.imageView.setImageResource(R.drawable.ic_camera); // 默认图片
        }
        
        // 显示拍摄时间
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date(photoItem.getTimestamp()));
        holder.textView.setText(timeStr);
        
        // 设置选择状态
        holder.checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setChecked(selectedPhotos.contains(photoItem));
        
        // 设置点击事件
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSelectionMode) {
                    // 选择模式下，切换选中状态
                    toggleSelection(photoItem, holder.getAdapterPosition());
                } else {
                    // 正常模式下，打开照片
                    if (listener != null) {
                        listener.onPhotoClick(holder.getAdapterPosition());
                    }
                }
            }
        });
        
        // 设置长按事件
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isSelectionMode) {
                    // 进入选择模式
                    setSelectionMode(true);
                    toggleSelection(photoItem, holder.getAdapterPosition());
                    return true;
                }
                return false;
            }
        });
        
        // CheckBox点击事件
        holder.checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSelection(photoItem, holder.getAdapterPosition());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return photoList.size();
    }
    
    // 切换照片选中状态
    private void toggleSelection(PhotoItem photoItem, int position) {
        if (selectedPhotos.contains(photoItem)) {
            selectedPhotos.remove(photoItem);
        } else {
            selectedPhotos.add(photoItem);
        }
        
        notifyItemChanged(position);
        
        if (listener != null) {
            listener.onSelectionChanged(selectedPhotos.size());
        }
        
        // 如果没有选中的照片，退出选择模式
        if (selectedPhotos.isEmpty()) {
            setSelectionMode(false);
        }
    }
    
    // 设置选择模式
    public void setSelectionMode(boolean selectionMode) {
        isSelectionMode = selectionMode;
        notifyDataSetChanged();
        
        if (!selectionMode) {
            selectedPhotos.clear();
            if (listener != null) {
                listener.onSelectionChanged(0);
            }
        }
    }
    
    // 获取选中的照片路径列表
    public List<String> getSelectedPhotoPaths() {
        List<String> paths = new ArrayList<>();
        for (PhotoItem item : selectedPhotos) {
            paths.add(item.getPath());
        }
        return paths;
    }
    
    // 删除选中的照片
    public void deleteSelectedPhotos() {
        for (PhotoItem item : selectedPhotos) {
            // 删除文件
            File file = new File(item.getPath());
            if (file.exists()) {
                file.delete();
            }
            // 从列表中移除
            photoList.remove(item);
        }
        
        // 清空选中列表
        selectedPhotos.clear();
        
        // 退出选择模式
        setSelectionMode(false);
        
        // 通知数据改变
        notifyDataSetChanged();
    }
    
    private Bitmap createThumbnail(String imagePath) {
        try {
            File file = new File(imagePath);
            if (!file.exists()) {
                return null;
            }
            
            // 读取图片尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);
            
            // 计算缩放比例
            int scale = Math.min(options.outWidth, options.outHeight) / 200; // 缩略图大小约200px
            if (scale <= 0) scale = 1;
            
            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;
            
            return BitmapFactory.decodeFile(imagePath, options);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;
        CheckBox checkBox;
        
        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
            textView = itemView.findViewById(R.id.text_view);
            checkBox = itemView.findViewById(R.id.checkbox);
        }
    }
}