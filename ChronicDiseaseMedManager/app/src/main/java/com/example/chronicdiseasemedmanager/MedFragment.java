package com.example.chronicdiseasemedmanager;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.gson.Gson;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MedFragment extends Fragment {

    private ApiService apiService;
    private Long currentUserId;

    // 视图组件
    private TextView tvDiseaseCount, tvMedicationCount, tvActiveMedCount;
    private Button btnTabDiseases, btnTabMedications;
    private Button btnAddDisease, btnAddMedication;
    private ScrollView layoutDiseases, layoutMedications;
    private LinearLayout containerDiseaseList, containerMedicationList;

    // 数据
    private List<Disease> diseaseList = new ArrayList<>();
    private List<Medication> medicationList = new ArrayList<>();
    private Map<Long, String> diseaseNameMap = new HashMap<>();

    // 当前选中的Tab
    private boolean isDiseaseTab = true;

    private AlarmManager alarmManager;
    private static final String TAG = "MedFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_med, container, false);

        // 获取用户ID
        SharedPreferences sp = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);
        alarmManager = (AlarmManager) requireActivity().getSystemService(Context.ALARM_SERVICE);

        Log.d(TAG, "当前用户ID: " + currentUserId);

        initViews(view);
        initRetrofit();

        if (currentUserId != -1L) {
            // 先加载数据
            loadMedStats();
            loadDiseases();
            // 延迟加载用药信息，确保疾病数据先加载完成
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                loadMedications();
            }, 500);
        } else {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "用户未登录");
        }

        setupListeners();

        return view;
    }

    private void initViews(View view) {
        tvDiseaseCount = view.findViewById(R.id.tvDiseaseCount);
        tvMedicationCount = view.findViewById(R.id.tvMedicationCount);
        tvActiveMedCount = view.findViewById(R.id.tvActiveMedCount);

        btnTabDiseases = view.findViewById(R.id.btnTabDiseases);
        btnTabMedications = view.findViewById(R.id.btnTabMedications);
        btnAddDisease = view.findViewById(R.id.btnAddDisease);
        btnAddMedication = view.findViewById(R.id.btnAddMedication);

        layoutDiseases = view.findViewById(R.id.layoutDiseases);
        layoutMedications = view.findViewById(R.id.layoutMedications);
        containerDiseaseList = view.findViewById(R.id.containerDiseaseList);
        containerMedicationList = view.findViewById(R.id.containerMedicationList);

        Button btnTestReminder = view.findViewById(R.id.btnTestReminder);
        btnTestReminder.setOnClickListener(v -> testReminder());
        // 设置初始Tab
        btnTabDiseases.setSelected(true);
    }

    @SuppressLint("ScheduleExactAlarm")
    private void testReminder() {
        // 创建一个测试提醒（1分钟后）
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, 30);

        Intent intent = new Intent(requireActivity(), MedicationReminderReceiver.class);
        intent.putExtra("medicine_name", "测试药品");
        intent.putExtra("dosage", "10mg");
        intent.putExtra("time_label", "测试");
        intent.putExtra("request_code", 9999);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireActivity(),
                9999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
        );

        Toast.makeText(getContext(), "测试提醒已设置，1分钟后显示", Toast.LENGTH_SHORT).show();
    }
    private void initRetrofit() {
        try {
            apiService = new Retrofit.Builder()
                    .baseUrl("http://192.168.71.34:8080/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
            Log.d(TAG, "Retrofit初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "Retrofit初始化失败: " + e.getMessage());
        }
    }

    private void setupListeners() {
        // Tab切换
        btnTabDiseases.setOnClickListener(v -> switchTab(true));
        btnTabMedications.setOnClickListener(v -> switchTab(false));

        // 添加按钮
        btnAddDisease.setOnClickListener(v -> showAddDiseaseDialog());
        btnAddMedication.setOnClickListener(v -> showAddMedicationDialog());
    }

    private void switchTab(boolean showDiseases) {
        isDiseaseTab = showDiseases;
        btnTabDiseases.setSelected(showDiseases);
        btnTabMedications.setSelected(!showDiseases);

        layoutDiseases.setVisibility(showDiseases ? View.VISIBLE : View.GONE);
        layoutMedications.setVisibility(showDiseases ? View.GONE : View.VISIBLE);
    }

    private void loadMedStats() {
        Log.d(TAG, "开始加载统计数据，用户ID: " + currentUserId);
        apiService.getMedStats(currentUserId).enqueue(new Callback<MedStats>() {
            @Override
            public void onResponse(Call<MedStats> call, Response<MedStats> response) {
                if (response.isSuccessful() && response.body() != null) {
                    MedStats stats = response.body();
                    tvDiseaseCount.setText(String.valueOf(stats.totalDiseases));
                    tvMedicationCount.setText(String.valueOf(stats.totalMedications));
                    tvActiveMedCount.setText(String.valueOf(stats.activeMedications));
                    Log.d(TAG, "统计数据加载成功: " + stats.totalDiseases + " 疾病, " + stats.totalMedications + " 用药");
                } else {
                    Log.e(TAG, "统计数据响应失败: " + response.code() + " - " + response.message());
                    Toast.makeText(getContext(), "加载统计信息失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MedStats> call, Throwable t) {
                Log.e(TAG, "统计数据加载失败: " + t.getMessage(), t);
                Toast.makeText(getContext(), "加载统计信息失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDiseases() {
        Log.d(TAG, "开始加载疾病数据，用户ID: " + currentUserId);
        apiService.getDiseases(currentUserId).enqueue(new Callback<List<Disease>>() {
            @Override
            public void onResponse(Call<List<Disease>> call, Response<List<Disease>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    diseaseList = response.body();
                    updateDiseaseNameMap();
                    refreshDiseaseListUI();
                    Log.d(TAG, "疾病数据加载成功，数量: " + diseaseList.size());

                    // 调试：打印疾病数据
                    for (Disease disease : diseaseList) {
                        Log.d(TAG, "疾病: " + disease.diseaseName + ", ID: " + disease.id);
                    }
                } else {
                    Log.e(TAG, "疾病数据响应失败: " + response.code() + " - " + response.message());
                    Toast.makeText(getContext(), "加载疾病信息失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Disease>> call, Throwable t) {
                Log.e(TAG, "疾病数据加载失败: " + t.getMessage(), t);
                Toast.makeText(getContext(), "加载疾病信息失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMedications() {
        Log.d(TAG, "开始加载用药数据，用户ID: " + currentUserId);
        apiService.getMedications(currentUserId).enqueue(new Callback<List<Medication>>() {
            @Override
            public void onResponse(Call<List<Medication>> call, Response<List<Medication>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    medicationList = response.body();
                    // 为每个药品设置疾病名称
                    for (Medication med : medicationList) {
                        if (med.diseaseId != null && diseaseNameMap.containsKey(med.diseaseId)) {
                            med.diseaseName = diseaseNameMap.get(med.diseaseId);
                        }
                    }
                    refreshMedicationListUI();
                    Log.d(TAG, "用药数据加载成功，数量: " + medicationList.size());

                    // 新增：设置提醒（每次加载数据都重新设置）
                    setupAllMedicationReminders();

                } else {
                    Log.e(TAG, "用药数据响应失败: " + response.code() + " - " + response.message());
                    Toast.makeText(getContext(), "加载用药信息失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Medication>> call, Throwable t) {
                Log.e(TAG, "用药数据加载失败: " + t.getMessage(), t);
                Toast.makeText(getContext(), "加载用药信息失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDiseaseNameMap() {
        diseaseNameMap.clear();
        for (Disease disease : diseaseList) {
            diseaseNameMap.put(disease.id, disease.diseaseName);
            Log.d(TAG, "疾病映射: " + disease.id + " -> " + disease.diseaseName);
        }
    }

    private void refreshDiseaseListUI() {
        containerDiseaseList.removeAllViews();

        if (diseaseList.isEmpty()) {
            TextView emptyView = new TextView(getContext());
            emptyView.setText("暂无疾病信息，点击上方按钮添加");
            emptyView.setTextSize(14);
            emptyView.setTextColor(0xFF6B7280);
            emptyView.setPadding(16, 24, 16, 24);
            emptyView.setGravity(Gravity.CENTER);
            containerDiseaseList.addView(emptyView);
            Log.d(TAG, "疾病列表为空，显示空视图");
            return;
        }

        Log.d(TAG, "刷新疾病列表UI，数量: " + diseaseList.size());
        for (Disease disease : diseaseList) {
            View diseaseCard = createDiseaseCard(disease);
            containerDiseaseList.addView(diseaseCard);
        }
    }

    private View createDiseaseCard(Disease disease) {
        // 动态创建疾病卡片
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

        // 疾病名称
        LinearLayout nameLayout = new LinearLayout(getContext());
        nameLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvDiseaseName = new TextView(getContext());
        tvDiseaseName.setText(disease.diseaseName);
        tvDiseaseName.setTextSize(18);
        tvDiseaseName.setTextColor(0xFF1F2937);
        tvDiseaseName.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvDiseaseName.setLayoutParams(nameParams);

        TextView tvDiagnosisDate = new TextView(getContext());
        tvDiagnosisDate.setText(disease.diagnosisDate != null ? disease.diagnosisDate : "未记录");
        tvDiagnosisDate.setTextSize(12);
        tvDiagnosisDate.setTextColor(0xFF6B7280);

        nameLayout.addView(tvDiseaseName);
        nameLayout.addView(tvDiagnosisDate);

        // 医院信息
        TextView tvHospital = new TextView(getContext());
        tvHospital.setText("医院: " + (disease.hospital != null ? disease.hospital : "未记录"));
        tvHospital.setTextSize(14);
        tvHospital.setTextColor(0xFF4B5563);
        tvHospital.setPadding(0, 8, 0, 8);

        // 症状描述
        TextView tvSymptoms = new TextView(getContext());
        String symptoms = disease.symptoms != null ? disease.symptoms : "未描述";
        tvSymptoms.setText("症状: " + (symptoms.length() > 50 ? symptoms.substring(0, 50) + "..." : symptoms));
        tvSymptoms.setTextSize(14);
        tvSymptoms.setTextColor(0xFF6B7280);
        tvSymptoms.setMaxLines(2);
        tvSymptoms.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvSymptoms.setPadding(0, 0, 0, 12);

        // 按钮布局
        LinearLayout buttonLayout = new LinearLayout(getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

        Button btnEdit = new Button(getContext());
        btnEdit.setText("编辑");
        btnEdit.setBackgroundColor(0xFF3B82F6);
        btnEdit.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(0, 0, 8, 0);
        btnEdit.setLayoutParams(btnParams);
        btnEdit.setOnClickListener(v -> showEditDiseaseDialog(disease));

        Button btnDelete = new Button(getContext());
        btnDelete.setText("删除");
        btnDelete.setBackgroundColor(0xFFEF4444);
        btnDelete.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams btnDeleteParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnDelete.setLayoutParams(btnDeleteParams);
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除该疾病信息吗？")
                    .setPositiveButton("确定", (dialog, which) -> deleteDisease(disease.id))
                    .setNegativeButton("取消", null)
                    .show();
        });

        buttonLayout.addView(btnEdit);
        buttonLayout.addView(btnDelete);

        // 添加到卡片
        card.addView(nameLayout);
        card.addView(tvHospital);
        card.addView(tvSymptoms);
        card.addView(buttonLayout);

        // 点击查看详情
        card.setOnClickListener(v -> showDiseaseDetailDialog(disease));

        return card;
    }

    private void refreshMedicationListUI() {
        containerMedicationList.removeAllViews();

        if (medicationList.isEmpty()) {
            TextView emptyView = new TextView(getContext());
            emptyView.setText("暂无用药方案，点击上方按钮添加");
            emptyView.setTextSize(14);
            emptyView.setTextColor(0xFF6B7280);
            emptyView.setPadding(16, 24, 16, 24);
            emptyView.setGravity(Gravity.CENTER);
            containerMedicationList.addView(emptyView);
            Log.d(TAG, "用药列表为空，显示空视图");
            return;
        }

        Log.d(TAG, "刷新用药列表UI，数量: " + medicationList.size());
        for (Medication medication : medicationList) {
            View medCard = createMedicationCard(medication);
            containerMedicationList.addView(medCard);
        }
    }

    private View createMedicationCard(Medication medication) {
        // 动态创建用药卡片
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

        // 药品名称和状态
        LinearLayout nameLayout = new LinearLayout(getContext());
        nameLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvMedicineName = new TextView(getContext());
        tvMedicineName.setText(medication.medicineName);
        tvMedicineName.setTextSize(18);
        tvMedicineName.setTextColor(0xFF1F2937);
        tvMedicineName.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvMedicineName.setLayoutParams(nameParams);

        TextView tvStatus = new TextView(getContext());
        tvStatus.setText(medication.isActive != null && medication.isActive ? "在用" : "停用");
        tvStatus.setTextSize(12);
        tvStatus.setTextColor(medication.isActive != null && medication.isActive ? 0xFF10B981 : 0xFFEF4444);
        tvStatus.setPadding(8, 4, 8, 4);
        tvStatus.setBackgroundColor(medication.isActive != null && medication.isActive ? 0x1010B981 : 0x10EF4444);

        nameLayout.addView(tvMedicineName);
        nameLayout.addView(tvStatus);

        // 通用名称（如果有）
        if (medication.genericName != null && !medication.genericName.isEmpty()) {
            TextView tvGenericName = new TextView(getContext());
            tvGenericName.setText("通用名: " + medication.genericName);
            tvGenericName.setTextSize(14);
            tvGenericName.setTextColor(0xFF6B7280);
            tvGenericName.setPadding(0, 4, 0, 0);
            card.addView(tvGenericName);
        }

        // 剂量
        TextView tvDosage = new TextView(getContext());
        tvDosage.setText("剂量: " + (medication.dosage != null ? medication.dosage : "未设置"));
        tvDosage.setTextSize(14);
        tvDosage.setTextColor(0xFF4B5563);
        tvDosage.setPadding(0, 4, 0, 0);

        // 频率
        TextView tvFrequency = new TextView(getContext());
        tvFrequency.setText("频率: " + (medication.frequency != null ? medication.frequency : "未设置"));
        tvFrequency.setTextSize(14);
        tvFrequency.setTextColor(0xFF4B5563);
        tvFrequency.setPadding(0, 4, 0, 0);

        // 服药时间
        TextView tvTakeTime = new TextView(getContext());
        tvTakeTime.setText("时间: " + getTakeTimeDescription(medication));
        tvTakeTime.setTextSize(14);
        tvTakeTime.setTextColor(0xFF4B5563);
        tvTakeTime.setPadding(0, 4, 0, 0);

        // 相关疾病
        TextView tvDiseaseName = new TextView(getContext());
        tvDiseaseName.setText("相关疾病: " + (medication.diseaseName != null ? medication.diseaseName : "未关联"));
        tvDiseaseName.setTextSize(14);
        tvDiseaseName.setTextColor(0xFF6B7280);
        tvDiseaseName.setPadding(0, 4, 0, 0);

        // 按钮布局 - 新增编辑和删除按钮
        LinearLayout buttonLayout = new LinearLayout(getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

        Button btnEdit = new Button(getContext());
        btnEdit.setText("编辑");
        btnEdit.setBackgroundColor(0xFF3B82F6);
        btnEdit.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(0, 0, 8, 0);
        btnEdit.setLayoutParams(btnParams);
        btnEdit.setOnClickListener(v -> showEditMedicationDialog(medication));

        Button btnDelete = new Button(getContext());
        btnDelete.setText("删除");
        btnDelete.setBackgroundColor(0xFFEF4444);
        btnDelete.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams btnDeleteParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnDelete.setLayoutParams(btnDeleteParams);
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除该用药方案吗？")
                    .setPositiveButton("确定", (dialog, which) -> deleteMedication(medication.id))
                    .setNegativeButton("取消", null)
                    .show();
        });

        buttonLayout.addView(btnEdit);
        buttonLayout.addView(btnDelete);

        // 添加到卡片
        card.addView(nameLayout);
        card.addView(tvDosage);
        card.addView(tvFrequency);
        card.addView(tvTakeTime);
        card.addView(tvDiseaseName);
        card.addView(buttonLayout); // 添加按钮布局

        // 点击查看详情
        card.setOnClickListener(v -> showMedicationDetailDialog(medication));

        return card;
    }

    private String getTakeTimeDescription(Medication medication) {
        StringBuilder sb = new StringBuilder();
        if (medication.takeTimeMorning != null && !medication.takeTimeMorning.isEmpty()) {
            sb.append("早上 ").append(medication.takeTimeMorning);
        }
        if (medication.takeTimeNoon != null && !medication.takeTimeNoon.isEmpty()) {
            if (sb.length() > 0) sb.append("，");
            sb.append("中午 ").append(medication.takeTimeNoon);
        }
        if (medication.takeTimeEvening != null && !medication.takeTimeEvening.isEmpty()) {
            if (sb.length() > 0) sb.append("，");
            sb.append("晚上 ").append(medication.takeTimeEvening);
        }
        if (medication.takeTimeNight != null && !medication.takeTimeNight.isEmpty()) {
            if (sb.length() > 0) sb.append("，");
            sb.append("睡前 ").append(medication.takeTimeNight);
        }
        return sb.length() > 0 ? sb.toString() : "未设置服药时间";
    }

    // --- 疾病管理对话框 ---
    private void showAddDiseaseDialog() {
        showDiseaseDialog(null);
    }

    private void showEditDiseaseDialog(Disease disease) {
        showDiseaseDialog(disease);
    }

    private void showDiseaseDialog(Disease disease) {
        boolean isEdit = disease != null;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(isEdit ? "编辑疾病信息" : "添加疾病信息");

        // 创建对话框内容
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(layout);
        builder.setView(scrollView);

        // 疾病名称
        TextView tvDiseaseNameLabel = new TextView(getContext());
        tvDiseaseNameLabel.setText("疾病名称 *");
        tvDiseaseNameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvDiseaseNameLabel.setPadding(0, 0, 0, 4);
        layout.addView(tvDiseaseNameLabel);

        EditText etDiseaseName = new EditText(getContext());
        etDiseaseName.setHint("如：高血压、糖尿病");
        etDiseaseName.setBackgroundColor(0xFFF3F4F6);
        etDiseaseName.setMinHeight(48);
        layout.addView(etDiseaseName);

        // 疾病类型
        TextView tvDiseaseTypeLabel = new TextView(getContext());
        tvDiseaseTypeLabel.setText("疾病类型");
        tvDiseaseTypeLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvDiseaseTypeLabel);

        EditText etDiseaseType = new EditText(getContext());
        etDiseaseType.setHint("如：原发性、继发性");
        etDiseaseType.setBackgroundColor(0xFFF3F4F6);
        etDiseaseType.setMinHeight(48);
        layout.addView(etDiseaseType);

        // 确诊日期
        TextView tvDiagnosisDateLabel = new TextView(getContext());
        tvDiagnosisDateLabel.setText("确诊日期");
        tvDiagnosisDateLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvDiagnosisDateLabel);

        EditText etDiagnosisDate = new EditText(getContext());
        etDiagnosisDate.setHint("格式：YYYY-MM-DD");
        etDiagnosisDate.setBackgroundColor(0xFFF3F4F6);
        etDiagnosisDate.setMinHeight(48);
        layout.addView(etDiagnosisDate);

        // 确诊医院
        TextView tvHospitalLabel = new TextView(getContext());
        tvHospitalLabel.setText("确诊医院");
        tvHospitalLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvHospitalLabel);

        EditText etHospital = new EditText(getContext());
        etHospital.setHint("输入医院名称");
        etHospital.setBackgroundColor(0xFFF3F4F6);
        etHospital.setMinHeight(48);
        layout.addView(etHospital);

        // 主治医生
        TextView tvDoctorLabel = new TextView(getContext());
        tvDoctorLabel.setText("主治医生");
        tvDoctorLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvDoctorLabel);

        EditText etDoctor = new EditText(getContext());
        etDoctor.setHint("输入医生姓名");
        etDoctor.setBackgroundColor(0xFFF3F4F6);
        etDoctor.setMinHeight(48);
        layout.addView(etDoctor);

        // 诊断信息
        TextView tvDiagnosisInfoLabel = new TextView(getContext());
        tvDiagnosisInfoLabel.setText("诊断信息");
        tvDiagnosisInfoLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvDiagnosisInfoLabel);

        EditText etDiagnosisInfo = new EditText(getContext());
        etDiagnosisInfo.setHint("详细描述诊断结果");
        etDiagnosisInfo.setBackgroundColor(0xFFF3F4F6);
        etDiagnosisInfo.setMinHeight(120);
        etDiagnosisInfo.setGravity(android.view.Gravity.TOP);
        layout.addView(etDiagnosisInfo);

        // 症状描述
        TextView tvSymptomsLabel = new TextView(getContext());
        tvSymptomsLabel.setText("症状描述");
        tvSymptomsLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvSymptomsLabel);

        EditText etSymptoms = new EditText(getContext());
        etSymptoms.setHint("详细描述症状表现");
        etSymptoms.setBackgroundColor(0xFFF3F4F6);
        etSymptoms.setMinHeight(120);
        etSymptoms.setGravity(android.view.Gravity.TOP);
        layout.addView(etSymptoms);

        if (isEdit) {
            etDiseaseName.setText(disease.diseaseName);
            etDiseaseType.setText(disease.diseaseType);
            etDiagnosisDate.setText(disease.diagnosisDate);
            etHospital.setText(disease.hospital);
            etDoctor.setText(disease.doctor);
            etDiagnosisInfo.setText(disease.diagnosisInfo);
            etSymptoms.setText(disease.symptoms);
        }

        builder.setPositiveButton("保存", (dialog, which) -> {
            String diseaseName = etDiseaseName.getText().toString().trim();
            if (TextUtils.isEmpty(diseaseName)) {
                Toast.makeText(getContext(), "请输入疾病名称", Toast.LENGTH_SHORT).show();
                return;
            }

            Disease newDisease = isEdit ? disease : new Disease();
            newDisease.userId = currentUserId;
            newDisease.diseaseName = diseaseName;
            newDisease.diseaseType = etDiseaseType.getText().toString().trim();
            newDisease.diagnosisInfo = etDiagnosisInfo.getText().toString().trim();
            newDisease.symptoms = etSymptoms.getText().toString().trim();
            newDisease.diagnosisDate = etDiagnosisDate.getText().toString().trim();
            newDisease.hospital = etHospital.getText().toString().trim();
            newDisease.doctor = etDoctor.getText().toString().trim();

            Log.d(TAG, "保存疾病数据: " + newDisease.diseaseName + ", 用户ID: " + newDisease.userId);

            if (isEdit) {
                apiService.updateDisease(newDisease).enqueue(new Callback<SmsResponse>() {
                    @Override
                    public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                        Log.d(TAG, "更新疾病响应: " + response.code());
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "更新疾病结果: " + response.body().code + " - " + response.body().msg);
                            if (response.body().code == 200) {
                                Toast.makeText(getContext(), "更新成功", Toast.LENGTH_SHORT).show();
                                loadDiseases();
                                loadMedStats();
                            } else {
                                Toast.makeText(getContext(), "更新失败: " + response.body().msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "更新失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<SmsResponse> call, Throwable t) {
                        Log.e(TAG, "更新疾病失败: " + t.getMessage(), t);
                        Toast.makeText(getContext(), "更新失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                apiService.addDisease(newDisease).enqueue(new Callback<SmsResponse>() {
                    @Override
                    public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                        Log.d(TAG, "添加疾病响应: " + response.code());
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "添加疾病结果: " + response.body().code + " - " + response.body().msg);
                            if (response.body().code == 200) {
                                Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();
                                loadDiseases();
                                loadMedStats();
                            } else {
                                Toast.makeText(getContext(), "添加失败: " + response.body().msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "添加失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<SmsResponse> call, Throwable t) {
                        Log.e(TAG, "添加疾病失败: " + t.getMessage(), t);
                        Toast.makeText(getContext(), "添加失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void deleteDisease(Long diseaseId) {
        Log.d(TAG, "删除疾病，ID: " + diseaseId);
        apiService.deleteDisease(diseaseId).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                Log.d(TAG, "删除疾病响应: " + response.code());
                if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                    Toast.makeText(getContext(), "删除成功", Toast.LENGTH_SHORT).show();
                    loadDiseases();
                    loadMedStats();
                }
            }

            @Override
            public void onFailure(Call<SmsResponse> call, Throwable t) {
                Log.e(TAG, "删除疾病失败: " + t.getMessage(), t);
                Toast.makeText(getContext(), "删除失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDiseaseDetailDialog(Disease disease) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(disease.diseaseName);

        String detail = "疾病类型: " + (disease.diseaseType != null ? disease.diseaseType : "未设置") + "\n\n"
                + "确诊日期: " + (disease.diagnosisDate != null ? disease.diagnosisDate : "未记录") + "\n\n"
                + "确诊医院: " + (disease.hospital != null ? disease.hospital : "未记录") + "\n\n"
                + "主治医生: " + (disease.doctor != null ? disease.doctor : "未记录") + "\n\n"
                + "诊断信息:\n" + (disease.diagnosisInfo != null ? disease.diagnosisInfo : "未记录") + "\n\n"
                + "症状描述:\n" + (disease.symptoms != null ? disease.symptoms : "未描述");

        builder.setMessage(detail);
        builder.setPositiveButton("关闭", null);
        builder.show();
    }

    // --- 用药管理对话框 ---
    private void showAddMedicationDialog() {
        showMedicationDialog(null);
    }

    private void showEditMedicationDialog(Medication medication) {
        showMedicationDialog(medication);
    }

    private void showMedicationDialog(Medication medication) {
        boolean isEdit = medication != null;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(isEdit ? "编辑用药方案" : "添加用药方案");

        // 创建对话框内容
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(layout);
        builder.setView(scrollView);

        // 关联疾病选择器
        TextView tvDiseaseLabel = new TextView(getContext());
        tvDiseaseLabel.setText("关联疾病");
        tvDiseaseLabel.setPadding(0, 0, 0, 4);
        layout.addView(tvDiseaseLabel);

        Spinner spDisease = new Spinner(getContext());
        spDisease.setBackgroundColor(0xFFF3F4F6);
        spDisease.setMinimumHeight(48);
        layout.addView(spDisease);

        // 药品名称
        TextView tvMedicineNameLabel = new TextView(getContext());
        tvMedicineNameLabel.setText("药品名称 *");
        tvMedicineNameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvMedicineNameLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvMedicineNameLabel);

        EditText etMedicineName = new EditText(getContext());
        etMedicineName.setHint("如：阿司匹林");
        etMedicineName.setBackgroundColor(0xFFF3F4F6);
        etMedicineName.setMinHeight(48);
        layout.addView(etMedicineName);

        // 通用名称
        TextView tvGenericNameLabel = new TextView(getContext());
        tvGenericNameLabel.setText("通用名称");
        tvGenericNameLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvGenericNameLabel);

        EditText etGenericName = new EditText(getContext());
        etGenericName.setHint("药品的通用名称");
        etGenericName.setBackgroundColor(0xFFF3F4F6);
        etGenericName.setMinHeight(48);
        layout.addView(etGenericName);

        // 剂量
        TextView tvDosageLabel = new TextView(getContext());
        tvDosageLabel.setText("剂量");
        tvDosageLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvDosageLabel);

        EditText etDosage = new EditText(getContext());
        etDosage.setHint("如：10mg");
        etDosage.setBackgroundColor(0xFFF3F4F6);
        etDosage.setMinHeight(48);
        layout.addView(etDosage);

        // 频率
        TextView tvFrequencyLabel = new TextView(getContext());
        tvFrequencyLabel.setText("服用频率");
        tvFrequencyLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvFrequencyLabel);

        EditText etFrequency = new EditText(getContext());
        etFrequency.setHint("如：每日一次、每日三次");
        etFrequency.setBackgroundColor(0xFFF3F4F6);
        etFrequency.setMinHeight(48);
        layout.addView(etFrequency);

        // 服用说明
        TextView tvInstructionsLabel = new TextView(getContext());
        tvInstructionsLabel.setText("服用说明");
        tvInstructionsLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvInstructionsLabel);

        EditText etInstructions = new EditText(getContext());
        etInstructions.setHint("如：饭前、饭后、空腹");
        etInstructions.setBackgroundColor(0xFFF3F4F6);
        etInstructions.setMinHeight(48);
        layout.addView(etInstructions);

        // 服药时间
        TextView tvTakeTimeLabel = new TextView(getContext());
        tvTakeTimeLabel.setText("服药时间");
        tvTakeTimeLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvTakeTimeLabel);

        LinearLayout timeLayout = new LinearLayout(getContext());
        timeLayout.setOrientation(LinearLayout.HORIZONTAL);

        EditText etTakeTimeMorning = new EditText(getContext());
        etTakeTimeMorning.setHint("早上");
        etTakeTimeMorning.setBackgroundColor(0xFFF3F4F6);
        etTakeTimeMorning.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        etTakeTimeMorning.setLayoutParams(timeParams);

        EditText etTakeTimeNoon = new EditText(getContext());
        etTakeTimeNoon.setHint("中午");
        etTakeTimeNoon.setBackgroundColor(0xFFF3F4F6);
        etTakeTimeNoon.setGravity(android.view.Gravity.CENTER);
        etTakeTimeNoon.setLayoutParams(timeParams);

        EditText etTakeTimeEvening = new EditText(getContext());
        etTakeTimeEvening.setHint("晚上");
        etTakeTimeEvening.setBackgroundColor(0xFFF3F4F6);
        etTakeTimeEvening.setGravity(android.view.Gravity.CENTER);
        etTakeTimeEvening.setLayoutParams(timeParams);

        EditText etTakeTimeNight = new EditText(getContext());
        etTakeTimeNight.setHint("睡前");
        etTakeTimeNight.setBackgroundColor(0xFFF3F4F6);
        etTakeTimeNight.setGravity(android.view.Gravity.CENTER);
        etTakeTimeNight.setLayoutParams(timeParams);

        timeLayout.addView(etTakeTimeMorning);
        timeLayout.addView(etTakeTimeNoon);
        timeLayout.addView(etTakeTimeEvening);
        timeLayout.addView(etTakeTimeNight);
        layout.addView(timeLayout);

        // 开始日期
        TextView tvStartDateLabel = new TextView(getContext());
        tvStartDateLabel.setText("开始日期");
        tvStartDateLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvStartDateLabel);

        EditText etStartDate = new EditText(getContext());
        etStartDate.setHint("开始服用日期");
        etStartDate.setBackgroundColor(0xFFF3F4F6);
        etStartDate.setMinHeight(48);
        layout.addView(etStartDate);

        // 结束日期
        TextView tvEndDateLabel = new TextView(getContext());
        tvEndDateLabel.setText("结束日期");
        tvEndDateLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvEndDateLabel);

        EditText etEndDate = new EditText(getContext());
        etEndDate.setHint("结束服用日期");
        etEndDate.setBackgroundColor(0xFFF3F4F6);
        etEndDate.setMinHeight(48);
        layout.addView(etEndDate);

        // 是否在用开关
        TextView tvIsActiveLabel = new TextView(getContext());
        tvIsActiveLabel.setText("是否在用");
        tvIsActiveLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvIsActiveLabel);

        Switch switchIsActive = new Switch(getContext());
        switchIsActive.setChecked(true);
        layout.addView(switchIsActive);

        // 注意事项
        TextView tvPrecautionsLabel = new TextView(getContext());
        tvPrecautionsLabel.setText("注意事项");
        tvPrecautionsLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvPrecautionsLabel);

        EditText etPrecautions = new EditText(getContext());
        etPrecautions.setHint("输入用药注意事项");
        etPrecautions.setBackgroundColor(0xFFF3F4F6);
        etPrecautions.setMinHeight(120);
        etPrecautions.setGravity(android.view.Gravity.TOP);
        layout.addView(etPrecautions);

        // 副作用
        TextView tvSideEffectsLabel = new TextView(getContext());
        tvSideEffectsLabel.setText("副作用");
        tvSideEffectsLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvSideEffectsLabel);

        EditText etSideEffects = new EditText(getContext());
        etSideEffects.setHint("输入可能的副作用");
        etSideEffects.setBackgroundColor(0xFFF3F4F6);
        etSideEffects.setMinHeight(120);
        etSideEffects.setGravity(android.view.Gravity.TOP);
        layout.addView(etSideEffects);

        // 禁忌症
        TextView tvContraindicationsLabel = new TextView(getContext());
        tvContraindicationsLabel.setText("禁忌症");
        tvContraindicationsLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvContraindicationsLabel);

        EditText etContraindications = new EditText(getContext());
        etContraindications.setHint("输入禁忌症状");
        etContraindications.setBackgroundColor(0xFFF3F4F6);
        etContraindications.setMinHeight(120);
        etContraindications.setGravity(android.view.Gravity.TOP);
        layout.addView(etContraindications);

        // 作用机制
        TextView tvMechanismLabel = new TextView(getContext());
        tvMechanismLabel.setText("作用机制");
        tvMechanismLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvMechanismLabel);

        EditText etMechanism = new EditText(getContext());
        etMechanism.setHint("输入药品作用机制");
        etMechanism.setBackgroundColor(0xFFF3F4F6);
        etMechanism.setMinHeight(120);
        etMechanism.setGravity(android.view.Gravity.TOP);
        layout.addView(etMechanism);

        // 储存方式
        TextView tvStorageLabel = new TextView(getContext());
        tvStorageLabel.setText("储存方式");
        tvStorageLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvStorageLabel);

        EditText etStorage = new EditText(getContext());
        etStorage.setHint("如：阴凉干燥处保存");
        etStorage.setBackgroundColor(0xFFF3F4F6);
        etStorage.setMinHeight(48);
        layout.addView(etStorage);

        // 设置疾病选择器数据
        List<String> diseaseNames = new ArrayList<>();
        diseaseNames.add("请选择关联疾病");
        for (Disease d : diseaseList) {
            diseaseNames.add(d.diseaseName);
        }

        ArrayAdapter<String> diseaseAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, diseaseNames);
        diseaseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDisease.setAdapter(diseaseAdapter);

        if (isEdit && medication.diseaseId != null) {
            for (int i = 0; i < diseaseList.size(); i++) {
                if (diseaseList.get(i).id.equals(medication.diseaseId)) {
                    spDisease.setSelection(i + 1);
                    break;
                }
            }
        }

        // 填充数据（如果是编辑模式）
        if (isEdit) {
            etMedicineName.setText(medication.medicineName);
            etGenericName.setText(medication.genericName);
            etDosage.setText(medication.dosage);
            etFrequency.setText(medication.frequency);
            etInstructions.setText(medication.instructions);
            etTakeTimeMorning.setText(medication.takeTimeMorning);
            etTakeTimeNoon.setText(medication.takeTimeNoon);
            etTakeTimeEvening.setText(medication.takeTimeEvening);
            etTakeTimeNight.setText(medication.takeTimeNight);
            etStartDate.setText(medication.startDate);
            etEndDate.setText(medication.endDate);
            etPrecautions.setText(medication.precautions);
            etSideEffects.setText(medication.sideEffects);
            etContraindications.setText(medication.contraindications);
            etMechanism.setText(medication.mechanism);
            etStorage.setText(medication.storage);
            switchIsActive.setChecked(medication.isActive != null && medication.isActive);
        }

        // 设置时间字段的点击事件
        setupTimePicker(etTakeTimeMorning, "早上");
        setupTimePicker(etTakeTimeNoon, "中午");
        setupTimePicker(etTakeTimeEvening, "晚上");
        setupTimePicker(etTakeTimeNight, "睡前");

        // 设置日期字段的点击事件
        DatePickerHelper datePickerHelper = new DatePickerHelper();
        etStartDate.setOnClickListener(v -> datePickerHelper.showDatePicker(getContext(), etStartDate));
        etEndDate.setOnClickListener(v -> datePickerHelper.showDatePicker(getContext(), etEndDate));

        builder.setPositiveButton("保存", (dialog, which) -> {
            String medicineName = etMedicineName.getText().toString().trim();
            if (TextUtils.isEmpty(medicineName)) {
                Toast.makeText(getContext(), "请输入药品名称", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取选择的疾病ID
            Long selectedDiseaseId = null;
            int selectedPosition = spDisease.getSelectedItemPosition();
            if (selectedPosition > 0 && selectedPosition <= diseaseList.size()) {
                selectedDiseaseId = diseaseList.get(selectedPosition - 1).id;
            }

            Medication newMedication = isEdit ? medication : new Medication();
            newMedication.userId = currentUserId;
            newMedication.diseaseId = selectedDiseaseId;
            newMedication.medicineName = medicineName;
            newMedication.genericName = etGenericName.getText().toString().trim();
            newMedication.dosage = etDosage.getText().toString().trim();
            newMedication.frequency = etFrequency.getText().toString().trim();
            newMedication.instructions = etInstructions.getText().toString().trim();
            newMedication.takeTimeMorning = etTakeTimeMorning.getText().toString().trim();
            newMedication.takeTimeNoon = etTakeTimeNoon.getText().toString().trim();
            newMedication.takeTimeEvening = etTakeTimeEvening.getText().toString().trim();
            newMedication.takeTimeNight = etTakeTimeNight.getText().toString().trim();
            newMedication.precautions = etPrecautions.getText().toString().trim();
            newMedication.sideEffects = etSideEffects.getText().toString().trim();
            newMedication.contraindications = etContraindications.getText().toString().trim();
            newMedication.mechanism = etMechanism.getText().toString().trim();
            newMedication.storage = etStorage.getText().toString().trim();
            newMedication.startDate = etStartDate.getText().toString().trim();
            newMedication.endDate = etEndDate.getText().toString().trim();
            newMedication.isActive = switchIsActive.isChecked();

            Log.d(TAG, "保存用药数据: " + newMedication.medicineName + ", 用户ID: " + newMedication.userId);

            if (isEdit) {
                apiService.updateMedication(newMedication).enqueue(new Callback<SmsResponse>() {
                    @Override
                    public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                        Log.d(TAG, "更新用药响应: " + response.code());
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "更新用药结果: " + response.body().code + " - " + response.body().msg);
                            if (response.body().code == 200) {
                                Toast.makeText(getContext(), "更新成功", Toast.LENGTH_SHORT).show();

                                // 先取消旧的提醒
                                if (medication.id != null) {
                                    cancelMedicationReminders(medication.id);
                                }

                                // 重新加载用药列表
                                loadMedications();
                                loadMedStats();

                                // 新增：为更新的药品重新设置提醒
                                if (newMedication.isActive) {
                                    setupMedicationReminders(newMedication);
                                    Toast.makeText(getContext(), "用药提醒已更新", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(), "更新失败: " + response.body().msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "更新失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<SmsResponse> call, Throwable t) {
                        Log.e(TAG, "更新用药失败: " + t.getMessage(), t);
                        Toast.makeText(getContext(), "更新失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                apiService.addMedication(newMedication).enqueue(new Callback<SmsResponse>() {
                    @Override
                    public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                        Log.d(TAG, "添加用药响应: " + response.code());
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "添加用药结果: " + response.body().code + " - " + response.body().msg);
                            if (response.body().code == 200) {
                                Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();

                                // 重新加载用药列表
                                loadMedications();
                                loadMedStats();

                                // 新增：为新添加的药品设置提醒
                                if (newMedication.isActive) {
                                    setupMedicationReminders(newMedication);
                                    Toast.makeText(getContext(), "用药提醒已设置", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(), "添加失败: " + response.body().msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "添加失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<SmsResponse> call, Throwable t) {
                        Log.e(TAG, "添加用药失败: " + t.getMessage(), t);
                        Toast.makeText(getContext(), "添加失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 新增：删除用药方案的方法
    private void deleteMedication(Long medicationId) {
        Log.d(TAG, "删除用药方案，ID: " + medicationId);
        apiService.deleteMedication(medicationId).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                Log.d(TAG, "删除用药响应: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().code == 200) {
                        Toast.makeText(getContext(), "删除成功", Toast.LENGTH_SHORT).show();
                        cancelMedicationReminders(medicationId);
                        loadMedications();
                        loadMedStats();
                    } else {
                        Toast.makeText(getContext(), "删除失败: " + response.body().msg, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "删除失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SmsResponse> call, Throwable t) {
                Log.e(TAG, "删除用药失败: " + t.getMessage(), t);
                Toast.makeText(getContext(), "删除失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMedicationDetailDialog(Medication medication) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(medication.medicineName);

        StringBuilder detailBuilder = new StringBuilder();

        if (medication.genericName != null && !medication.genericName.isEmpty()) {
            detailBuilder.append("通用名称: ").append(medication.genericName).append("\n\n");
        }

        detailBuilder.append("剂量: ").append(medication.dosage != null ? medication.dosage : "未设置").append("\n\n")
                .append("服用频率: ").append(medication.frequency != null ? medication.frequency : "未设置").append("\n\n");

        if (medication.instructions != null && !medication.instructions.isEmpty()) {
            detailBuilder.append("服用说明: ").append(medication.instructions).append("\n\n");
        }

        detailBuilder.append("服药时间: ").append(getTakeTimeDescription(medication)).append("\n\n")
                .append("相关疾病: ").append(medication.diseaseName != null ? medication.diseaseName : "未关联").append("\n\n")
                .append("状态: ").append(medication.isActive != null && medication.isActive ? "在用" : "停用").append("\n\n")
                .append("开始日期: ").append(medication.startDate != null ? medication.startDate : "未设置").append("\n\n");

        if (medication.endDate != null && !medication.endDate.isEmpty()) {
            detailBuilder.append("结束日期: ").append(medication.endDate).append("\n\n");
        }

        if (medication.precautions != null && !medication.precautions.isEmpty()) {
            detailBuilder.append("注意事项:\n").append(medication.precautions).append("\n\n");
        }

        if (medication.sideEffects != null && !medication.sideEffects.isEmpty()) {
            detailBuilder.append("副作用:\n").append(medication.sideEffects).append("\n\n");
        }

        if (medication.contraindications != null && !medication.contraindications.isEmpty()) {
            detailBuilder.append("禁忌症:\n").append(medication.contraindications).append("\n\n");
        }

        if (medication.mechanism != null && !medication.mechanism.isEmpty()) {
            detailBuilder.append("作用机制:\n").append(medication.mechanism).append("\n\n");
        }

        if (medication.storage != null && !medication.storage.isEmpty()) {
            detailBuilder.append("储存方式: ").append(medication.storage).append("\n\n");
        }

        builder.setMessage(detailBuilder.toString());

        // 添加编辑按钮
        builder.setPositiveButton("编辑", (dialog, which) -> {
            showEditMedicationDialog(medication);
        });

        // 添加删除按钮
        builder.setNeutralButton("删除", (dialog, which) -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除该用药方案吗？")
                    .setPositiveButton("确定", (dialog2, which2) -> deleteMedication(medication.id))
                    .setNegativeButton("取消", null)
                    .show();
        });

        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void setupTimePicker(EditText editText, String title) {
        editText.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    getContext(),
                    (view, hourOfDay, minute) -> {
                        String time = String.format("%02d:%02d", hourOfDay, minute);
                        editText.setText(time);
                    },
                    8, 0, true
            );
            timePickerDialog.setTitle(title + "服药时间");
            timePickerDialog.show();
        });
    }

    private void setupAllMedicationReminders() {
        // 取消所有现有提醒
        cancelAllReminders();

        // 为每个有效用药设置提醒
        for (Medication medication : medicationList) {
            if (medication.isActive != null && medication.isActive) {
                setupMedicationReminders(medication);
            }
        }
    }

    // 新增方法：设置单个药品的提醒
    private void setupMedicationReminders(Medication medication) {
        if (medication.id == null) return;

        // 早上提醒
        if (medication.takeTimeMorning != null && !medication.takeTimeMorning.isEmpty()) {
            setReminder(medication, medication.takeTimeMorning, "早上", (int) (medication.id * 10 + 1));
        }

        // 中午提醒
        if (medication.takeTimeNoon != null && !medication.takeTimeNoon.isEmpty()) {
            setReminder(medication, medication.takeTimeNoon, "中午", (int) (medication.id * 10 + 2));
        }

        // 晚上提醒
        if (medication.takeTimeEvening != null && !medication.takeTimeEvening.isEmpty()) {
            setReminder(medication, medication.takeTimeEvening, "晚上", (int) (medication.id * 10 + 3));
        }

        // 睡前提醒
        if (medication.takeTimeNight != null && !medication.takeTimeNight.isEmpty()) {
            setReminder(medication, medication.takeTimeNight, "睡前", (int) (medication.id * 10 + 4));
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private void setReminder(Medication medication, String timeStr, String timeLabel, int requestCode) {
        try {
            // 1. 解析时间
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            // 2. 准备 Intent - 第一次提醒
            Intent intent = new Intent(requireActivity(), MedicationReminderReceiver.class);
            intent.putExtra("medicine_name", medication.medicineName);
            intent.putExtra("dosage", medication.dosage);
            intent.putExtra("time_label", timeLabel);
            intent.putExtra("request_code", requestCode);
            intent.putExtra("reminder_type", 1);

            // 关键：传递时间信息，用于自动重设
            intent.putExtra("hour", hour);
            intent.putExtra("minute", minute);
            intent.putExtra("medication_id", medication.id != null ? medication.id : 0L);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    requireActivity(),
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 3. 设置日历时间
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // 如果时间已过，设置为明天
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }

            // 4. --- 关键修改：使用精确闹钟 ---

            // 检查权限 (Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.e(TAG, "没有精确闹钟权限");
                    // 实际开发中应该弹窗引导用户去设置页面开启权限
                    // Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    // startActivity(intent);
                    return;
                }
            }

            // 使用 setExactAndAllowWhileIdle (即使在低电量模式也能唤醒)
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );

            Log.d(TAG, "第一次提醒已设置：" + medication.medicineName + " " + timeLabel + " " + timeStr +
                    "，触发时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(calendar.getTimeInMillis())));

        } catch (Exception e) {
            Log.e(TAG, "设置提醒失败：" + e.getMessage(), e);
        }
    }


    private void cancelAllReminders() {
        for (Medication medication : medicationList) {
            if (medication.id == null) continue;

            int[] requestCodes = {
                    (int)(medication.id * 10 + 1),
                    (int)(medication.id * 10 + 2),
                    (int)(medication.id * 10 + 3),
                    (int)(medication.id * 10 + 4)
            };

            for (int code : requestCodes) {
                // 取消第一次提醒
                Intent firstIntent = new Intent(requireActivity(), MedicationReminderReceiver.class);
                PendingIntent firstPendingIntent = PendingIntent.getBroadcast(
                        requireActivity(),
                        code,
                        firstIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                alarmManager.cancel(firstPendingIntent);

                // 取消第二次提醒
                Intent secondIntent = new Intent(requireActivity(), MedicationReminderReceiver.class);
                PendingIntent secondPendingIntent = PendingIntent.getBroadcast(
                        requireActivity(),
                        code + 2000,
                        secondIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                alarmManager.cancel(secondPendingIntent);
            }
        }
    }

    private void cancelMedicationReminders(Long medicationId) {
        if (medicationId == null) return;

        int[] requestCodes = {
                (int)(medicationId * 10 + 1),
                (int)(medicationId * 10 + 2),
                (int)(medicationId * 10 + 3),
                (int)(medicationId * 10 + 4)
        };

        for (int code : requestCodes) {
            // 取消第一次提醒
            Intent firstIntent = new Intent(requireActivity(), MedicationReminderReceiver.class);
            PendingIntent firstPendingIntent = PendingIntent.getBroadcast(
                    requireActivity(),
                    code,
                    firstIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(firstPendingIntent);

            // 取消第二次提醒
            Intent secondIntent = new Intent(requireActivity(), MedicationReminderReceiver.class);
            PendingIntent secondPendingIntent = PendingIntent.getBroadcast(
                    requireActivity(),
                    code + 2000,
                    secondIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(secondPendingIntent);
        }
    }
    // 在Fragment销毁时取消提醒
    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelAllReminders();
    }

}
