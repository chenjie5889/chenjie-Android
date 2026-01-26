package com.example.chronicdiseasemedmanager;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {

                requestPermissionLauncher = registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> {
                            if (!isGranted) {
                                Toast.makeText(this, "需要通知权限才能接收用药提醒", Toast.LENGTH_LONG).show();
                            }
                        }
                );
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        BottomNavigationView nav = findViewById(R.id.bottom_nav);

        // 初始页面
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_container, new HomeFragment()).commit();
        }

        nav.setOnItemSelectedListener(item -> {
            Fragment frag = null;
            int id = item.getItemId();
            if (id == R.id.menu_home) frag = new HomeFragment();
            else if (id == R.id.menu_archive) frag = new ArchiveFragment();
            else if (id == R.id.menu_med) frag = new MedFragment();
            else if (id == R.id.menu_family) frag = new FamilyFragment();
            else if (id == R.id.menu_set) frag = new SetFragment();

            if (frag != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_container, frag).commit();
                return true;
            }
            return false;
        });
    }
}