package com.example.chronicdiseasemedmanager;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ProfileInfoActivity extends AppCompatActivity {

    private ImageView btnBack;
    private EditText etNickname, etRealName, etIdCard, etPhone;
    private Button btnSave, btnChangePassword, btnChangePhone;
    private ApiService apiService;
    private Long currentUserId;
    private String currentPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_info);

        // 获取用户信息
        SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);
        currentPhone = sp.getString("phone", "");

        initViews();
        initRetrofit();

        // 从数据库加载最新数据，然后显示
        loadLatestUserInfo();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etNickname = findViewById(R.id.etNickname);
        etRealName = findViewById(R.id.etRealName);
        etIdCard = findViewById(R.id.etIdCard);
        etPhone = findViewById(R.id.etPhone);

        btnSave = findViewById(R.id.btnSaveProfile);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnChangePhone = findViewById(R.id.btnChangePhone);
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.137.1:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    /**
     * 从数据库加载最新的用户信息
     */
    private void loadLatestUserInfo() {
        // 首先显示本地缓存
        showLocalUserInfo();

        // 如果用户已登录，从服务器获取最新数据
        if (currentUserId != -1L) {
            apiService.getUserInfo(currentUserId).enqueue(new Callback<UserInfoResponse>() {
                @Override
                public void onResponse(Call<UserInfoResponse> call, Response<UserInfoResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        UserInfoResponse userInfo = response.body();

                        if (userInfo != null && userInfo.id != null) {
                            // 更新本地存储
                            saveUserInfoToLocal(userInfo);
                            // 更新UI显示
                            updateUserInfoUI(userInfo);
                        }
                    }
                }

                @Override
                public void onFailure(Call<UserInfoResponse> call, Throwable t) {
                    // 网络失败时仍显示本地数据
                    Toast.makeText(ProfileInfoActivity.this,
                            "加载最新信息失败，显示本地数据", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * 显示本地存储的用户信息
     */
    private void showLocalUserInfo() {
        SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);

        String nickname = sp.getString("nickname", "");
        String realName = sp.getString("realName", "");
        String idCard = sp.getString("idCard", "");
        String phone = sp.getString("phone", "");

        etNickname.setText(nickname);
        etRealName.setText(realName);
        etIdCard.setText(idCard);
        etPhone.setText(phone);

        // 如果昵称为空，显示默认昵称
        if (TextUtils.isEmpty(nickname) && !TextUtils.isEmpty(phone) && phone.length() >= 4) {
            etNickname.setHint("用户" + phone.substring(phone.length() - 4));
        }
    }

    /**
     * 更新UI显示用户信息
     */
    private void updateUserInfoUI(UserInfoResponse userInfo) {
        if (userInfo == null) return;

        // 设置昵称
        if (!TextUtils.isEmpty(userInfo.nickname)) {
            etNickname.setText(userInfo.nickname);
        }

        // 设置真实姓名
        if (!TextUtils.isEmpty(userInfo.realName)) {
            etRealName.setText(userInfo.realName);
        }

        // 设置身份证号
        if (!TextUtils.isEmpty(userInfo.idCard)) {
            etIdCard.setText(userInfo.idCard);
        }

        // 设置手机号
        if (!TextUtils.isEmpty(userInfo.phone)) {
            etPhone.setText(userInfo.phone);
            currentPhone = userInfo.phone;
        }
    }

    /**
     * 将用户信息保存到本地
     */
    private void saveUserInfoToLocal(UserInfoResponse userInfo) {
        SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        if (!TextUtils.isEmpty(userInfo.nickname)) {
            editor.putString("nickname", userInfo.nickname);
        }

        if (!TextUtils.isEmpty(userInfo.realName)) {
            editor.putString("realName", userInfo.realName);
        }

        if (!TextUtils.isEmpty(userInfo.idCard)) {
            editor.putString("idCard", userInfo.idCard);
        }

        if (!TextUtils.isEmpty(userInfo.phone)) {
            editor.putString("phone", userInfo.phone);
        }

        editor.apply();
    }

    private void setupListeners() {
        // 返回按钮
        btnBack.setOnClickListener(v -> finish());

        // 保存个人信息
        btnSave.setOnClickListener(v -> saveProfileInfo());

        // 修改密码（弹窗模式）
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // 修改手机号
        btnChangePhone.setOnClickListener(v -> showChangePhoneDialog());
    }

    /**
     * 显示修改密码弹窗
     */
    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改密码");

        // 创建对话框内容
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        builder.setView(dialogView);

        EditText etOldPassword = dialogView.findViewById(R.id.etOldPassword);
        EditText etNewPassword = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirmPassword = dialogView.findViewById(R.id.etConfirmPassword);

        AlertDialog dialog = builder.create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "确认修改", (dialogInterface, which) -> {
            String oldPassword = etOldPassword.getText().toString().trim();
            String newPassword = etNewPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            // 简化验证：只验证基本规则
            if (TextUtils.isEmpty(oldPassword)) {
                Toast.makeText(this, "请输入原密码", Toast.LENGTH_SHORT).show();
                return;
            }

            if (TextUtils.isEmpty(newPassword)) {
                Toast.makeText(this, "请输入新密码", Toast.LENGTH_SHORT).show();
                return;
            }

            // 简化的密码规则：至少1位字符
            if (newPassword.length() < 1) {
                Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            if (oldPassword.equals(newPassword)) {
                Toast.makeText(this, "新密码不能与原密码相同", Toast.LENGTH_SHORT).show();
                return;
            }

            // 执行修改密码
            changePassword(oldPassword, newPassword);
            dialog.dismiss();
        });

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (dialogInterface, which) -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 执行修改密码操作
     */
    private void changePassword(String oldPassword, String newPassword) {
        // 显示加载状态
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setMessage("正在修改密码...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        apiService.changePassword(currentUserId, oldPassword, newPassword)
                .enqueue(new Callback<SmsResponse>() {
                    @Override
                    public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                        loadingDialog.dismiss();

                        if (response.isSuccessful() && response.body() != null) {
                            SmsResponse res = response.body();
                            if (res.code == 200) {
                                Toast.makeText(ProfileInfoActivity.this, "密码修改成功", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ProfileInfoActivity.this, "密码修改失败: " + res.msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(ProfileInfoActivity.this, "服务器响应异常", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<SmsResponse> call, Throwable t) {
                        loadingDialog.dismiss();
                        Toast.makeText(ProfileInfoActivity.this, "网络连接失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveProfileInfo() {
        String nickname = etNickname.getText().toString().trim();
        String realName = etRealName.getText().toString().trim();
        String idCard = etIdCard.getText().toString().trim();

        // 验证输入
        if (TextUtils.isEmpty(nickname)) {
            if (!TextUtils.isEmpty(currentPhone) && currentPhone.length() >= 4) {
                nickname = "用户" + currentPhone.substring(currentPhone.length() - 4);
            } else {
                nickname = "用户";
            }
        }

        if (!TextUtils.isEmpty(idCard) && idCard.length() != 18) {
            Toast.makeText(this, "身份证号必须为18位", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!TextUtils.isEmpty(realName) && realName.length() < 2) {
            Toast.makeText(this, "真实姓名至少2个字符", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示加载中
        btnSave.setEnabled(false);
        btnSave.setText("保存中...");

        saveToDatabase(nickname, realName, idCard);
    }

    /**
     * 简化的密码验证规则（与注册时一致）
     * 任何输入的字符都可以作为密码，只要不为空
     */
    private boolean isPasswordValid(String password) {
        // 最简单的规则：不能为空
        return !TextUtils.isEmpty(password) && password.length() >= 1;
    }

    private void showChangePhoneDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改手机号");

        // 创建对话框内容
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_phone, null);
        builder.setView(dialogView);

        EditText etNewPhone = dialogView.findViewById(R.id.etNewPhone);
        EditText etVerifyCode = dialogView.findViewById(R.id.etVerifyCode);
        Button btnSendCode = dialogView.findViewById(R.id.btnSendCode);
        TextView tvCountdown = dialogView.findViewById(R.id.tvCountdown);

        AlertDialog dialog = builder.create();

        // 发送验证码
        btnSendCode.setOnClickListener(v -> {
            String newPhone = etNewPhone.getText().toString().trim();

            if (!newPhone.matches("^1[3-9]\\d{9}$")) {
                Toast.makeText(this, "请输入正确的手机号", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPhone.equals(currentPhone)) {
                Toast.makeText(this, "新手机号不能与原手机号相同", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSendCode.setEnabled(false);
            tvCountdown.setVisibility(View.VISIBLE);

            apiService.sendChangePhoneCode(newPhone)
                    .enqueue(new Callback<SmsResponse>() {
                        @Override
                        public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                SmsResponse res = response.body();
                                if (res.code == 200) {
                                    startCountdownTimer(btnSendCode, tvCountdown);
                                    Toast.makeText(ProfileInfoActivity.this, "验证码已发送: " + res.data, Toast.LENGTH_LONG).show();
                                } else {
                                    btnSendCode.setEnabled(true);
                                    tvCountdown.setVisibility(View.GONE);
                                    Toast.makeText(ProfileInfoActivity.this, "发送失败: " + res.msg, Toast.LENGTH_SHORT).show();
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<SmsResponse> call, Throwable t) {
                            btnSendCode.setEnabled(true);
                            tvCountdown.setVisibility(View.GONE);
                            Toast.makeText(ProfileInfoActivity.this, "网络连接失败", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // 确认修改
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "确认修改", (dialogInterface, which) -> {
            String newPhone = etNewPhone.getText().toString().trim();
            String code = etVerifyCode.getText().toString().trim();

            if (TextUtils.isEmpty(newPhone) || TextUtils.isEmpty(code)) {
                Toast.makeText(this, "请输入手机号和验证码", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPhone.matches("^1[3-9]\\d{9}$")) {
                Toast.makeText(this, "手机号格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }

            apiService.changePhone(currentUserId, currentPhone, newPhone, code)
                    .enqueue(new Callback<SmsResponse>() {
                        @Override
                        public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                SmsResponse res = response.body();
                                if (res.code == 200) {
                                    Toast.makeText(ProfileInfoActivity.this, "手机号修改成功", Toast.LENGTH_SHORT).show();

                                    // 更新本地存储
                                    SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
                                    SharedPreferences.Editor editor = sp.edit();
                                    editor.putString("phone", newPhone);
                                    editor.apply();

                                    // 更新显示
                                    currentPhone = newPhone;
                                    etPhone.setText(newPhone);

                                    dialog.dismiss();
                                } else {
                                    Toast.makeText(ProfileInfoActivity.this, "修改失败: " + res.msg, Toast.LENGTH_SHORT).show();
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<SmsResponse> call, Throwable t) {
                            Toast.makeText(ProfileInfoActivity.this, "网络连接失败", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (dialogInterface, which) -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    private void startCountdownTimer(final Button btn, final TextView tv) {
        new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tv.setText((millisUntilFinished / 1000) + "秒后重新发送");
            }

            @Override
            public void onFinish() {
                btn.setEnabled(true);
                tv.setVisibility(View.GONE);
                tv.setText("");
            }
        }.start();
    }

    private void saveToDatabase(String nickname, String realName, String idCard) {
        UpdateUserInfoRequest request = new UpdateUserInfoRequest();
        request.userId = currentUserId;
        request.nickname = nickname;
        request.realName = realName;
        request.idCard = idCard;

        apiService.updateUserInfo(request)
                .enqueue(new Callback<SmsResponse>() {
                    @Override
                    public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                        btnSave.setEnabled(true);
                        btnSave.setText("保存个人信息");

                        if (response.isSuccessful() && response.body() != null) {
                            SmsResponse res = response.body();

                            if (res.code == 200) {
                                saveToLocalPreferences(nickname, realName, idCard);
                                Toast.makeText(ProfileInfoActivity.this, "个人信息保存成功", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ProfileInfoActivity.this, "保存失败: " + res.msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            saveToLocalPreferences(nickname, realName, idCard);
                            Toast.makeText(ProfileInfoActivity.this, "服务器响应异常，已保存到本地", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<SmsResponse> call, Throwable t) {
                        btnSave.setEnabled(true);
                        btnSave.setText("保存个人信息");
                        saveToLocalPreferences(nickname, realName, idCard);
                        Toast.makeText(ProfileInfoActivity.this, "网络连接失败，已保存到本地", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveToLocalPreferences(String nickname, String realName, String idCard) {
        SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("nickname", nickname);
        editor.putString("realName", realName);
        editor.putString("idCard", idCard);
        editor.apply();
    }
}