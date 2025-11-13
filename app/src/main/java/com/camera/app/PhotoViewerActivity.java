package com.pipiqiang.qcamera.app;
import com.pipiqiang.qcamera.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class PhotoViewerActivity extends AppCompatActivity {
    
    private ImageView imageView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);
        
        // 启用返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("查看照片");
        }
        
        initViews();
        loadPhoto();
    }
    
    private void initViews() {
        imageView = findViewById(R.id.image_view);
    }
    
    private void loadPhoto() {
        String photoPath = getIntent().getStringExtra("photo_path");
        if (photoPath != null && !photoPath.isEmpty()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                // 加载原图
                Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.photo_viewer_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_upload_single) {
            String path = getIntent().getStringExtra("photo_path");
            if (path != null && !path.isEmpty()) {
                android.widget.Toast.makeText(this, "开始上传", android.widget.Toast.LENGTH_SHORT).show();
                new android.os.AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... voids) {
                        try {
                            SettingsManager sm = new SettingsManager(PhotoViewerActivity.this);
                            CloudUploadHelper.upload(PhotoViewerActivity.this, sm, path);
                            return true;
                        } catch (Exception e) {
                            android.util.Log.e("PhotoViewer", "上传失败", e);
                            return false;
                        }
                    }

                    @Override
                    protected void onPostExecute(Boolean ok) {
                        android.widget.Toast.makeText(PhotoViewerActivity.this, ok ? "上传成功" : "上传失败", android.widget.Toast.LENGTH_LONG).show();
                        // 如果删除了源文件，尝试关闭页面
                        if (ok) {
                            File f = new File(path);
                            if (!f.exists()) {
                                finish();
                            }
                        }
                    }
                }.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}