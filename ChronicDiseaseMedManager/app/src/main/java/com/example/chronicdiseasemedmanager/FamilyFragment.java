package com.example.chronicdiseasemedmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.*;

public class FamilyFragment extends Fragment {

    private static final String TAG = "FamilyFragment";

    private ApiService apiService;
    private Long currentUserId;
    private String currentUserPhone;

    // 视图组件
    private LinearLayout layoutPendingRequests;
    private LinearLayout layoutFamilyList;
    private Button btnAddFamily;
    private TextView tvNoPending, tvNoFamily;
    private LinearLayout containerPendingList;
    private LinearLayout containerFamilyList;
    private ProgressBar progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_family, container, false);

        // 获取用户信息
        SharedPreferences sp = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);
        currentUserPhone = sp.getString("phone", "");

        initViews(view);
        initRetrofit();

        if (currentUserId != -1L) {
            loadData();
        } else {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void initViews(View v) {
        // 获取正确类型的视图
        progressBar = v.findViewById(R.id.progressBar);
        btnAddFamily = v.findViewById(R.id.btnAddFamily);
        tvNoPending = v.findViewById(R.id.tvNoPending);
        tvNoFamily = v.findViewById(R.id.tvNoFamily);
        containerPendingList = v.findViewById(R.id.containerPendingList);
        containerFamilyList = v.findViewById(R.id.containerFamilyList);

        // 修复：移除类型声明，直接赋值给成员变量
        layoutPendingRequests = v.findViewById(R.id.layoutPendingRequests);
        layoutFamilyList = v.findViewById(R.id.layoutFamilyList);

        btnAddFamily.setOnClickListener(view -> showAddFamilyDialog());

        showLoading(false); // 初始状态不显示加载
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.137.1:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void loadData() {
        showLoading(true);

        // 同时加载待处理请求和家属列表
        loadPendingRequests();
        loadFamilyList();
    }

    private void loadPendingRequests() {
        apiService.getPendingRequests(currentUserId).enqueue(new Callback<List<FamilyRequestResponse>>() {
            @Override
            public void onResponse(Call<List<FamilyRequestResponse>> call, Response<List<FamilyRequestResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<FamilyRequestResponse> requests = response.body();
                    updatePendingRequestsUI(requests);
                } else {
                    Log.e(TAG, "加载待处理请求失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<FamilyRequestResponse>> call, Throwable t) {
                Log.e(TAG, "加载待处理请求失败: " + t.getMessage());
            }
        });
    }

    private void loadFamilyList() {
        apiService.getApprovedFamily(currentUserId).enqueue(new Callback<List<FamilyMemberResponse>>() {
            @Override
            public void onResponse(Call<List<FamilyMemberResponse>> call, Response<List<FamilyMemberResponse>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<FamilyMemberResponse> familyList = response.body();
                    updateFamilyListUI(familyList);
                } else {
                    Log.e(TAG, "加载家属列表失败: " + response.code());
                    tvNoFamily.setVisibility(View.VISIBLE);
                    containerFamilyList.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Call<List<FamilyMemberResponse>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "加载家属列表失败: " + t.getMessage());
                tvNoFamily.setVisibility(View.VISIBLE);
                containerFamilyList.setVisibility(View.GONE);
            }
        });
    }

    private void updatePendingRequestsUI(List<FamilyRequestResponse> requests) {
        containerPendingList.removeAllViews();

        if (requests == null || requests.isEmpty()) {
            tvNoPending.setVisibility(View.VISIBLE);
            containerPendingList.setVisibility(View.GONE);
            return;
        }

        tvNoPending.setVisibility(View.GONE);
        containerPendingList.setVisibility(View.VISIBLE);

        for (FamilyRequestResponse request : requests) {
            View requestCard = createRequestCard(request);
            containerPendingList.addView(requestCard);
        }
    }

    private View createRequestCard(FamilyRequestResponse request) {
        // 创建请求卡片
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 16, 16, 16);
        card.setBackgroundResource(R.drawable.med_card_bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 12);
        card.setLayoutParams(params);

        // 请求信息
        TextView tvRequesterInfo = new TextView(getContext());
        tvRequesterInfo.setText(request.requesterName + " (" + request.requesterPhone + ")");
        tvRequesterInfo.setTextSize(16);
        tvRequesterInfo.setTextColor(0xFF1F2937);
        tvRequesterInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvRequesterInfo);

        // 关系
        TextView tvRelationship = new TextView(getContext());
        tvRelationship.setText("关系: " + request.relationship);
        tvRelationship.setTextSize(14);
        tvRelationship.setTextColor(0xFF4B5563);
        tvRelationship.setPadding(0, 4, 0, 4);
        card.addView(tvRelationship);

        // 请求权限
        TextView tvPermissions = new TextView(getContext());
        StringBuilder permissionsText = new StringBuilder("请求查看: ");
        if (request.requestedPermissions != null) {
            for (String perm : request.requestedPermissions) {
                switch (perm) {
                    case "ARCHIVE": permissionsText.append("档案 "); break;
                    case "DISEASE": permissionsText.append("疾病 "); break;
                    case "MEDICATION": permissionsText.append("用药 "); break;
                }
            }
        }
        tvPermissions.setText(permissionsText.toString());
        tvPermissions.setTextSize(14);
        tvPermissions.setTextColor(0xFF6B7280);
        tvPermissions.setPadding(0, 4, 0, 12);
        card.addView(tvPermissions);

        // 按钮布局
        LinearLayout buttonLayout = new LinearLayout(getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

        Button btnAgree = new Button(getContext());
        btnAgree.setText("同意");
        btnAgree.setBackgroundColor(0xFF10B981);
        btnAgree.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(0, 0, 8, 0);
        btnAgree.setLayoutParams(btnParams);
        btnAgree.setOnClickListener(v -> handleRequest(request.id, true));

        Button btnReject = new Button(getContext());
        btnReject.setText("拒绝");
        btnReject.setBackgroundColor(0xFFEF4444);
        btnReject.setTextColor(0xFFFFFFFF);
        btnReject.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        btnReject.setOnClickListener(v -> handleRequest(request.id, false));

        buttonLayout.addView(btnAgree);
        buttonLayout.addView(btnReject);
        card.addView(buttonLayout);

        return card;
    }

    private void handleRequest(Long relationshipId, boolean agree) {
        HandleRequest request = new HandleRequest();
        request.userId = currentUserId;
        request.relationshipId = relationshipId;
        request.agree = agree;

        apiService.handleFamilyRequest(request).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SmsResponse res = response.body();
                    Toast.makeText(getContext(), res.msg, Toast.LENGTH_SHORT).show();
                    loadData(); // 重新加载数据
                } else {
                    Toast.makeText(getContext(), "处理失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SmsResponse> call, Throwable t) {
                Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateFamilyListUI(List<FamilyMemberResponse> familyList) {
        containerFamilyList.removeAllViews();

        if (familyList == null || familyList.isEmpty()) {
            tvNoFamily.setVisibility(View.VISIBLE);
            containerFamilyList.setVisibility(View.GONE);
            return;
        }

        tvNoFamily.setVisibility(View.GONE);
        containerFamilyList.setVisibility(View.VISIBLE);

        for (FamilyMemberResponse family : familyList) {
            View familyCard = createFamilyCard(family);
            containerFamilyList.addView(familyCard);
        }
    }

    private View createFamilyCard(FamilyMemberResponse family) {
        // 创建家属卡片
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 16, 16, 16);
        card.setBackgroundResource(R.drawable.med_card_bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 12);
        card.setLayoutParams(params);

        // 家属信息
        TextView tvFamilyInfo = new TextView(getContext());
        tvFamilyInfo.setText(family.familyName + " (" + family.familyPhone + ")");
        tvFamilyInfo.setTextSize(16);
        tvFamilyInfo.setTextColor(0xFF1F2937);
        tvFamilyInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvFamilyInfo);

        // 关系
        TextView tvRelationship = new TextView(getContext());
        tvRelationship.setText("关系: " + family.relationship);
        tvRelationship.setTextSize(14);
        tvRelationship.setTextColor(0xFF4B5563);
        tvRelationship.setPadding(0, 4, 0, 4);
        card.addView(tvRelationship);

        // 权限信息
        TextView tvPermissions = new TextView(getContext());
        StringBuilder permissionsText = new StringBuilder("可查看: ");
        if (family.permissions != null) {
            for (Map.Entry<String, Boolean> entry : family.permissions.entrySet()) {
                if (entry.getValue()) {
                    switch (entry.getKey()) {
                        case "ARCHIVE": permissionsText.append("档案 "); break;
                        case "DISEASE": permissionsText.append("疾病 "); break;
                        case "MEDICATION": permissionsText.append("用药 "); break;
                    }
                }
            }
        }
        tvPermissions.setText(permissionsText.toString());
        tvPermissions.setTextSize(14);
        tvPermissions.setTextColor(0xFF6B7280);
        tvPermissions.setPadding(0, 4, 0, 12);
        card.addView(tvPermissions);

        // 按钮布局
        LinearLayout buttonLayout = new LinearLayout(getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

        Button btnViewData = new Button(getContext());
        btnViewData.setText("查看数据");
        btnViewData.setBackgroundColor(0xFF3B82F6);
        btnViewData.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(0, 0, 8, 0);
        btnViewData.setLayoutParams(btnParams);
        btnViewData.setOnClickListener(v -> viewFamilyData(family));

        Button btnRemove = new Button(getContext());
        btnRemove.setText("移除");
        btnRemove.setBackgroundColor(0xFFEF4444);
        btnRemove.setTextColor(0xFFFFFFFF);
        btnRemove.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        btnRemove.setOnClickListener(v -> removeFamily(family));

        buttonLayout.addView(btnViewData);
        buttonLayout.addView(btnRemove);
        card.addView(buttonLayout);

        return card;
    }

    private void viewFamilyData(FamilyMemberResponse family) {
        apiService.getFamilyData(currentUserId, family.familyId).enqueue(new Callback<FamilyDataResponse>() {
            @Override
            public void onResponse(Call<FamilyDataResponse> call, Response<FamilyDataResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FamilyDataResponse data = response.body();
                    showFamilyDataDialog(family, data);
                } else {
                    Toast.makeText(getContext(), "获取数据失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FamilyDataResponse> call, Throwable t) {
                Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFamilyDataDialog(FamilyMemberResponse family, FamilyDataResponse data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(family.familyName + "的健康数据");

        // 创建对话框内容
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(layout);
        builder.setView(scrollView);

        // 档案信息
        if (data.archive != null) {
            TextView tvArchiveTitle = new TextView(getContext());
            tvArchiveTitle.setText("【档案信息】");
            tvArchiveTitle.setTextSize(16);
            tvArchiveTitle.setTextColor(0xFF1F2937);
            tvArchiveTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvArchiveTitle.setPadding(0, 0, 0, 8);
            layout.addView(tvArchiveTitle);

            TextView tvArchive = new TextView(getContext());
            String archiveText = "性别: " + (data.archive.gender != null ? data.archive.gender : "未设置") + "\n" +
                    "生日: " + (data.archive.birthday != null ? data.archive.birthday : "未设置") + "\n" +
                    "身高: " + (data.archive.height != null ? data.archive.height + "cm" : "未设置") + "\n" +
                    "体重: " + (data.archive.weight != null ? data.archive.weight + "kg" : "未设置") + "\n" +
                    "病史: " + (data.archive.medicalHistory != null ? data.archive.medicalHistory : "无");
            tvArchive.setText(archiveText);
            tvArchive.setTextSize(14);
            tvArchive.setTextColor(0xFF4B5563);
            tvArchive.setPadding(0, 0, 0, 16);
            layout.addView(tvArchive);
        }

        // 疾病信息
        if (data.diseases != null && !data.diseases.isEmpty()) {
            TextView tvDiseaseTitle = new TextView(getContext());
            tvDiseaseTitle.setText("【疾病信息】");
            tvDiseaseTitle.setTextSize(16);
            tvDiseaseTitle.setTextColor(0xFF1F2937);
            tvDiseaseTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDiseaseTitle.setPadding(0, 0, 0, 8);
            layout.addView(tvDiseaseTitle);

            for (Disease disease : data.diseases) {
                TextView tvDisease = new TextView(getContext());
                String diseaseText = "疾病: " + disease.diseaseName + "\n" +
                        "确诊时间: " + (disease.diagnosisDate != null ? disease.diagnosisDate : "未记录") + "\n" +
                        "医院: " + (disease.hospital != null ? disease.hospital : "未记录");
                tvDisease.setText(diseaseText);
                tvDisease.setTextSize(14);
                tvDisease.setTextColor(0xFF4B5563);
                tvDisease.setPadding(0, 0, 0, 8);
                tvDisease.setBackgroundColor(0xFFF3F4F6);
                tvDisease.setPadding(8, 8, 8, 8);
                layout.addView(tvDisease);
            }
        }

        // 用药信息
        if (data.medications != null && !data.medications.isEmpty()) {
            TextView tvMedicationTitle = new TextView(getContext());
            tvMedicationTitle.setText("【用药信息】");
            tvMedicationTitle.setTextSize(16);
            tvMedicationTitle.setTextColor(0xFF1F2937);
            tvMedicationTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMedicationTitle.setPadding(0, 0, 0, 8);
            layout.addView(tvMedicationTitle);

            for (Medication medication : data.medications) {
                TextView tvMedication = new TextView(getContext());
                String medText = "药品: " + medication.medicineName + "\n" +
                        "剂量: " + (medication.dosage != null ? medication.dosage : "未设置") + "\n" +
                        "频率: " + (medication.frequency != null ? medication.frequency : "未设置") + "\n" +
                        "状态: " + (medication.isActive != null && medication.isActive ? "在用" : "停用");
                tvMedication.setText(medText);
                tvMedication.setTextSize(14);
                tvMedication.setTextColor(0xFF4B5563);
                tvMedication.setPadding(0, 0, 0, 8);
                tvMedication.setBackgroundColor(0xFFF3F4F6);
                tvMedication.setPadding(8, 8, 8, 8);
                layout.addView(tvMedication);
            }
        }

        if (data.archive == null && (data.diseases == null || data.diseases.isEmpty()) &&
                (data.medications == null || data.medications.isEmpty())) {
            TextView tvNoData = new TextView(getContext());
            tvNoData.setText("暂无可用数据");
            tvNoData.setTextSize(14);
            tvNoData.setTextColor(0xFF6B7280);
            tvNoData.setGravity(android.view.Gravity.CENTER);
            layout.addView(tvNoData);
        }

        builder.setPositiveButton("关闭", null);
        builder.show();
    }

    private void removeFamily(FamilyMemberResponse family) {
        new AlertDialog.Builder(getContext())
                .setTitle("确认移除")
                .setMessage("确定要移除家属 " + family.familyName + " 吗？\n" +
                        "移除后您将无法查看该家属的健康数据。")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 关键修改：调用移除家属接口，传递正确的参数
                    apiService.removeFamily(currentUserId, family.familyId).enqueue(new Callback<SmsResponse>() {
                        @Override
                        public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                SmsResponse res = response.body();
                                if (res.code == 200) {
                                    Toast.makeText(getContext(), res.msg, Toast.LENGTH_SHORT).show();
                                    loadData(); // 重新加载数据
                                } else {
                                    Toast.makeText(getContext(), "移除失败: " + res.msg, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(), "移除失败: " + response.code(), Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<SmsResponse> call, Throwable t) {
                            Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "移除家属失败: " + t.getMessage());
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddFamilyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("添加家属");

        // 创建对话框内容
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // 家属手机号
        TextView tvPhoneLabel = new TextView(getContext());
        tvPhoneLabel.setText("家属手机号");
        tvPhoneLabel.setPadding(0, 0, 0, 4);
        layout.addView(tvPhoneLabel);

        EditText etPhone = new EditText(getContext());
        etPhone.setHint("请输入家属手机号");
        etPhone.setBackgroundColor(0xFFF3F4F6);
        etPhone.setMinHeight(48);
        layout.addView(etPhone);

        // 关系选择
        TextView tvRelationshipLabel = new TextView(getContext());
        tvRelationshipLabel.setText("关系类型");
        tvRelationshipLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvRelationshipLabel);

        Spinner spRelationship = new Spinner(getContext());
        spRelationship.setBackgroundColor(0xFFF3F4F6);
        spRelationship.setMinimumHeight(48);

        String[] relationships = {"请选择关系", "父子", "父女", "母子", "母女", "夫妻", "兄弟", "姐妹", "其他"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, relationships);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRelationship.setAdapter(adapter);
        layout.addView(spRelationship);

        // 权限选择
        TextView tvPermissionsLabel = new TextView(getContext());
        tvPermissionsLabel.setText("请求查看权限");
        tvPermissionsLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvPermissionsLabel);

        LinearLayout permissionLayout = new LinearLayout(getContext());
        permissionLayout.setOrientation(LinearLayout.VERTICAL);

        CheckBox cbArchive = new CheckBox(getContext());
        cbArchive.setText("健康档案");
        cbArchive.setChecked(true);
        permissionLayout.addView(cbArchive);

        CheckBox cbDisease = new CheckBox(getContext());
        cbDisease.setText("疾病信息");
        cbDisease.setChecked(true);
        permissionLayout.addView(cbDisease);

        CheckBox cbMedication = new CheckBox(getContext());
        cbMedication.setText("用药信息");
        cbMedication.setChecked(true);
        permissionLayout.addView(cbMedication);

        layout.addView(permissionLayout);

        builder.setPositiveButton("发送请求", (dialog, which) -> {
            String phone = etPhone.getText().toString().trim();
            String relationship = spRelationship.getSelectedItem().toString();

            if (TextUtils.isEmpty(phone) || !phone.matches("^1[3-9]\\d{9}$")) {
                Toast.makeText(getContext(), "请输入正确的手机号", Toast.LENGTH_SHORT).show();
                return;
            }

            if ("请选择关系".equals(relationship)) {
                Toast.makeText(getContext(), "请选择关系类型", Toast.LENGTH_SHORT).show();
                return;
            }

            if (phone.equals(currentUserPhone)) {
                Toast.makeText(getContext(), "不能添加自己为家属", Toast.LENGTH_SHORT).show();
                return;
            }

            // 构建权限列表
            List<String> permissions = new ArrayList<>();
            if (cbArchive.isChecked()) permissions.add("ARCHIVE");
            if (cbDisease.isChecked()) permissions.add("DISEASE");
            if (cbMedication.isChecked()) permissions.add("MEDICATION");

            if (permissions.isEmpty()) {
                Toast.makeText(getContext(), "请至少选择一项权限", Toast.LENGTH_SHORT).show();
                return;
            }

            // 发送请求
            FamilyRequest request = new FamilyRequest();
            request.userId = currentUserId;
            request.familyPhone = phone;
            request.relationship = relationship;
            request.permissions = permissions;

            sendFamilyRequest(request);
        });

        builder.setNegativeButton("取消", null);
        builder.setView(layout);
        builder.show();
    }

    private void sendFamilyRequest(FamilyRequest request) {
        apiService.sendFamilyRequest(request).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SmsResponse res = response.body();
                    Toast.makeText(getContext(), res.msg, Toast.LENGTH_SHORT).show();
                    loadData(); // 重新加载数据
                } else {
                    Toast.makeText(getContext(), "发送请求失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SmsResponse> call, Throwable t) {
                Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);

        // 确保视图不为空
        if (layoutPendingRequests != null) {
            layoutPendingRequests.setVisibility(show ? View.GONE : View.VISIBLE);
        }

        if (layoutFamilyList != null) {
            layoutFamilyList.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }
}