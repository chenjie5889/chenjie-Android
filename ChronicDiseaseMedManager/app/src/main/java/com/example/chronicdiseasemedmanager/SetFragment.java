package com.example.chronicdiseasemedmanager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SetFragment extends Fragment {

    private TextView tvNickname, tvPhone, tvVersion;
    private TextView itemProfileInfo, itemAbout, itemAssistant; // 添加itemAssistant
    private Button btnLogout;
    private ApiService apiService;
    private Long currentUserId;
    private View rootView; // 添加根视图引用

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_set, container, false); // 保存根视图

        initViews(rootView);
        initRetrofit();
        loadLatestUserInfo();
        setupListeners();

        return rootView;
    }

    private void initViews(View v) {
        tvNickname = v.findViewById(R.id.tvNickname);
        tvPhone = v.findViewById(R.id.tvPhone);
        tvVersion = v.findViewById(R.id.tvVersion);

        itemProfileInfo = v.findViewById(R.id.itemProfileInfo);
        itemAbout = v.findViewById(R.id.itemAbout);
        itemAssistant = v.findViewById(R.id.itemAssistant); // 直接从传入的View中查找
        btnLogout = v.findViewById(R.id.btnLogout);

        // 获取并显示版本号
        try {
            PackageManager pm = requireContext().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(requireContext().getPackageName(), 0);
            tvVersion.setText("v" + pi.versionName);
        } catch (Exception e) {
            tvVersion.setText("v1.0");
        }
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.238.1:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void loadLatestUserInfo() {
        // 从 SharedPreferences 读取用户信息
        SharedPreferences sp = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);
        String phone = sp.getString("phone", "未登录");
        String nickname = sp.getString("nickname", "");
        String realName = sp.getString("realName", "");

        // 首先显示本地数据
        updateUserInfoUI(nickname, realName, phone);

        // 如果用户已登录，从服务器获取最新数据
        if (currentUserId != -1L) {
            apiService.getUserInfo(currentUserId).enqueue(new Callback<UserInfoResponse>() {
                @Override
                public void onResponse(Call<UserInfoResponse> call, Response<UserInfoResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        UserInfoResponse userInfo = response.body();

                        if (userInfo != null && userInfo.id != null) {
                            // 保存到本地
                            saveUserInfoToLocal(userInfo);
                            // 更新UI
                            updateUserInfoUI(userInfo.nickname, userInfo.realName, userInfo.phone);
                        }
                    }
                }

                @Override
                public void onFailure(Call<UserInfoResponse> call, Throwable t) {
                    // 网络失败时使用本地数据
                    Toast.makeText(requireContext(), "加载最新信息失败", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateUserInfoUI(String nickname, String realName, String phone) {
        // 如果没有昵称，默认显示手机号
        if (TextUtils.isEmpty(nickname)) {
            if (!TextUtils.isEmpty(phone) && !phone.equals("未登录")) {
                nickname = "用户" + phone.substring(phone.length() - 4);
            } else {
                nickname = "用户";
            }
        }

        // 优先显示真实姓名，如果没有则显示昵称
        if (!TextUtils.isEmpty(realName)) {
            tvNickname.setText(realName + " (" + nickname + ")");
        } else {
            tvNickname.setText(nickname);
        }

        // 显示手机号（隐藏中间4位）
        if (!TextUtils.isEmpty(phone) && phone.length() == 11) {
            String maskedPhone = phone.substring(0, 3) + "****" + phone.substring(7);
            tvPhone.setText(maskedPhone);
        } else {
            tvPhone.setText(phone);
        }
    }

    private void saveUserInfoToLocal(UserInfoResponse userInfo) {
        SharedPreferences sp = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        if (!TextUtils.isEmpty(userInfo.nickname)) {
            editor.putString("nickname", userInfo.nickname);
        }

        if (!TextUtils.isEmpty(userInfo.realName)) {
            editor.putString("realName", userInfo.realName);
        }

        if (!TextUtils.isEmpty(userInfo.phone)) {
            editor.putString("phone", userInfo.phone);
        }

        editor.apply();
    }

    private void setupListeners() {
        // 个人信息点击事件 - 跳转到个人信息编辑页面
        itemProfileInfo.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ProfileInfoActivity.class);
            startActivity(intent);
        });

        // 关于我们
        itemAbout.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("关于我们")
                    .setMessage("慢性病用药管理系统\n\n致力于为慢性病患者提供便捷的用药提醒与健康档案管理服务。")
                    .setPositiveButton("确定", null)
                    .show();
        });

        // 智能助手 - 直接使用已初始化的itemAssistant变量
        itemAssistant.setOnClickListener(view -> {
            Intent intent = new Intent(getActivity(), ChatAssistantActivity.class);
            startActivity(intent);
        });

        // 退出登录
        btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("提示")
                .setMessage("确定要退出当前账号吗？")
                .setPositiveButton("确定", (dialog, which) -> performLogout())
                .setNegativeButton("取消", null)
                .show();
    }

    private void performLogout() {
        // 1. 清除 SharedPreferences 中的登录状态
        SharedPreferences sp = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.clear(); // 清除所有数据
        editor.apply();

        // 2. 跳转回登录页面
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        // 清空任务栈，防止用户按返回键回到主页
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // 3. 结束当前 Activity
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}