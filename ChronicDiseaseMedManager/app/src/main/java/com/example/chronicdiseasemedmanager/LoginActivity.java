package com.example.chronicdiseasemedmanager;

import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {

    private boolean isUser = true;
    private Button btnUser, btnAdmin, btnLogin;
    private TextView tvShowOther, btnSms, btnWechat, tvRegister;
    private LinearLayout layoutOther;
    private EditText etPhone, etPass;
    private ApiService apiService;

    private final int BLUE_MAIN = 0xFF3B82F6;
    private final int COLOR_TRANS = 0x00000000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initWidget();
        initRetrofit();
        setupListeners();
        updateTheme();
    }

    private void initWidget() {
        btnUser = findViewById(R.id.btnUser);
        btnAdmin = findViewById(R.id.btnAdmin);
        btnLogin = findViewById(R.id.btnLogin);
        etPhone = findViewById(R.id.etPhone);
        etPass = findViewById(R.id.etPass);
        tvRegister = findViewById(R.id.tvRegister);
        tvShowOther = findViewById(R.id.tvShowOther);
        layoutOther = findViewById(R.id.layoutOtherMethods);
        btnSms = findViewById(R.id.btnSmsLogin);
        btnWechat = findViewById(R.id.btnWechatLogin);
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.71.34:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void setupListeners() {
        btnUser.setOnClickListener(v -> { isUser = true; updateTheme(); });
        btnAdmin.setOnClickListener(v -> { isUser = false; updateTheme(); });

        tvShowOther.setOnClickListener(v -> {
            tvShowOther.setVisibility(View.GONE);
            layoutOther.setVisibility(View.VISIBLE);
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        btnLogin.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String pass = etPass.getText().toString().trim();

            if (phone.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show();
                return;
            }

            // 新增：调用新的登录接口
            Call<LoginResponse> call = apiService.login(phone, pass);
            call.enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse res = response.body();

                        // 在登录成功的回调中
                        if (res.code == 200) {
                            // 保存用户信息到SharedPreferences
                            SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
                            SharedPreferences.Editor editor = sp.edit();
                            editor.putString("phone", phone);
                            editor.putString("nickname", res.nickname);
                            editor.putLong("userId", res.userId);
                            editor.putBoolean("isLoggedIn", true);
                            editor.apply();

                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else if (res.code == 201) {
                            // 保存用户ID
                            SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
                            SharedPreferences.Editor editor = sp.edit();
                            editor.putString("phone", phone);
                            editor.putLong("userId", res.userId);
                            editor.putBoolean("isLoggedIn", true);
                            editor.apply();

                            Intent intent = new Intent(LoginActivity.this, IdentityBindActivity.class);
                            intent.putExtra("phone", phone);
                            startActivity(intent);
                            finish();
                        }else {
                            Toast.makeText(LoginActivity.this, res.msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "连接服务器失败", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnSms.setOnClickListener(v -> Toast.makeText(this, "短信登录暂未开放", Toast.LENGTH_SHORT).show());
        btnWechat.setOnClickListener(v -> Toast.makeText(this, "微信登录正在接入", Toast.LENGTH_SHORT).show());
    }

    private void updateTheme() {
        if (isUser) {
            btnUser.setBackgroundColor(BLUE_MAIN);
            btnUser.setTextColor(0xFFFFFFFF);
            btnAdmin.setBackgroundColor(COLOR_TRANS);
            btnAdmin.setTextColor(BLUE_MAIN);
        } else {
            btnAdmin.setBackgroundColor(BLUE_MAIN);
            btnAdmin.setTextColor(0xFFFFFFFF);
            btnUser.setBackgroundColor(COLOR_TRANS);
            btnUser.setTextColor(BLUE_MAIN);
        }
    }
}