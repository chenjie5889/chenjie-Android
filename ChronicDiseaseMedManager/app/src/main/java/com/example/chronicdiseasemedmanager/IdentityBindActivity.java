package com.example.chronicdiseasemedmanager;

import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class IdentityBindActivity extends AppCompatActivity {
    private EditText etName, etIdCard;
    private Button btnSubmit;
    private String phone;
    private Long userId;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identity_bind);

        // 获取用户信息
        SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
        phone = sp.getString("phone", "");
        userId = sp.getLong("userId", -1L);

        etName = findViewById(R.id.etRealName);
        etIdCard = findViewById(R.id.etIdCard);
        btnSubmit = findViewById(R.id.btnSubmitBind);

        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.71.29:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);

        btnSubmit.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String idCard = etIdCard.getText().toString().trim();

            if (TextUtils.isEmpty(name) || idCard.length() != 18) {
                Toast.makeText(this, "请输入真实姓名及18位身份证号", Toast.LENGTH_SHORT).show();
                return;
            }

            apiService.bindIdentity(phone, name, idCard).enqueue(new Callback<SmsResponse>() {
                @Override
                public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                        Toast.makeText(IdentityBindActivity.this, "信息绑定成功", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(IdentityBindActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(IdentityBindActivity.this, "绑定失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<SmsResponse> call, Throwable t) {
                    Toast.makeText(IdentityBindActivity.this, "网络连接失败", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}