package com.example.chronicdiseasemedmanager;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RegisterActivity extends AppCompatActivity {

    private EditText etPhone, etCode, etPass;
    private Button btnGetCode, btnRegister;
    private TextView tvBackLogin;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initWidget();
        initRetrofit();
        setupListeners();
    }

    private void initWidget() {
        etPhone = findViewById(R.id.etRegPhone);
        etCode = findViewById(R.id.etSmsCode);
        etPass = findViewById(R.id.etRegPass);
        btnGetCode = findViewById(R.id.btnGetSms);
        btnRegister = findViewById(R.id.btnDoRegister);
        tvBackLogin = findViewById(R.id.tvBackLogin);
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.238.1:8080/") // 请确保与后端IP一致
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void setupListeners() {
        tvBackLogin.setOnClickListener(v -> finish());
        btnGetCode.setOnClickListener(v -> sendSms());
        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void sendSms() {
        String phone = etPhone.getText().toString().trim();
        if (phone.length() != 11) {
            showSimpleDialog("提示", "请输入正确的11位手机号");
            return;
        }

        apiService.sendSms(phone).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // 模拟短信显示
                    showSimpleDialog("验证码已发送", "模拟验证码为：" + response.body().data);
                    startTimer();
                }
            }
            @Override public void onFailure(Call<SmsResponse> call, Throwable t) {
                showSimpleDialog("错误", "无法连接服务器");
            }
        });
    }

    private void doRegister() {
        String phone = etPhone.getText().toString().trim();
        String code = etCode.getText().toString().trim();
        String password = etPass.getText().toString().trim();

        // 前端格式预校验（不要去掉）
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            showErrorDialog("注册失败", "手机号格式不正确");
            return;
        }

        apiService.register(phone, code, password).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                // 只要服务器有返回（哪怕是 400 或 500）
                if (response.body() != null) {
                    SmsResponse res = response.body();
                    if (res.code == 200) {
                        Toast.makeText(RegisterActivity.this, "注册成功", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        // 【核心改进】直接显示后端返回的 msg 字符串
                        // 这样如果是重复注册，就会显示“该手机号已注册...”
                        // 如果是验证码错，就会显示“验证码错误...”
                        showErrorDialog("注册失败", res.msg);
                    }
                } else {
                    // 处理 404 等 body 为空的情况
                    showErrorDialog("注册失败", "服务器响应异常，错误码：" + response.code());
                }
            }

            @Override
            public void onFailure(Call<SmsResponse> call, Throwable t) {
                showErrorDialog("连接失败", "请检查网络或后端服务是否开启");
            }
        });
    }

    // 统一的弹窗提示方法
    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("我知道了", null)
                .show();
    }

    private void showSimpleDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void startTimer() {
        new CountDownTimer(60000, 1000) {
            @Override public void onTick(long l) {
                btnGetCode.setEnabled(false);
                btnGetCode.setText((l / 1000) + "秒");
            }
            @Override public void onFinish() {
                btnGetCode.setEnabled(true);
                btnGetCode.setText("获取验证码");
            }
        }.start();
    }
}