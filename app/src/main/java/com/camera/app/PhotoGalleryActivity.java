package com.camera.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PhotoGalleryActivity extends AppCompatActivity implements PhotoAdapter.OnPhotoClickListener {
    
    private static final int PERMISSION_REQUEST_CODE = 101;
    
    private RecyclerView recyclerView;
    private PhotoAdapter photoAdapter;
    private List<PhotoItem> photoList;
    private Map<String, List<PhotoItem>> photosByDate; // 按日期分组的照片
    private boolean isSelectionMode = false; // 是否处于选择模式
    private MenuItem deleteMenuItem; // 删除菜单项
    private MenuItem selectAllMenuItem; // 全选菜单项
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_gallery);
        
        // 启用返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("照片库");
        }
        
        initViews();
        
        // 检查存储权限
        if (checkStoragePermission()) {
            loadPhotos();
        } else {
            Toast.makeText(this, "需要存储权限才能查看照片", Toast.LENGTH_LONG).show();
        }
    }
    
    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view);
        photoList = new ArrayList<>();
        photosByDate = new LinkedHashMap<>(); // 保持插入顺序
        photoAdapter = new PhotoAdapter(this, photoList, this);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(photoAdapter);
        
        // 设置RecyclerView的性能优化
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
        
        // 设置RecycledViewPool大小
        RecyclerView.RecycledViewPool recycledViewPool = new RecyclerView.RecycledViewPool();
        recycledViewPool.setMaxRecycledViews(0, 20); // 假设只有一种view type
        recyclerView.setRecycledViewPool(recycledViewPool);
    }
    
    private void loadPhotos() {
        photoList.clear();
        photosByDate.clear();
        
        // 获取应用私有目录和公共图片目录中的照片
        List<File> photoDirs = new ArrayList<>();
        
        // 添加应用私有目录
        StorageManager storageManager = new StorageManager(this);
        File privatePhotoDir = storageManager.getPhotoDirectory();
        if (privatePhotoDir != null && privatePhotoDir.exists()) {
            photoDirs.add(privatePhotoDir);
        }
        
        // 添加公共图片目录
        File publicPhotoDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (publicPhotoDir != null && publicPhotoDir.exists()) {
            photoDirs.add(publicPhotoDir);
        }
        
        // 从所有目录加载照片
        for (File photoDir : photoDirs) {
            if (photoDir != null && photoDir.exists()) {
                File[] photoFiles = photoDir.listFiles();
                if (photoFiles != null) {
                    // 先收集所有照片信息
                    List<PhotoItem> tempPhotoList = new ArrayList<>();
                    for (File file : photoFiles) {
                        if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jpg")) {
                            PhotoItem photoItem = new PhotoItem(file.getAbsolutePath(), file.lastModified());
                            tempPhotoList.add(photoItem);
                            
                            // 按日期分组
                            String dateKey = getDateKey(file.lastModified());
                            if (!photosByDate.containsKey(dateKey)) {
                                photosByDate.put(dateKey, new ArrayList<PhotoItem>());
                            }
                            photosByDate.get(dateKey).add(photoItem);
                        }
                    }
                    
                    // 按拍摄时间倒序排列（最新的在前面）
                    Collections.sort(tempPhotoList, new Comparator<PhotoItem>() {
                        @Override
                        public int compare(PhotoItem p1, PhotoItem p2) {
                            return Long.compare(p2.getTimestamp(), p1.getTimestamp());
                        }
                    });
                    
                    // 批量添加到主列表
                    photoList.addAll(tempPhotoList);
                }
            }
        }
        
        // 去重处理，避免同一张照片在两个目录中都存在
        Set<String> uniquePaths = new HashSet<>();
        List<PhotoItem> uniquePhotos = new ArrayList<>();
        for (PhotoItem photo : photoList) {
            // 使用文件名作为去重依据，避免同一照片在两个目录中重复显示
            String fileName = new File(photo.getPath()).getName();
            if (!uniquePaths.contains(fileName)) {
                uniquePaths.add(fileName);
                uniquePhotos.add(photo);
            }
        }
        photoList.clear();
        photoList.addAll(uniquePhotos);
        
        photoAdapter.notifyDataSetChanged();
        
        // 显示照片数量
        Toast.makeText(this, "找到 " + photoList.size() + " 张照片", Toast.LENGTH_SHORT).show();
    }
    
    // 获取日期键值（用于分组）
    private String getDateKey(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    @Override
    public void onPhotoClick(int position) {
        if (isSelectionMode) {
            // 选择模式下不处理点击事件
            return;
        }
        
        // 点击照片查看大图
        PhotoItem photoItem = photoList.get(position);
        Intent intent = new Intent(this, PhotoViewerActivity.class);
        intent.putExtra("photo_path", photoItem.getPath());
        startActivity(intent);
    }
    
    @Override
    public void onPhotoLongClick(int position) {
        // 长按进入选择模式
        enterSelectionMode();
    }
    
    @Override
    public void onSelectionChanged(int selectedCount) {
        // 更新选择模式状态和菜单
        isSelectionMode = selectedCount > 0;
        if (deleteMenuItem != null) {
            deleteMenuItem.setVisible(isSelectionMode);
            deleteMenuItem.setTitle("删除 (" + selectedCount + ")");
        }
        
        // 更新全选菜单项
        if (selectAllMenuItem != null) {
            selectAllMenuItem.setVisible(isSelectionMode);
            if (photoAdapter.isAllSelected()) {
                selectAllMenuItem.setTitle("取消全选");
            } else {
                selectAllMenuItem.setTitle("全选");
            }
        }
        
        // 更新标题
        if (isSelectionMode) {
            getSupportActionBar().setTitle("选择照片 (" + selectedCount + ")");
        } else {
            getSupportActionBar().setTitle("照片库");
        }
    }
    
    // 进入选择模式
    private void enterSelectionMode() {
        isSelectionMode = true;
        photoAdapter.setSelectionMode(true);
        invalidateOptionsMenu(); // 重新创建菜单
    }
    
    // 退出选择模式
    private void exitSelectionMode() {
        isSelectionMode = false;
        photoAdapter.setSelectionMode(false);
        if (deleteMenuItem != null) {
            deleteMenuItem.setVisible(false);
        }
        if (selectAllMenuItem != null) {
            selectAllMenuItem.setVisible(false);
        }
        getSupportActionBar().setTitle("照片库");
    }
    
    // 显示删除确认对话框
    private void showDeleteConfirmationDialog() {
        int selectedCount = photoAdapter.getSelectedPhotoPaths().size();
        if (selectedCount == 0) return;
        
        new AlertDialog.Builder(this)
                .setTitle("删除照片")
                .setMessage("确定要删除选中的 " + selectedCount + " 张照片吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteSelectedPhotos();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    // 删除选中的照片
    private void deleteSelectedPhotos() {
        photoAdapter.deleteSelectedPhotos();
        Toast.makeText(this, "照片已删除", Toast.LENGTH_SHORT).show();
        exitSelectionMode();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gallery_menu, menu);
        deleteMenuItem = menu.findItem(R.id.action_delete);
        selectAllMenuItem = menu.findItem(R.id.action_select_all);
        deleteMenuItem.setVisible(isSelectionMode);
        selectAllMenuItem.setVisible(isSelectionMode);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (isSelectionMode) {
                    exitSelectionMode();
                } else {
                    onBackPressed();
                }
                return true;
            case R.id.action_select_all:
                // 处理全选/取消全选
                if (photoAdapter.isAllSelected()) {
                    photoAdapter.deselectAll();
                    item.setTitle("全选");
                } else {
                    photoAdapter.selectAll();
                    item.setTitle("取消全选");
                }
                return true;
            case R.id.action_delete:
                showDeleteConfirmationDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 返回时重新加载照片列表
        if (checkStoragePermission()) {
            loadPhotos();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理图片缓存
        if (photoAdapter != null) {
            photoAdapter.clearImageCache();
        }
    }
    
    @Override
    public void onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }
}