package com.camera.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    
    private Context context;
    private List<PhotoItem> photoList;
    private OnPhotoClickListener listener;
    
    public interface OnPhotoClickListener {
        void onPhotoClick(int position);
        void onPhotoLongClick(int position);
    }
    
    public PhotoAdapter(Context context, List<PhotoItem> photoList, OnPhotoClickListener listener) {
        this.context = context;
        this.photoList = photoList;
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
        
        // 设置点击事件
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onPhotoClick(holder.getAdapterPosition());
                }
            }
        });
        
        // 设置长按事件
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (listener != null) {
                    listener.onPhotoLongClick(holder.getAdapterPosition());
                    return true;
                }
                return false;
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return photoList.size();
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
        
        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
            textView = itemView.findViewById(R.id.text_view);
        }
    }
}