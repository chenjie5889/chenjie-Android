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
        addRecentWeekMedicationStats();

        // 3. 添加用药习惯分析
        addMedicationHabitsStats();

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
    private void addRecentWeekMedicationStats() {
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
        Map<String, int[]> dailyStats = new HashMap<>();

        for (int i = 6; i >= 0; i--) {
            Calendar dayCal = (Calendar) calendar.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, -i);
            String dateStr = dateFormat.format(dayCal.getTime());
            dailyStats.put(dateStr, new int[]{0, 0}); // [总次数, 按时次数]
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
                }
            }
        }

        // 创建柱状图
        BarChart barChart = new BarChart(this);
        barChart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
        ));

        // 创建两个数据集：按时和漏服
        ArrayList<BarEntry> onTimeEntries = new ArrayList<>();
        ArrayList<BarEntry> missedEntries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        int index = 0;
        for (Map.Entry<String, int[]> entry : dailyStats.entrySet()) {
            String dateStr = entry.getKey();
            int[] stats = entry.getValue();
            onTimeEntries.add(new BarEntry(index, stats[1]));
            missedEntries.add(new BarEntry(index, stats[0] - stats[1]));

            // 获取显示标签
            try {
                Date date = dateFormat.parse(dateStr);
                labels.add(monthDayFormat.format(date));
            } catch (ParseException e) {
                labels.add(dateStr.substring(5));
            }
            index++;
        }

        BarDataSet onTimeDataSet = new BarDataSet(onTimeEntries, "按时服药");
        onTimeDataSet.setColor(0xFF3B82F6);
        onTimeDataSet.setValueTextColor(0xFF1F2937);
        onTimeDataSet.setValueTextSize(10f);

        BarDataSet missedDataSet = new BarDataSet(missedEntries, "漏服");
        missedDataSet.setColor(0xFFEF4444);
        missedDataSet.setValueTextColor(0xFF1F2937);
        missedDataSet.setValueTextSize(10f);

        BarData barData = new BarData(onTimeDataSet, missedDataSet);
        barData.setBarWidth(0.45f); // 设置柱宽
        barData.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value > 0 ? String.valueOf((int) value) : "";
            }
        });

        barChart.setData(barData);
        barChart.groupBars(0f, 0.1f, 0.05f); // 分组显示

        // 配置X轴
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);

        // 配置Y轴
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);
        barChart.getAxisRight().setEnabled(false);

        // 配置图例
        Legend legend = barChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);

        Description description = new Description();
        description.setText("近7天用药情况");
        barChart.setDescription(description);
        barChart.animateY(1000);
        barChart.setExtraOffsets(10, 10, 10, 10);
        barChart.invalidate();

        contentLayout.addView(barChart);

        // 添加说明文字
        TextView tvNote = new TextView(this);
        tvNote.setText("蓝色: 按时服药  红色: 漏服");
        tvNote.setTextColor(0xFF6B7280);
        tvNote.setTextSize(12);
        tvNote.setPadding(0, 16, 0, 0);
        tvNote.setGravity(android.view.Gravity.CENTER);
        contentLayout.addView(tvNote);

        containerStats.addView(cardView);
    }

    /**
     * 用药习惯分析
     */
    private void addMedicationHabitsStats() {
        if (medicationLogs.isEmpty()) {
            return;
        }

        View cardView = createStatCard("⏰ 用药习惯分析");
        LinearLayout contentLayout = cardView.findViewById(R.id.cardContent);

        // 统计各时间段的服药情况 - 由于 MedicationLogResponse 可能没有 takeTime，这里简化处理
        // 实际使用时，如果后端返回的数据包含 takeTime，可以取消注释
        /*
        Map<String, int[]> timeStats = new HashMap<>();
        timeStats.put("早上 (06:00-10:00)", new int[]{0, 0});
        timeStats.put("中午 (11:00-13:00)", new int[]{0, 0});
        timeStats.put("晚上 (17:00-21:00)", new int[]{0, 0});
        timeStats.put("睡前 (21:00-23:00)", new int[]{0, 0});
        timeStats.put("其他时间", new int[]{0, 0});

        for (MedicationLogResponse log : medicationLogs) {
            String takeTime = log.takeTime;
            if (takeTime == null || takeTime.isEmpty()) {
                continue;
            }

            try {
                String[] parts = takeTime.split(":");
                int hour = Integer.parseInt(parts[0]);

                String period = getTimePeriod(hour);
                int[] stats = timeStats.get(period);
                if (stats != null) {
                    stats[0]++; // 总次数
                    if (log.status != null && log.status == 1) {
                        stats[1]++; // 按时次数
                    }
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        // 显示各时间段统计
        boolean hasData = false;
        for (Map.Entry<String, int[]> entry : timeStats.entrySet()) {
            String period = entry.getKey();
            int[] stats = entry.getValue();

            if (stats[0] == 0) {
                continue;
            }

            hasData = true;
            double rate = stats[1] * 100.0 / stats[0];

            LinearLayout periodLayout = new LinearLayout(this);
            periodLayout.setOrientation(LinearLayout.HORIZONTAL);
            periodLayout.setPadding(0, 8, 0, 8);

            TextView tvPeriod = new TextView(this);
            tvPeriod.setText(period);
            tvPeriod.setTextColor(0xFF374151);
            tvPeriod.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvRate = new TextView(this);
            tvRate.setText(String.format(Locale.getDefault(), "%.0f%% (%d/%d)", rate, stats[1], stats[0]));
            tvRate.setTextColor(rate >= 80 ? 0xFF10B981 : (rate >= 60 ? 0xFFF59E0B : 0xFFEF4444));
            tvRate.setTypeface(null, android.graphics.Typeface.BOLD);

            periodLayout.addView(tvPeriod);
            periodLayout.addView(tvRate);
            contentLayout.addView(periodLayout);
        }

        if (!hasData) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("暂无时间段统计数据");
            tvEmpty.setTextColor(0xFF6B7280);
            tvEmpty.setPadding(0, 16, 0, 16);
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            contentLayout.addView(tvEmpty);
        }
        */

        // 由于缺少时间数据，暂时显示提示信息
        TextView tvInfo = new TextView(this);
        tvInfo.setText("当前版本暂不支持按时间段统计，后续版本将完善此功能");
        tvInfo.setTextColor(0xFF6B7280);
        tvInfo.setPadding(0, 16, 0, 16);
        tvInfo.setGravity(android.view.Gravity.CENTER);
        contentLayout.addView(tvInfo);

        containerStats.addView(cardView);
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