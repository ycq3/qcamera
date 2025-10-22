package com.camera.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PhotoGalleryActivity extends AppCompatActivity implements PhotoAdapter.OnPhotoClickListener {
    
    private static final int PERMISSION_REQUEST_CODE = 101;
    
    private RecyclerView recyclerView;
    private PhotoAdapter photoAdapter;
    private List<PhotoItem> photoList;
    
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
        photoAdapter = new PhotoAdapter(this, photoList, this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setAdapter(photoAdapter);
    }
    
    private void loadPhotos() {
        photoList.clear();
        
        StorageManager storageManager = new StorageManager(this);
        File photoDir = storageManager.getPhotoDirectory();
        
        if (photoDir != null && photoDir.exists()) {
            File[] photoFiles = photoDir.listFiles();
            if (photoFiles != null) {
                for (File file : photoFiles) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".jpg")) {
                        photoList.add(new PhotoItem(file.getAbsolutePath(), file.lastModified()));
                    }
                }
            }
            
            // 按拍摄时间倒序排列（最新的在前面）
            Collections.sort(photoList, new Comparator<PhotoItem>() {
                @Override
                public int compare(PhotoItem p1, PhotoItem p2) {
                    return Long.compare(p2.getTimestamp(), p1.getTimestamp());
                }
            });
        }
        
        photoAdapter.notifyDataSetChanged();
        
        // 显示照片数量
        Toast.makeText(this, "找到 " + photoList.size() + " 张照片", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onPhotoClick(int position) {
        // 点击照片查看大图
        PhotoItem photoItem = photoList.get(position);
        Intent intent = new Intent(this, PhotoViewerActivity.class);
        intent.putExtra("photo_path", photoItem.getPath());
        startActivity(intent);
    }
    
    @Override
    public void onPhotoLongClick(int position) {
        // 长按照片显示删除选项
        showDeleteDialog(position);
    }
    
    private void showDeleteDialog(final int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除照片")
                .setMessage("确定要删除这张照片吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deletePhoto(position);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private void deletePhoto(int position) {
        if (position >= 0 && position < photoList.size()) {
            PhotoItem photoItem = photoList.get(position);
            File photoFile = new File(photoItem.getPath());
            
            if (photoFile.exists() && photoFile.delete()) {
                photoList.remove(position);
                photoAdapter.notifyItemRemoved(position);
                Toast.makeText(this, "照片已删除", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 返回时重新加载照片列表
        if (checkStoragePermission()) {
            loadPhotos();
        }
    }
}