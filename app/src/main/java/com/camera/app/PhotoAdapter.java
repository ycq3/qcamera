package com.pipiqiang.qcamera.app;
import com.pipiqiang.qcamera.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.lang.ref.WeakReference;
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
    
    // 使用LruCache作为内存缓存
    private LruCache<String, Bitmap> memoryCache;
    private int thumbnailSize = 200; // 缩略图大小
    
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
        
        // 初始化内存缓存
        initMemoryCache();
        
        // 计算合适的缩略图大小
        calculateThumbnailSize();
    }
    
    // 初始化内存缓存
    private void initMemoryCache() {
        // 获取可用内存的最大值，单位是KB
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // 使用最大可用内存的1/8作为缓存大小
        int cacheSize = maxMemory / 8;
        
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // 重写此方法来衡量每张图片的大小，单位是KB
                return bitmap.getByteCount() / 1024;
            }
        };
    }
    
    // 计算合适的缩略图大小
    private void calculateThumbnailSize() {
        // 根据屏幕密度调整缩略图大小
        float density = context.getResources().getDisplayMetrics().density;
        thumbnailSize = (int) (120 * density);
        if (thumbnailSize < 200) thumbnailSize = 200;
        if (thumbnailSize > 400) thumbnailSize = 400;
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
        loadThumbnail(holder.imageView, photoItem.getPath());
        
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
    
    // 异步加载缩略图
    private void loadThumbnail(ImageView imageView, String imagePath) {
        // 首先设置默认图片
        imageView.setImageResource(R.drawable.ic_camera);
        
        // 检查内存缓存
        Bitmap cachedBitmap = getBitmapFromMemCache(imagePath);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            imageView.setImageBitmap(cachedBitmap);
            return;
        }
        
        // 异步加载图片
        new ThumbnailLoaderTask(imageView, imagePath, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    
    // 添加图片到内存缓存
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            memoryCache.put(key, bitmap);
        }
    }
    
    // 从内存缓存获取图片
    private Bitmap getBitmapFromMemCache(String key) {
        return memoryCache.get(key);
    }
    
    // 缩略图加载任务
    private static class ThumbnailLoaderTask extends AsyncTask<Void, Void, Bitmap> {
        private WeakReference<ImageView> imageViewReference;
        private String imagePath;
        private WeakReference<PhotoAdapter> adapterReference;
        
        public ThumbnailLoaderTask(ImageView imageView, String imagePath, PhotoAdapter adapter) {
            this.imageViewReference = new WeakReference<>(imageView);
            this.imagePath = imagePath;
            this.adapterReference = new WeakReference<>(adapter);
        }
        
        @Override
        protected Bitmap doInBackground(Void... voids) {
            try {
                File file = new File(imagePath);
                if (!file.exists()) {
                    return null;
                }
                
                // 检查是否已取消
                if (isCancelled()) {
                    return null;
                }
                
                // 读取图片尺寸
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(imagePath, options);
                
                // 计算缩放比例
                PhotoAdapter adapter = adapterReference.get();
                if (adapter == null) return null;
                
                int scale = Math.min(options.outWidth, options.outHeight) / adapter.thumbnailSize;
                if (scale <= 0) scale = 1;
                
                // 使用更激进的缩放以减少内存占用
                scale = (int) Math.ceil(scale / 2.0);
                if (scale <= 0) scale = 1;
                
                options.inJustDecodeBounds = false;
                options.inSampleSize = scale;
                options.inPreferredConfig = Bitmap.Config.RGB_565; // 使用较低的色彩配置节省内存
                options.inPurgeable = true;  // 设置位图可被回收
                options.inInputShareable = true;  // 设置位图可共享引用
                
                // 检查是否已取消
                if (isCancelled()) {
                    return null;
                }
                
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
                
                // 检查是否已取消
                if (isCancelled()) {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    return null;
                }
                
                // 将加载的图片添加到缓存
                if (bitmap != null && adapter != null) {
                    adapter.addBitmapToMemoryCache(imagePath, bitmap);
                }
                
                return bitmap;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                return;
            }
            
            ImageView imageView = imageViewReference.get();
            if (imageView != null && bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }
    
    // 清理图片缓存
    public void clearImageCache() {
        memoryCache.evictAll();
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
    
    // 全选功能
    public void selectAll() {
        selectedPhotos.clear();
        selectedPhotos.addAll(photoList);
        notifyDataSetChanged();
        
        if (listener != null) {
            listener.onSelectionChanged(selectedPhotos.size());
        }
    }
    
    // 取消全选
    public void deselectAll() {
        selectedPhotos.clear();
        notifyDataSetChanged();
        
        if (listener != null) {
            listener.onSelectionChanged(0);
        }
        
        // 退出选择模式
        setSelectionMode(false);
    }
    
    // 检查是否全选
    public boolean isAllSelected() {
        return selectedPhotos.size() == photoList.size() && !photoList.isEmpty();
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