package com.example.kirito.imageloaderthread;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Permission;
import com.yanzhenjie.permission.PermissionListener;
import com.yanzhenjie.permission.Rationale;
import com.yanzhenjie.permission.RationaleListener;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private GridView gv;
    private ProgressDialog pd;
    private HashSet<String> mDirpath = new HashSet<>();
    private int mPic_size;
    private File mFileDir;
    private List<String> mfiles;

    private static final String TAG = "MainActivity";
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            pd.dismiss();

            mfiles = Arrays.asList(mFileDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    if (s.endsWith("jpg")){
                        return true;
                    }
                    return false;
                }
            }));
            MyAdapter adapter = new MyAdapter(getApplicationContext(),mfiles,mFileDir.getAbsolutePath());
            gv.setAdapter(adapter);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gv = findViewById(R.id.gv);
        getImages();
    }

    private void getImages() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            Toast.makeText(this, "暂无外部储存", Toast.LENGTH_SHORT).show();
            return;
        }
        pd = ProgressDialog.show(this,null,"加载中...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Uri imgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver cr = MainActivity.this.getContentResolver();

                Cursor cursor = cr.query(imgUri,null,MediaStore.Images.Media.MIME_TYPE + "=? or "
                +MediaStore.Images.Media.MIME_TYPE + "=?",new String[]{"image/jpeg","image/png"},
                        MediaStore.Images.Media.DATE_MODIFIED);

                while (cursor.moveToNext()){
                    String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                    File pa_file = new File(path).getParentFile();
                    String parfile_path = pa_file.getAbsolutePath();

                    if (mDirpath.contains(parfile_path)){//使用一个hashset，过滤已经扫描过的文件夹，节省时间
                        continue;
                    }else {
                        mDirpath.add(parfile_path);
                    }

                    int picSize = pa_file.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File file, String s) {
                            return s.endsWith(".jpg");
                        }
                    }).length;

                    if (picSize > mPic_size){//选取包含最多图片的文件夹
                        mPic_size = picSize;
                        mFileDir = pa_file;
                    }
                }
                cursor.close();
                mDirpath = null;
                mHandler.sendEmptyMessage(0x001);
            }
        }).start();
    }

}
