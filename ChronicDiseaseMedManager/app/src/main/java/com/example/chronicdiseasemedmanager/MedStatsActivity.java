package com.example.chronicdiseasemedmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MedStatsActivity extends AppCompatActivity {

    private static final String TAG = "MedStatsActivity";

    private LinearLayout containerStats;
    private LinearLayout tvLoading;  // 改为 LinearLayout
    private LinearLayout tvNoData;    // 改为 LinearLayout
    private Button btnRefresh;
    private ScrollView scrollView;
    private Toolbar toolbar;

    private ApiService apiService;
    private Long currentUserId;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private SimpleDateFormat monthDayFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());

    // 数据集合 - 使用 MedicationLogResponse 而不是 MedicationLog
    private List<MedicationLogResponse> medicationLogs = new ArrayList<>();
    private List<CustomMetric> metricList = new ArrayList<>();
    private Map<Long, List<CustomMetricRecord>> metricRecordsMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_med_stats);

        initViews();
        initData();
        loadData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("数据统计与分析");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        containerStats = findViewById(R.id.containerStats);
        tvLoading = findViewById(R.id.loadingLayout);
        tvNoData = findViewById(R.id.noDataLayout);
        btnRefresh = findViewById(R.id.btnRefresh);
        scrollView = findViewById(R.id.scrollView);

        btnRefresh.setOnClickListener(v -> {
            tvLoading.setVisibility(View.VISIBLE);
            scrollView.setVisibility(View.GONE);
            tvNoData.setVisibility(View.GONE);
            containerStats.removeAllViews();
            loadData();
        });
    }

    private void initData() {
        SharedPreferences sp = getSharedPreferences("user_info", Context.MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);

        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.71.67:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void loadData() {
        if (currentUserId == -1L) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 加载用药记录 - 使用 getMedLogsByDate 或 getTodayMedicationLogs
        loadMedicationLogs();
    }

    private void loadMedicationLogs() {
        // 使用 getMedLogs 或 getTodayMedicationLogs，这里使用 getMedLogs
        // 注意：根据您的 ApiService，getMedLogs 返回 List<MedicationLog>，但 MedicationLog 没有 takeTime
        // 所以我们使用 getTodayMedicationLogs 或 getMedLogsByDate 来获取 MedicationLogResponse
        apiService.getMedLogs(currentUserId).enqueue(new Callback<List<MedicationLog>>() {
            @Override
            public void onResponse(Call<List<MedicationLog>> call, Response<List<MedicationLog>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // 将 MedicationLog 转换为 MedicationLogResponse
                    List<MedicationLog> logs = response.body();
                    medicationLogs.clear();
                    for (MedicationLog log : logs) {
                        MedicationLogResponse resp = new MedicationLogResponse();
                        resp.logDate = log.logDate;
                        resp.status = log.status;
                        // 注意：MedicationLog 没有 medicineName 和 takeTime，这里使用默认值
                        resp.medicineName = "未知药品";
                        resp.takeTime = "未知时间";
                        medicationLogs.add(resp);
                    }
                    Log.d(TAG, "用药记录加载成功，数量: " + medicationLogs.size());

                    // 加载自定义指标数据
                    loadCustomMetrics();
                } else {
                    showError("加载用药记录失败");
                }
            }

            @Override
            public void onFailure(Call<List<MedicationLog>> call, Throwable t) {
                Log.e(TAG, "加载用药记录失败: " + t.getMessage());
                showError("网络错误: " + t.getMessage());
            }
        });
    }

    private void loadCustomMetrics() {
        apiService.getCustomMetrics(currentUserId).enqueue(new Callback<List<CustomMetric>>() {
            @Override
            public void onResponse(Call<List<CustomMetric>> call, Response<List<CustomMetric>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    metricList = response.body();
                    Log.d(TAG, "自定义指标加载成功，数量: " + metricList.size());

                    if (metricList.isEmpty()) {
                        // 如果没有自定义指标，直接显示用药统计
                        displayAllStats();
                    } else {
                        // 加载每个指标的记录
                        loadAllMetricRecords(0);
                    }
                } else {
                    displayAllStats();
                }
            }

            @Override
            public void onFailure(Call<List<CustomMetric>> call, Throwable t) {
                Log.e(TAG, "加载自定义指标失败: " + t.getMessage());
                displayAllStats();
            }
        });
    }

    private void loadAllMetricRecords(final int index) {
        if (index >= metricList.size()) {
            // 所有指标记录加载完成
            displayAllStats();
            return;
        }

        CustomMetric metric = metricList.get(index);
        apiService.getMetricRecords(currentUserId, metric.id).enqueue(new Callback<List<CustomMetricRecord>>() {
            @Override
            public void onResponse(Call<List<CustomMetricRecord>> call, Response<List<CustomMetricRecord>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    metricRecordsMap.put(metric.id, response.body());
                }
                loadAllMetricRecords(index + 1);
            }

            @Override
            public void onFailure(Call<List<CustomMetricRecord>> call, Throwable t) {
                Log.e(TAG, "加载指标记录失败: " + t.getMessage());
                loadAllMetricRecords(index + 1);
            }
        });
    }

    private void showError(String message) {
        tvLoading.setVisibility(View.GONE);
        tvNoData.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void displayAllStats() {
        tvLoading.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);

        if (medicationLogs.isEmpty() && metricRecordsMap.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            return;
        }

        tvNoData.setVisibility(View.GONE);
        containerStats.removeAllViews();

        // 1. 添加用药依从性统计
        addMedicationComplianceStats();

        // 2. 添加近7天用药情况
        addRecentWeekMedStats();

        // 3. 添加用药习惯分析
        // addMedicationHabitsStats();

        // 4. 添加病情变化趋势（自定义指标）
        addHealthTrendStats();
    }

    /**
     * 创建统计卡片
     */
    private View createStatCard(String title) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View cardView = inflater.inflate(R.layout.item_stat_card, containerStats, false);

        TextView tvTitle = cardView.findViewById(R.id.tvCardTitle);
        tvTitle.setText(title);

        return cardView;
    }

    /**
     * 用药依从性统计
     */
    private void addMedicationComplianceStats() {
        View cardView = createStatCard("💊 用药依从性统计");
        LinearLayout contentLayout = cardView.findViewById(R.id.cardContent);

        if (medicationLogs.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("暂无用药记录数据");
            tvEmpty.setTextColor(0xFF6B7280);
            tvEmpty.setPadding(0, 16, 0, 16);
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            contentLayout.addView(tvEmpty);
            containerStats.addView(cardView);
            return;
        }

        // 计算总记录数和按时服药数
        int totalRecords = medicationLogs.size();
        int onTimeCount = 0;
        int missedCount = 0;

        for (MedicationLogResponse log : medicationLogs) {
            if (log.status != null && log.status == 1) {
                onTimeCount++;
            } else {
                missedCount++;
            }
        }

        double complianceRate = totalRecords > 0 ? (onTimeCount * 100.0 / totalRecords) : 0;

        // 显示总体依从性
        LinearLayout rateLayout = new LinearLayout(this);
        rateLayout.setOrientation(LinearLayout.HORIZONTAL);
        rateLayout.setPadding(0, 8, 0, 16);

        TextView tvRateLabel = new TextView(this);
        tvRateLabel.setText("总体依从性: ");
        tvRateLabel.setTextColor(0xFF374151);
        tvRateLabel.setTextSize(16);

        TextView tvRateValue = new TextView(this);
        tvRateValue.setText(String.format(Locale.getDefault(), "%.1f%%", complianceRate));
        tvRateValue.setTextColor(complianceRate >= 80 ? 0xFF10B981 : (complianceRate >= 60 ? 0xFFF59E0B : 0xFFEF4444));
        tvRateValue.setTextSize(20);
        tvRateValue.setTypeface(null, android.graphics.Typeface.BOLD);

        rateLayout.addView(tvRateLabel);
        rateLayout.addView(tvRateValue);

        // 添加统计详情
        TextView tvStats = new TextView(this);
        tvStats.setText(String.format(Locale.getDefault(), "按时: %d次  漏服: %d次  总计: %d次", onTimeCount, missedCount, totalRecords));
        tvStats.setTextColor(0xFF4B5563);
        tvStats.setTextSize(14);
        tvStats.setPadding(0, 0, 0, 16);

        // 添加进度条
        View progressBar = new View(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                0, 24
        );
        progressParams.weight = (float) onTimeCount / totalRecords;
        progressBar.setLayoutParams(progressParams);
        progressBar.setBackgroundColor(0xFF3B82F6);

        View missedProgressBar = new View(this);
        LinearLayout.LayoutParams missedParams = new LinearLayout.LayoutParams(
                0, 24
        );
        missedParams.weight = (float) missedCount / totalRecords;
        missedProgressBar.setLayoutParams(missedParams);
        missedProgressBar.setBackgroundColor(0xFFEF4444);

        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setOrientation(LinearLayout.HORIZONTAL);
        progressContainer.setBackgroundColor(0xFFE5E7EB);
        progressContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                24
        ));
        progressContainer.addView(progressBar);
        progressContainer.addView(missedProgressBar);

        // 添加评价
        TextView tvEvaluation = new TextView(this);
        tvEvaluation.setPadding(0, 16, 0, 8);
        tvEvaluation.setTextSize(14);

        if (complianceRate >= 90) {
            tvEvaluation.setText("👍 优秀！您有非常好的用药依从性，请继续保持！");
            tvEvaluation.setTextColor(0xFF10B981);
        } else if (complianceRate >= 75) {
            tvEvaluation.setText("👌 良好！大多数时间都能按时服药，继续坚持！");
            tvEvaluation.setTextColor(0xFF3B82F6);
        } else if (complianceRate >= 60) {
            tvEvaluation.setText("⚠️ 一般！需要加强用药规律性，设置提醒会有帮助");
            tvEvaluation.setTextColor(0xFFF59E0B);
        } else {
            tvEvaluation.setText("❗ 需要改进！经常漏服可能影响治疗效果，请重视用药提醒");
            tvEvaluation.setTextColor(0xFFEF4444);
        }

        contentLayout.addView(rateLayout);
        contentLayout.addView(tvStats);
        contentLayout.addView(progressContainer);
        contentLayout.addView(tvEvaluation);

        containerStats.addView(cardView);
    }

    /**
     * 近7天用药情况
     */
    private void addRecentWeekMedStats() {
        View cardView = createStatCard("📅 近7天用药情况");
        LinearLayout contentLayout = cardView.findViewById(R.id.cardContent);

        if (medicationLogs.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("暂无用药记录数据");
            tvEmpty.setTextColor(0xFF6B7280);
            tvEmpty.setPadding(0, 16, 0, 16);
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            contentLayout.addView(tvEmpty);
            containerStats.addView(cardView);
            return;
        }

        // 获取最近7天的日期
        Calendar calendar = Calendar.getInstance();
        List<String> dateList = new ArrayList<>(); // 存储日期字符串 yyyy-MM-dd
        List<String> dateLabels = new ArrayList<>(); // 存储显示标签
        Map<String, int[]> dailyStats = new HashMap<>(); // 用于快速查找

        // 按时间顺序添加7天（从最早到最新）
        for (int i = 6; i >= 0; i--) {
            Calendar dayCal = (Calendar) calendar.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, -i);
            String dateStr = dateFormat.format(dayCal.getTime());
            dateList.add(dateStr);
            dailyStats.put(dateStr, new int[]{0, 0, 0}); // [总次数, 按时次数, 漏服次数]

            // 生成显示标签
            String displayLabel;
            if (i == 0) {
                displayLabel = "今天";
            } else if (i == 1) {
                displayLabel = "昨天";
            } else {
                displayLabel = monthDayFormat.format(dayCal.getTime());
            }
            dateLabels.add(displayLabel);
        }

        // 统计每天的数据
        for (MedicationLogResponse log : medicationLogs) {
            if (log.logDate == null) continue;

            String logDate = log.logDate;
            if (dailyStats.containsKey(logDate)) {
                int[] stats = dailyStats.get(logDate);
                stats[0]++; // 总次数
                if (log.status != null && log.status == 1) {
                    stats[1]++; // 按时次数
                } else {
                    stats[2]++; // 漏服次数
                }
            }
        }

        // 打印调试信息
        Log.d(TAG, "=== 近7天用药数据 ===");
        for (int i = 0; i < dateList.size(); i++) {
            String date = dateList.get(i);
            int[] stats = dailyStats.get(date);
            Log.d(TAG, "日期: " + dateLabels.get(i) + "(" + date + ")" +
                    ", 总次数: " + stats[0] +
                    ", 按时: " + stats[1] +
                    ", 漏服: " + stats[2]);
        }

        // 创建堆叠柱状图
        BarChart barChart = new BarChart(this);
        barChart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
        ));

        // 创建数据集：按时和漏服（堆叠显示）
        ArrayList<BarEntry> combinedEntries = new ArrayList<>();

        // 按dateList的顺序添加数据
        for (int i = 0; i < dateList.size(); i++) {
            String date = dateList.get(i);
            int[] stats = dailyStats.get(date);

            // 创建堆叠柱状图的数据点
            // y值数组：[按时次数, 漏服次数]
            combinedEntries.add(new BarEntry(i, new float[]{stats[1], stats[2]}));
        }

        // 创建堆叠数据集
        BarDataSet combinedDataSet = new BarDataSet(combinedEntries, "用药情况");
        combinedDataSet.setColors(new int[]{0xFF3B82F6, 0xFFEF4444}); // 蓝色(按时), 红色(漏服)
        combinedDataSet.setStackLabels(new String[]{"按时", "漏服"});
        // 关键修改：禁用数值显示
        combinedDataSet.setDrawValues(false);

        BarData barData = new BarData(combinedDataSet);
        barData.setBarWidth(0.7f); // 设置柱宽
        // 确保不显示数值
        barData.setDrawValues(false);

        barChart.setData(barData);
        barChart.setDrawValueAboveBar(false); // 确保不显示数值

        // 配置X轴
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(0);

        // 配置Y轴
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0xFFE5E7EB);
        barChart.getAxisRight().setEnabled(false);

        // 配置图例
        Legend legend = barChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextSize(12f);

        Description description = new Description();
        description.setText("");
        barChart.setDescription(description);

        barChart.setBackgroundColor(0xFFFFFFFF);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(false); // 确保不显示数值
        barChart.setPinchZoom(false);
        barChart.setScaleEnabled(false);
        barChart.setDoubleTapToZoomEnabled(false);
        barChart.animateY(1000);
        barChart.setExtraOffsets(10, 20, 10, 10);
        barChart.invalidate();

        contentLayout.addView(barChart);

        // 添加图例说明
        LinearLayout legendLayout = new LinearLayout(this);
        legendLayout.setOrientation(LinearLayout.HORIZONTAL);
        legendLayout.setGravity(android.view.Gravity.CENTER);
        legendLayout.setPadding(0, 16, 0, 0);

        // 蓝色图例
        LinearLayout blueLegend = new LinearLayout(this);
        blueLegend.setOrientation(LinearLayout.HORIZONTAL);
        blueLegend.setPadding(16, 0, 16, 0);

        View blueDot = new View(this);
        blueDot.setLayoutParams(new LinearLayout.LayoutParams(16, 16));
        blueDot.setBackgroundColor(0xFF3B82F6);

        TextView blueText = new TextView(this);
        blueText.setText("按时服药");
        blueText.setTextColor(0xFF4B5563);
        blueText.setTextSize(12);
        blueText.setPadding(8, 0, 0, 0);

        blueLegend.addView(blueDot);
        blueLegend.addView(blueText);

        // 红色图例
        LinearLayout redLegend = new LinearLayout(this);
        redLegend.setOrientation(LinearLayout.HORIZONTAL);
        redLegend.setPadding(16, 0, 16, 0);

        View redDot = new View(this);
        redDot.setLayoutParams(new LinearLayout.LayoutParams(16, 16));
        redDot.setBackgroundColor(0xFFEF4444);

        TextView redText = new TextView(this);
        redText.setText("漏服");
        redText.setTextColor(0xFF4B5563);
        redText.setTextSize(12);
        redText.setPadding(8, 0, 0, 0);

        redLegend.addView(redDot);
        redLegend.addView(redText);

        legendLayout.addView(blueLegend);
        legendLayout.addView(redLegend);

        contentLayout.addView(legendLayout);

        // 添加数据表格
        addDataTable(contentLayout, dateList, dateLabels, dailyStats);

        containerStats.addView(cardView);
    }


    /**
     * 添加数据表格
     */
    private void addDataTable(LinearLayout parent, List<String> dateList, List<String> dateLabels, Map<String, int[]> dailyStats) {
        // 创建表格布局
        LinearLayout tableLayout = new LinearLayout(this);
        tableLayout.setOrientation(LinearLayout.VERTICAL);
        tableLayout.setPadding(0, 16, 0, 0);

        // 表格标题
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setPadding(8, 8, 8, 8);
        headerRow.setBackgroundColor(0xFFF3F4F6);

        TextView headerDate = new TextView(this);
        headerDate.setText("日期");
        headerDate.setTextColor(0xFF374151);
        headerDate.setTextSize(14);
        headerDate.setTypeface(null, android.graphics.Typeface.BOLD);
        headerDate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView headerOnTime = new TextView(this);
        headerOnTime.setText("按时");
        headerOnTime.setTextColor(0xFF3B82F6);
        headerOnTime.setTextSize(14);
        headerOnTime.setTypeface(null, android.graphics.Typeface.BOLD);
        headerOnTime.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headerOnTime.setGravity(android.view.Gravity.CENTER);

        TextView headerMissed = new TextView(this);
        headerMissed.setText("漏服");
        headerMissed.setTextColor(0xFFEF4444);
        headerMissed.setTextSize(14);
        headerMissed.setTypeface(null, android.graphics.Typeface.BOLD);
        headerMissed.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headerMissed.setGravity(android.view.Gravity.CENTER);

        headerRow.addView(headerDate);
        headerRow.addView(headerOnTime);
        headerRow.addView(headerMissed);
        tableLayout.addView(headerRow);

        // 表格数据行
        for (int i = 0; i < dateList.size(); i++) {
            String date = dateList.get(i);
            int[] stats = dailyStats.get(date);

            LinearLayout dataRow = new LinearLayout(this);
            dataRow.setOrientation(LinearLayout.HORIZONTAL);
            dataRow.setPadding(8, 12, 8, 12);

            if (i % 2 == 0) {
                dataRow.setBackgroundColor(0xFFFFFFFF);
            } else {
                dataRow.setBackgroundColor(0xFFF9FAFB);
            }

            TextView tvDate = new TextView(this);
            tvDate.setText(dateLabels.get(i));
            tvDate.setTextColor(0xFF4B5563);
            tvDate.setTextSize(14);
            tvDate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvOnTime = new TextView(this);
            tvOnTime.setText(String.valueOf(stats[1]));
            tvOnTime.setTextColor(0xFF3B82F6);
            tvOnTime.setTextSize(14);
            tvOnTime.setTypeface(null, android.graphics.Typeface.BOLD);
            tvOnTime.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tvOnTime.setGravity(android.view.Gravity.CENTER);

            TextView tvMissed = new TextView(this);
            tvMissed.setText(String.valueOf(stats[2]));
            tvMissed.setTextColor(0xFFEF4444);
            tvMissed.setTextSize(14);
            tvMissed.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMissed.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tvMissed.setGravity(android.view.Gravity.CENTER);

            dataRow.addView(tvDate);
            dataRow.addView(tvOnTime);
            dataRow.addView(tvMissed);

            tableLayout.addView(dataRow);
        }

        parent.addView(tableLayout);
    }

    /**
     * 病情变化趋势
     */
    private void addHealthTrendStats() {
        if (metricRecordsMap.isEmpty()) {
            return;
        }

        for (CustomMetric metric : metricList) {
            List<CustomMetricRecord> records = metricRecordsMap.get(metric.id);
            if (records == null || records.isEmpty()) {
                continue;
            }

            // 按日期排序
            Collections.sort(records, new Comparator<CustomMetricRecord>() {
                @Override
                public int compare(CustomMetricRecord o1, CustomMetricRecord o2) {
                    return o1.recordDate.compareTo(o2.recordDate);
                }
            });

            // 取最近10条记录
            int start = Math.max(0, records.size() - 10);
            List<CustomMetricRecord> recentRecords = records.subList(start, records.size());

            View cardView = createStatCard("📈 " + metric.metricName + "变化趋势 (" + (metric.unit != null ? metric.unit : "") + ")");
            LinearLayout contentLayout = cardView.findViewById(R.id.cardContent);

            // 创建折线图
            LineChart lineChart = new LineChart(this);
            lineChart.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    350
            ));

            ArrayList<Entry> entries = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();

            boolean hasValidData = false;
            for (int i = 0; i < recentRecords.size(); i++) {
                CustomMetricRecord record = recentRecords.get(i);
                try {
                    // 尝试解析数值（如果是血压值如"120/80"，取收缩压）
                    float value = parseMetricValue(record.recordValue);
                    entries.add(new Entry(i, value));
                    hasValidData = true;

                    // 显示短日期
                    try {
                        Date date = dateFormat.parse(record.recordDate);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        labels.add((cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.DAY_OF_MONTH));
                    } catch (ParseException e) {
                        labels.add(record.recordDate.substring(5));
                    }
                } catch (NumberFormatException e) {
                    // 忽略无法解析的记录
                }
            }

            if (!hasValidData) {
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText("无法解析数值数据");
                tvEmpty.setTextColor(0xFF6B7280);
                tvEmpty.setPadding(0, 16, 0, 16);
                tvEmpty.setGravity(android.view.Gravity.CENTER);
                contentLayout.addView(tvEmpty);
                containerStats.addView(cardView);
                continue;
            }

            LineDataSet dataSet = new LineDataSet(entries, metric.metricName);
            dataSet.setColor(0xFF3B82F6);
            dataSet.setCircleColor(0xFF3B82F6);
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(4f);
            dataSet.setValueTextColor(0xFF1F2937);
            dataSet.setValueTextSize(9f);
            dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // 平滑曲线
            dataSet.setDrawValues(false);

            ArrayList<ILineDataSet> dataSets = new ArrayList<>();
            dataSets.add(dataSet);

            LineData lineData = new LineData(dataSets);
            lineChart.setData(lineData);

            // 配置X轴
            XAxis xAxis = lineChart.getXAxis();
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setGranularity(1f);
            xAxis.setDrawGridLines(false);

            // 配置Y轴
            YAxis leftAxis = lineChart.getAxisLeft();
            leftAxis.setDrawGridLines(true);
            lineChart.getAxisRight().setEnabled(false);

            Description description = new Description();
            description.setText("近" + recentRecords.size() + "次记录");
            lineChart.setDescription(description);
            lineChart.animateX(1000);
            lineChart.getLegend().setEnabled(false);
            lineChart.setExtraOffsets(10, 10, 10, 10);
            lineChart.invalidate();

            contentLayout.addView(lineChart);

            // 添加参考范围（如果有）
            addReferenceRange(contentLayout, metric.metricName);

            containerStats.addView(cardView);
        }
    }

    private String getTimePeriod(int hour) {
        if (hour >= 6 && hour <= 10) {
            return "早上 (06:00-10:00)";
        } else if (hour >= 11 && hour <= 13) {
            return "中午 (11:00-13:00)";
        } else if (hour >= 17 && hour <= 21) {
            return "晚上 (17:00-21:00)";
        } else if (hour >= 21 && hour <= 23) {
            return "睡前 (21:00-23:00)";
        } else {
            return "其他时间";
        }
    }

    private float parseMetricValue(String value) {
        if (value.contains("/")) {
            // 血压值，取收缩压
            String[] parts = value.split("/");
            return Float.parseFloat(parts[0].trim());
        }
        return Float.parseFloat(value);
    }

    private void addReferenceRange(LinearLayout layout, String metricName) {
        // 根据指标名称添加参考范围
        String reference = "";
        if (metricName.contains("血压")) {
            reference = "参考范围：收缩压 90-140 mmHg，舒张压 60-90 mmHg";
        } else if (metricName.contains("血糖")) {
            reference = "参考范围：空腹 3.9-6.1 mmol/L，餐后 <7.8 mmol/L";
        } else if (metricName.contains("体重")) {
            reference = "建议保持稳定，波动不超过2kg";
        } else if (metricName.contains("心率")) {
            reference = "参考范围：60-100 次/分";
        } else if (metricName.contains("血脂")) {
            reference = "参考范围：总胆固醇 <5.2 mmol/L";
        }

        if (!reference.isEmpty()) {
            TextView tvRef = new TextView(this);
            tvRef.setText(reference);
            tvRef.setTextSize(12);
            tvRef.setTextColor(0xFF6B7280);
            tvRef.setPadding(0, 16, 0, 0);
            layout.addView(tvRef);
        }
    }
}