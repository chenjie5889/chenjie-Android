package com.example.chronicdiseasemedmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ArchiveFragment extends Fragment {
    private View layoutEmpty, layoutContent, containerEdit, containerDetail;
    private EditText etHeight, etWeight, etHistory, etName, etBirthday;
    private TextView viewName, viewGender, viewBirthday, viewBMI, viewHistory, btnEditTop, tvGoFill, btnSave;
    private RadioGroup rgGender;
    private RadioButton rbMale, rbFemale;
    private ApiService apiService;
    private Long currentUserId;
    private boolean hasArchiveData = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_archive, container, false);

        SharedPreferences sp = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);

        initViews(view);
        initRetrofit();

        if (currentUserId != -1L) {
            loadArchive();
        } else {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
        }

        btnEditTop.setOnClickListener(v -> switchMode(true));
        tvGoFill.setOnClickListener(v -> switchMode(true));
        btnSave.setOnClickListener(v -> saveArchive());

        return view;
    }

    private void initViews(View v) {
        layoutEmpty = v.findViewById(R.id.layoutEmpty);
        layoutContent = v.findViewById(R.id.layoutContent);
        containerEdit = v.findViewById(R.id.containerEdit);
        containerDetail = v.findViewById(R.id.containerDetail);

        viewName = v.findViewById(R.id.viewName);
        viewGender = v.findViewById(R.id.viewGender);
        viewBirthday = v.findViewById(R.id.viewBirthday);
        viewBMI = v.findViewById(R.id.viewBMI);
        viewHistory = v.findViewById(R.id.viewHistory);

        etName = v.findViewById(R.id.etName);
        etBirthday = v.findViewById(R.id.etBirthday);
        etHeight = v.findViewById(R.id.etHeight);
        etWeight = v.findViewById(R.id.etWeight);
        etHistory = v.findViewById(R.id.etHistory);

        rgGender = v.findViewById(R.id.rgGender);
        rbMale = v.findViewById(R.id.rbMale);
        rbFemale = v.findViewById(R.id.rbFemale);

        btnEditTop = v.findViewById(R.id.btnEditTop);
        tvGoFill = v.findViewById(R.id.tvGoFill);
        btnSave = v.findViewById(R.id.btnSave);

        // 姓名字段设为不可编辑（从身份证获取）
        etName.setEnabled(false);
        // 生日字段设为可编辑
        etBirthday.setEnabled(true);
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.71.29:8080/") // 请确保IP与后端一致
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void loadArchive() {
        showLoading(true);
        apiService.getArchive(currentUserId).enqueue(new Callback<ArchivePlusResponse>() {
            @Override
            public void onResponse(Call<ArchivePlusResponse> call, Response<ArchivePlusResponse> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    ArchivePlusResponse data = response.body();

                    // 只要有响应，就显示内容布局（可能有数据也可能需要创建）
                    layoutEmpty.setVisibility(View.GONE);
                    layoutContent.setVisibility(View.VISIBLE);

                    hasArchiveData = data.hasArchiveData();

                    // 更新展示UI
                    viewName.setText("姓名：" + (data.realName != null ? data.realName : "未绑定"));

                    if (data.hasArchiveData() && data.archive != null) {
                        // 有档案数据
                        Archive archive = data.archive;

                        // 更新详情视图
                        viewGender.setText("性别：" + (archive.gender != null ? archive.gender : "未设定"));
                        viewBirthday.setText("生日：" + (archive.birthday != null ? archive.birthday : "未设定"));

                        String heightStr = archive.height != null ? String.format("%.1f", archive.height) + "cm" : "未设定";
                        String weightStr = archive.weight != null ? String.format("%.1f", archive.weight) + "kg" : "未设定";
                        viewBMI.setText("体征：" + heightStr + " / " + weightStr);
                        viewHistory.setText(archive.medicalHistory != null ? archive.medicalHistory : "暂无记录");

                        // 预填编辑框
                        etName.setText(data.realName);
                        if ("男".equals(archive.gender)) {
                            rbMale.setChecked(true);
                        } else if ("女".equals(archive.gender)) {
                            rbFemale.setChecked(true);
                        }
                        etBirthday.setText(archive.birthday != null ? archive.birthday : "");
                        etHeight.setText(archive.height != null ? String.format("%.1f", archive.height) : "");
                        etWeight.setText(archive.weight != null ? String.format("%.1f", archive.weight) : "");
                        etHistory.setText(archive.medicalHistory != null ? archive.medicalHistory : "");

                        // 显示编辑按钮
                        btnEditTop.setVisibility(View.VISIBLE);
                    } else {
                        // 无档案数据，显示默认值
                        viewGender.setText("性别：未设定");
                        viewBirthday.setText("生日：未设定");
                        viewBMI.setText("体征：未设定 / 未设定");
                        viewHistory.setText("暂无记录");

                        // 清空编辑框，但保留姓名
                        etName.setText(data.realName != null ? data.realName : "");
                        etBirthday.setText("");
                        etHeight.setText("");
                        etWeight.setText("");
                        etHistory.setText("");

                        // 尝试从身份证信息预填生日和性别
                        tryPreFillFromUserInfo();

                        // 隐藏编辑按钮，直接显示编辑模式
                        btnEditTop.setVisibility(View.GONE);
                        switchMode(true); // 自动进入编辑模式
                    }

                    // 初始显示详情模式（如果有数据）
                    if (hasArchiveData) {
                        switchMode(false);
                    }
                } else {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    layoutContent.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "加载档案失败，请检查网络连接", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ArchivePlusResponse> call, Throwable t) {
                showLoading(false);
                layoutEmpty.setVisibility(View.VISIBLE);
                layoutContent.setVisibility(View.GONE);
                Toast.makeText(getContext(), "网络异常，无法加载档案: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void tryPreFillFromUserInfo() {
        // 这里可以添加从用户信息预填生日和性别的逻辑
        // 例如，如果用户已绑定身份证，可以从身份证提取生日和性别
        Toast.makeText(getContext(), "请填写您的健康信息", Toast.LENGTH_SHORT).show();
    }

    private void saveArchive() {
        // 验证输入
        String birthday = etBirthday.getText().toString().trim();
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();

        // 验证生日格式
        if (TextUtils.isEmpty(birthday)) {
            Toast.makeText(getContext(), "请填写生日", Toast.LENGTH_SHORT).show();
            return;
        }

        // 简单的生日格式验证
        if (!birthday.matches("\\d{4}-\\d{2}-\\d{2}")) {
            Toast.makeText(getContext(), "生日格式错误，应为：YYYY-MM-DD", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(heightStr) || TextUtils.isEmpty(weightStr)) {
            Toast.makeText(getContext(), "请填写身高和体重", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Double height = Double.parseDouble(heightStr);
            Double weight = Double.parseDouble(weightStr);

            if (height <= 0 || height > 300) {
                Toast.makeText(getContext(), "身高必须在0-300cm之间", Toast.LENGTH_SHORT).show();
                return;
            }

            if (weight <= 0 || weight > 300) {
                Toast.makeText(getContext(), "体重必须在0-300kg之间", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取性别
            String gender = "";
            if (rbMale.isChecked()) {
                gender = "男";
            } else if (rbFemale.isChecked()) {
                gender = "女";
            } else {
                Toast.makeText(getContext(), "请选择性别", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取病史
            String medicalHistory = etHistory.getText().toString().trim();

            // 创建Archive对象
            Archive archive = new Archive();
            archive.userId = currentUserId;
            archive.gender = gender;
            archive.birthday = birthday;
            archive.height = height;
            archive.weight = weight;
            archive.medicalHistory = medicalHistory;

            showLoading(true);
            // 调用更新接口
            apiService.updateArchive(archive).enqueue(new Callback<SmsResponse>() {
                @Override
                public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                    showLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        SmsResponse res = response.body();
                        if (res.code == 200) {
                            Toast.makeText(getContext(), "保存成功", Toast.LENGTH_SHORT).show();
                            hasArchiveData = true;
                            switchMode(false);
                            loadArchive(); // 重新加载确保回显
                        } else {
                            Toast.makeText(getContext(), "保存失败: " + res.msg, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "保存失败，服务器响应异常", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<SmsResponse> call, Throwable t) {
                    showLoading(false);
                    Toast.makeText(getContext(), "网络连接失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "身高和体重必须是数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void switchMode(boolean isEditing) {
        containerEdit.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        containerDetail.setVisibility(isEditing ? View.GONE : View.VISIBLE);
        btnEditTop.setVisibility(isEditing ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (show) {
            layoutContent.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            tvGoFill.setText("加载中...");
            tvGoFill.setEnabled(false);
        } else {
            tvGoFill.setText("立即创建档案");
            tvGoFill.setEnabled(true);
        }
    }
}