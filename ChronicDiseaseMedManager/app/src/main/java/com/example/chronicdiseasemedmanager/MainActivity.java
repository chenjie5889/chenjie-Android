package com.example.chronicdiseasemedmanager;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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