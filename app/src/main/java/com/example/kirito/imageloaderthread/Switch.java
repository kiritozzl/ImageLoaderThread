package com.example.kirito.imageloaderthread;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Permission;
import com.yanzhenjie.permission.PermissionListener;
import com.yanzhenjie.permission.Rationale;
import com.yanzhenjie.permission.RationaleListener;

import java.util.List;

/**
 * Created by kirito on 2018.01.10.
 */

public class Switch extends AppCompatActivity {
    private Button btn;
    private PermissionListener listener;
    private static final String TAG = "Switch";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_switch);

        btn = findViewById(R.id.button);
        listener = new PermissionListener() {
            @Override
            public void onSucceed(int requestCode, @NonNull List<String> grantPermissions) {
                Intent intent = new Intent(Switch.this,MainActivity.class);
                startActivity(intent);
            }

            @Override
            public void onFailed(int requestCode, @NonNull List<String> deniedPermissions) {
            }
        };

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //运行时权限请求，使用了开源API
                AndPermission.with(getApplicationContext())
                        .requestCode(100)
                        .permission(Permission.STORAGE)
                        .callback(listener)
                        .rationale(new RationaleListener() {
                            @Override
                            public void showRequestPermissionRationale(int requestCode, Rationale rationale) {
                                AndPermission.rationaleDialog(Switch.this, rationale).show();
                            }
                        })
                        .start();
            }
        });
    }
}
