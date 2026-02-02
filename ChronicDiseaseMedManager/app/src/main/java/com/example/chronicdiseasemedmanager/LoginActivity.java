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

    private Button btnLogin;
    private TextView tvShowOther, btnSms, btnWechat, tvRegister, tvAdminEntry;
    private LinearLayout layoutOther;
    private EditText etPhone, etPass;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initWidget();
        initRetrofit();
        setupListeners();
    }

    private void initWidget() {
        btnLogin = findViewById(R.id.btnLogin);
        etPhone = findViewById(R.id.etPhone);
        etPass = findViewById(R.id.etPass);
        tvRegister = findViewById(R.id.tvRegister);
        tvShowOther = findViewById(R.id.tvShowOther);
        tvAdminEntry = findViewById(R.id.tvAdminEntry);
        layoutOther = findViewById(R.id.layoutOtherMethods);
        btnSms = findViewById(R.id.btnSmsLogin);
        btnWechat = findViewById(R.id.btnWechatLogin);
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.71.34:8080/") // 修改为你的服务器IP
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void setupListeners() {
        tvShowOther.setOnClickListener(v -> {
            tvShowOther.setVisibility(View.GONE);
            layoutOther.setVisibility(View.VISIBLE);
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        tvAdminEntry.setOnClickListener(v -> {
            Toast.makeText(this, "管理员请访问: http://your-server-ip:8080/admin", Toast.LENGTH_LONG).show();
        });

        btnLogin.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String pass = etPass.getText().toString().trim();

            if (phone.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "请输入手机号和密码", Toast.LENGTH_SHORT).show();
                return;
            }

            // 调用用户登录接口
            Call<LoginResponse> call = apiService.login(phone, pass);
            call.enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse res = response.body();

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
                            // 需要身份绑定
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
                        } else {
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
}