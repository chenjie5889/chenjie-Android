package com.example.chronicdiseasemedmanager;

import android.app.DatePickerDialog;
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

import java.text.SimpleDateFormat;
import java.util.*;

public class ArchiveFragment extends Fragment {

    // 原有视图组件
    private View layoutEmpty, layoutContent, containerEdit, containerDetail;
    private EditText etHeight, etWeight, etHistory, etName, etBirthday;
    private TextView viewName, viewGender, viewBirthday, viewBMI, viewHistory, btnEditTop, tvGoFill, btnSave;
    private RadioGroup rgGender;
    private RadioButton rbMale, rbFemale;

    // 新增自定义指标视图组件
    private LinearLayout metricTabsContainer, metricRecordsContainer;
    private TextView btnAddMetric, btnAddRecord, tvSelectedDate, btnPrevDay, btnNextDay, btnToday;
    private LinearLayout metricDateLayout, metricEmptyState, recordEmptyState;
    private HorizontalScrollView metricTabsScroll;

    private ApiService apiService;
    private Long currentUserId;
    private boolean hasArchiveData = false;

    // 自定义指标数据
    private List<CustomMetric> metricList = new ArrayList<>();
    private List<CustomMetricRecord> recordList = new ArrayList<>();
    private Long selectedMetricId = null;
    private Calendar currentCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_archive, container, false);

        SharedPreferences sp = requireActivity().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);

        initViews(view);
        initRetrofit();

        if (currentUserId != -1L) {
            loadArchive();
            loadMetrics(); // 加载自定义指标
        } else {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
        }

        setupListeners();

        return view;
    }

    private void initViews(View v) {
        // 原有视图初始化
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

        // 自定义指标视图初始化
        metricTabsContainer = v.findViewById(R.id.metricTabsContainer);
        metricRecordsContainer = v.findViewById(R.id.metricRecordsContainer);
        btnAddMetric = v.findViewById(R.id.btnAddMetric);
        btnAddRecord = v.findViewById(R.id.btnAddRecord);
        tvSelectedDate = v.findViewById(R.id.tvSelectedDate);
        btnPrevDay = v.findViewById(R.id.btnPrevDay);
        btnNextDay = v.findViewById(R.id.btnNextDay);
        btnToday = v.findViewById(R.id.btnToday);
        metricDateLayout = v.findViewById(R.id.metricDateLayout);
        metricEmptyState = v.findViewById(R.id.metricEmptyState);
        recordEmptyState = v.findViewById(R.id.recordEmptyState);
        metricTabsScroll = v.findViewById(R.id.metricTabsScroll);

        // 姓名字段设为不可编辑
        etName.setEnabled(false);
        // 生日字段设为可编辑
        etBirthday.setEnabled(true);

        // 初始化日期显示
        updateDateDisplay();
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.238.1:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void setupListeners() {
        // 原有监听器
        btnEditTop.setOnClickListener(v -> switchMode(true));
        tvGoFill.setOnClickListener(v -> switchMode(true));
        btnSave.setOnClickListener(v -> saveArchive());

        // 自定义指标监听器
        btnAddMetric.setOnClickListener(v -> showAddMetricDialog());
        btnAddRecord.setOnClickListener(v -> {
            if (selectedMetricId == null) {
                Toast.makeText(getContext(), "请先选择一个指标", Toast.LENGTH_SHORT).show();
            } else {
                showAddRecordDialog();
            }
        });

        btnPrevDay.setOnClickListener(v -> {
            currentCalendar.add(Calendar.DAY_OF_MONTH, -1);
            updateDateDisplay();
            loadRecords();
        });

        btnNextDay.setOnClickListener(v -> {
            currentCalendar.add(Calendar.DAY_OF_MONTH, 1);
            updateDateDisplay();
            loadRecords();
        });

        btnToday.setOnClickListener(v -> {
            currentCalendar = Calendar.getInstance();
            updateDateDisplay();
            loadRecords();
        });

        tvSelectedDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        currentCalendar.set(year, month, dayOfMonth);
                        updateDateDisplay();
                        loadRecords();
                    },
                    currentCalendar.get(Calendar.YEAR),
                    currentCalendar.get(Calendar.MONTH),
                    currentCalendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });
    }

    // 原有档案方法
    private void loadArchive() {
        showLoading(true);
        apiService.getArchive(currentUserId).enqueue(new Callback<ArchivePlusResponse>() {
            @Override
            public void onResponse(Call<ArchivePlusResponse> call, Response<ArchivePlusResponse> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    ArchivePlusResponse data = response.body();

                    layoutEmpty.setVisibility(View.GONE);
                    layoutContent.setVisibility(View.VISIBLE);

                    hasArchiveData = data.hasArchiveData();

                    viewName.setText("姓名：" + (data.realName != null ? data.realName : "未绑定"));

                    if (data.hasArchiveData() && data.archive != null) {
                        Archive archive = data.archive;

                        viewGender.setText("性别：" + (archive.gender != null ? archive.gender : "未设定"));
                        viewBirthday.setText("生日：" + (archive.birthday != null ? archive.birthday : "未设定"));

                        String heightStr = archive.height != null ? String.format("%.1f", archive.height) + "cm" : "未设定";
                        String weightStr = archive.weight != null ? String.format("%.1f", archive.weight) + "kg" : "未设定";
                        viewBMI.setText("体征：" + heightStr + " / " + weightStr);
                        viewHistory.setText(archive.medicalHistory != null ? archive.medicalHistory : "暂无记录");

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

                        btnEditTop.setVisibility(View.VISIBLE);
                    } else {
                        viewGender.setText("性别：未设定");
                        viewBirthday.setText("生日：未设定");
                        viewBMI.setText("体征：未设定 / 未设定");
                        viewHistory.setText("暂无记录");

                        etName.setText(data.realName != null ? data.realName : "");
                        etBirthday.setText("");
                        etHeight.setText("");
                        etWeight.setText("");
                        etHistory.setText("");

                        tryPreFillFromUserInfo();
                        btnEditTop.setVisibility(View.GONE);
                        switchMode(true);
                    }

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
        Toast.makeText(getContext(), "请填写您的健康信息", Toast.LENGTH_SHORT).show();
    }

    private void saveArchive() {
        String birthday = etBirthday.getText().toString().trim();
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();

        if (TextUtils.isEmpty(birthday)) {
            Toast.makeText(getContext(), "请填写生日", Toast.LENGTH_SHORT).show();
            return;
        }

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

            String gender = "";
            if (rbMale.isChecked()) {
                gender = "男";
            } else if (rbFemale.isChecked()) {
                gender = "女";
            } else {
                Toast.makeText(getContext(), "请选择性别", Toast.LENGTH_SHORT).show();
                return;
            }

            String medicalHistory = etHistory.getText().toString().trim();

            Archive archive = new Archive();
            archive.userId = currentUserId;
            archive.gender = gender;
            archive.birthday = birthday;
            archive.height = height;
            archive.weight = weight;
            archive.medicalHistory = medicalHistory;

            showLoading(true);
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
                            loadArchive();
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

    // ========== 自定义指标方法 ==========

    private void loadMetrics() {
        apiService.getCustomMetrics(currentUserId).enqueue(new Callback<List<CustomMetric>>() {
            @Override
            public void onResponse(Call<List<CustomMetric>> call, Response<List<CustomMetric>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    metricList = response.body();
                    updateMetricTabs();

                    if (!metricList.isEmpty()) {
                        metricEmptyState.setVisibility(View.GONE);
                        metricTabsScroll.setVisibility(View.VISIBLE);
                        if (selectedMetricId == null) {
                            selectedMetricId = metricList.get(0).id;
                            loadRecords();
                        }
                    } else {
                        metricEmptyState.setVisibility(View.VISIBLE);
                        metricTabsScroll.setVisibility(View.GONE);
                        metricDateLayout.setVisibility(View.GONE);
                        btnAddRecord.setVisibility(View.GONE);
                        metricRecordsContainer.removeAllViews();
                        recordEmptyState.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<CustomMetric>> call, Throwable t) {
                Log.e("ArchiveFragment", "加载指标失败: " + t.getMessage());
            }
        });
    }

    private void updateMetricTabs() {
        metricTabsContainer.removeAllViews();

        for (CustomMetric metric : metricList) {
            TextView tab = createMetricTab(metric);
            metricTabsContainer.addView(tab);
        }
    }

    private TextView createMetricTab(CustomMetric metric) {
        TextView tab = new TextView(getContext());
        tab.setText(metric.metricName + (metric.unit != null ? " (" + metric.unit + ")" : ""));
        tab.setTextSize(14);
        tab.setPadding(16, 8, 16, 8);
        tab.setGravity(android.view.Gravity.CENTER);

        if (metric.id.equals(selectedMetricId)) {
            tab.setTextColor(0xFF3B82F6);
            tab.setBackgroundResource(R.drawable.tab_selected_bg);
        } else {
            tab.setTextColor(0xFF64748B);
            tab.setBackgroundResource(R.drawable.tab_unselected_bg);
        }

        tab.setOnClickListener(v -> {
            selectedMetricId = metric.id;
            updateMetricTabs();
            loadRecords();
        });

        tab.setOnLongClickListener(v -> {
            showDeleteMetricDialog(metric);
            return true;
        });

        return tab;
    }

    private void loadRecords() {
        if (selectedMetricId == null) {
            metricDateLayout.setVisibility(View.GONE);
            btnAddRecord.setVisibility(View.GONE);
            metricRecordsContainer.removeAllViews();
            recordEmptyState.setVisibility(View.GONE);
            return;
        }

        metricDateLayout.setVisibility(View.VISIBLE);
        btnAddRecord.setVisibility(View.VISIBLE);

        String currentDate = dateFormat.format(currentCalendar.getTime());

        apiService.getMetricRecords(currentUserId, selectedMetricId).enqueue(new Callback<List<CustomMetricRecord>>() {
            @Override
            public void onResponse(Call<List<CustomMetricRecord>> call, Response<List<CustomMetricRecord>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    recordList = response.body();

                    // 过滤出当前日期的记录
                    List<CustomMetricRecord> todayRecords = new ArrayList<>();
                    for (CustomMetricRecord record : recordList) {
                        if (currentDate.equals(record.recordDate)) {
                            todayRecords.add(record);
                        }
                    }

                    updateRecordsUI(todayRecords);
                }
            }

            @Override
            public void onFailure(Call<List<CustomMetricRecord>> call, Throwable t) {
                Log.e("ArchiveFragment", "加载记录失败: " + t.getMessage());
            }
        });
    }

    private void updateRecordsUI(List<CustomMetricRecord> records) {
        metricRecordsContainer.removeAllViews();

        if (records.isEmpty()) {
            recordEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        recordEmptyState.setVisibility(View.GONE);

        for (CustomMetricRecord record : records) {
            View recordView = createRecordView(record);
            metricRecordsContainer.addView(recordView);
        }
    }

    private View createRecordView(CustomMetricRecord record) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.item_metric_record, metricRecordsContainer, false);

        TextView tvMetricName = view.findViewById(R.id.tvMetricName);
        TextView tvRecordValue = view.findViewById(R.id.tvRecordValue);
        TextView tvUnit = view.findViewById(R.id.tvUnit);
        TextView tvNote = view.findViewById(R.id.tvNote);
        TextView tvRecordTime = view.findViewById(R.id.tvRecordTime);
        TextView btnDeleteRecord = view.findViewById(R.id.btnDeleteRecord);

        // 查找指标名称和单位
        for (CustomMetric metric : metricList) {
            if (metric.id.equals(record.metricId)) {
                tvMetricName.setText(metric.metricName);
                tvUnit.setText(metric.unit != null ? metric.unit : "");
                break;
            }
        }

        tvRecordValue.setText(record.recordValue);
        tvNote.setText(!TextUtils.isEmpty(record.note) ? record.note : "无备注");

        if (record.createdAt != null && record.createdAt.length() >= 16) {
            tvRecordTime.setText("记录于 " + record.createdAt.substring(11, 16));
        } else {
            tvRecordTime.setText("");
        }

        btnDeleteRecord.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除这条记录吗？")
                    .setPositiveButton("确定", (dialog, which) -> deleteRecord(record.id))
                    .setNegativeButton("取消", null)
                    .show();
        });

        return view;
    }

    private void showAddMetricDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("添加新指标");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView tvNameLabel = new TextView(getContext());
        tvNameLabel.setText("指标名称 *");
        tvNameLabel.setPadding(0, 0, 0, 4);
        layout.addView(tvNameLabel);

        EditText etMetricName = new EditText(getContext());
        etMetricName.setHint("如：血压、血糖");
        etMetricName.setBackgroundColor(0xFFF3F4F6);
        etMetricName.setMinHeight(48);
        layout.addView(etMetricName);

        TextView tvUnitLabel = new TextView(getContext());
        tvUnitLabel.setText("单位");
        tvUnitLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvUnitLabel);

        EditText etUnit = new EditText(getContext());
        etUnit.setHint("如：mmHg、mmol/L");
        etUnit.setBackgroundColor(0xFFF3F4F6);
        etUnit.setMinHeight(48);
        layout.addView(etUnit);

        builder.setView(layout);

        builder.setPositiveButton("添加", (dialog, which) -> {
            String name = etMetricName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(getContext(), "请输入指标名称", Toast.LENGTH_SHORT).show();
                return;
            }

            CustomMetric metric = new CustomMetric();
            metric.userId = currentUserId;
            metric.metricName = name;
            metric.unit = etUnit.getText().toString().trim();

            apiService.addCustomMetric(metric).enqueue(new Callback<SmsResponse>() {
                @Override
                public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                        Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();
                        loadMetrics();
                    } else {
                        Toast.makeText(getContext(), "添加失败", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<SmsResponse> call, Throwable t) {
                    Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showDeleteMetricDialog(CustomMetric metric) {
        new AlertDialog.Builder(getContext())
                .setTitle("删除指标")
                .setMessage("确定要删除指标 \"" + metric.metricName + "\" 吗？\n删除后所有相关记录也会被删除。")
                .setPositiveButton("删除", (dialog, which) -> {
                    apiService.deleteCustomMetric(metric.id).enqueue(new Callback<SmsResponse>() {
                        @Override
                        public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                                Toast.makeText(getContext(), "删除成功", Toast.LENGTH_SHORT).show();
                                if (metric.id.equals(selectedMetricId)) {
                                    selectedMetricId = null;
                                }
                                loadMetrics();
                            } else {
                                Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<SmsResponse> call, Throwable t) {
                            Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddRecordDialog() {
        CustomMetric selectedMetric = null;
        for (CustomMetric metric : metricList) {
            if (metric.id.equals(selectedMetricId)) {
                selectedMetric = metric;
                break;
            }
        }

        if (selectedMetric == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("添加记录 - " + selectedMetric.metricName);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView tvValueLabel = new TextView(getContext());
        tvValueLabel.setText("数值 *");
        tvValueLabel.setPadding(0, 0, 0, 4);
        layout.addView(tvValueLabel);

        EditText etValue = new EditText(getContext());
        etValue.setHint("如：" + (selectedMetric.unit != null ? selectedMetric.unit : "输入数值"));
        etValue.setBackgroundColor(0xFFF3F4F6);
        etValue.setMinHeight(48);
        layout.addView(etValue);

        TextView tvNoteLabel = new TextView(getContext());
        tvNoteLabel.setText("备注");
        tvNoteLabel.setPadding(0, 12, 0, 4);
        layout.addView(tvNoteLabel);

        EditText etNote = new EditText(getContext());
        etNote.setHint("如：早餐前、睡前");
        etNote.setBackgroundColor(0xFFF3F4F6);
        etNote.setMinHeight(48);
        layout.addView(etNote);

        builder.setView(layout);

        builder.setPositiveButton("添加", (dialog, which) -> {
            String value = etValue.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                Toast.makeText(getContext(), "请输入数值", Toast.LENGTH_SHORT).show();
                return;
            }

            CustomMetricRecord record = new CustomMetricRecord();
            record.userId = currentUserId;
            record.metricId = selectedMetricId;
            record.recordValue = value;
            record.note = etNote.getText().toString().trim();
            record.recordDate = dateFormat.format(currentCalendar.getTime());

            apiService.addMetricRecord(record).enqueue(new Callback<SmsResponse>() {
                @Override
                public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                        Toast.makeText(getContext(), "记录成功", Toast.LENGTH_SHORT).show();
                        loadRecords();
                    } else {
                        Toast.makeText(getContext(), "添加失败", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<SmsResponse> call, Throwable t) {
                    Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void deleteRecord(Long recordId) {
        apiService.deleteMetricRecord(recordId).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                    Toast.makeText(getContext(), "删除成功", Toast.LENGTH_SHORT).show();
                    loadRecords();
                } else {
                    Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SmsResponse> call, Throwable t) {
                Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDateDisplay() {
        tvSelectedDate.setText(dateFormat.format(currentCalendar.getTime()));
    }
}