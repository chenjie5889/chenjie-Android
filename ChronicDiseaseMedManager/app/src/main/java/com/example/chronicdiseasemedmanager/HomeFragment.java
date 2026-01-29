package com.example.chronicdiseasemedmanager;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {

    private CalendarView calendarView;
    private ApiService apiService;
    private Long currentUserId;
    private LinearLayout medicationContainer;
    private LinearLayout medicationLogContainer;
    private TextView tvNoMedication;
    private TextView tvNoLogs;

    // 存储当前的用药提醒信息（从Intent传递）
    private String currentMedicineName = "";
    private String currentDosage = "";
    private int currentRequestCode = 0;
    private boolean isFromNotification = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        calendarView = view.findViewById(R.id.mainCalendar);
        medicationContainer = view.findViewById(R.id.rvTodayMeds);
        medicationLogContainer = view.findViewById(R.id.rvTodayMedLogs);
        tvNoMedication = new TextView(getContext());

        // 检查是否从通知跳转过来
        checkIntentFromNotification();

        // 获取当前用户ID
        SharedPreferences sp = getActivity().getSharedPreferences("user_info", getActivity().MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);

        initRetrofit();

        if (currentUserId != -1L) {
            loadTodayMedications();
            loadTodayMedicationLogs();
            loadMedicationStatus();
        } else {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void checkIntentFromNotification() {
        if (getActivity() != null && getActivity().getIntent() != null) {
            Bundle extras = getActivity().getIntent().getExtras();
            if (extras != null && extras.getBoolean("open_med_fragment", false)) {
                // 从通知跳转过来
                isFromNotification = true;
                currentMedicineName = extras.getString("medicine_name", "");
                currentDosage = extras.getString("dosage", "");
                currentRequestCode = extras.getInt("request_code", 0);

                // 显示服药确认界面
                showMedicationConfirmation();

                // 清除标志，避免重复触发
                getActivity().getIntent().removeExtra("open_med_fragment");
            }
        }
    }

    private void showMedicationConfirmation() {
        if (getActivity() == null || currentMedicineName.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("💊 用药提醒");
        builder.setMessage("是否已服用：" + currentMedicineName +
                (currentDosage != null && !currentDosage.isEmpty() ? "\n剂量：" + currentDosage : ""));

        builder.setPositiveButton("✅ 已服药", (dialog, which) -> {
            recordMedicationTaken();
            Toast.makeText(getContext(), "服药记录已保存", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("稍后提醒", (dialog, which) -> {
            // 设置5分钟后的提醒
            scheduleReminderIn5Minutes();
            Toast.makeText(getContext(), "5分钟后再次提醒", Toast.LENGTH_SHORT).show();
        });

        builder.setNeutralButton("取消", null);
        builder.show();
    }

    private void loadTodayMedicationLogs() {
        if (medicationLogContainer == null || apiService == null) return;

        // 清除现有视图
        medicationLogContainer.removeAllViews();

        // 显示标题
        TextView tvTitle = new TextView(getContext());
        tvTitle.setText("今日服药记录");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(16, 16, 16, 8);
        tvTitle.setTextColor(0xFF1F2937);
        medicationLogContainer.addView(tvTitle);

        apiService.getTodayMedicationLogs(currentUserId).enqueue(new Callback<List<MedicationLogResponse>>() {
            @Override
            public void onResponse(Call<List<MedicationLogResponse>> call, Response<List<MedicationLogResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<MedicationLogResponse> logs = response.body();

                    if (logs.isEmpty()) {
                        showNoLogsMessage();
                    } else {
                        // 按时间排序（最近的在前）
                        Collections.sort(logs, (log1, log2) -> {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                Date time1 = log1.takeTime != null ? sdf.parse(log1.takeTime) : new Date(0);
                                Date time2 = log2.takeTime != null ? sdf.parse(log2.takeTime) : new Date(0);
                                return time2.compareTo(time1); // 降序
                            } catch (Exception e) {
                                return 0;
                            }
                        });

                        for (MedicationLogResponse log : logs) {
                            View logCard = createMedicationLogCard(log);
                            medicationLogContainer.addView(logCard);
                        }
                    }
                } else {
                    showNoLogsMessage();
                }
            }

            @Override
            public void onFailure(Call<List<MedicationLogResponse>> call, Throwable t) {
                Log.e("HomeFragment", "获取用药记录失败: " + t.getMessage());
                showNoLogsMessage();
            }
        });
    }

    private View createMedicationLogCard(MedicationLogResponse log) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 12, 16, 12);
        card.setBackgroundResource(R.drawable.log_card_bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 4, 8, 4);
        card.setLayoutParams(params);

        // 药品名称和时间
        LinearLayout nameTimeLayout = new LinearLayout(getContext());
        nameTimeLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvMedicineName = new TextView(getContext());
        tvMedicineName.setText(log.medicineName != null ? log.medicineName : "未知药品");
        tvMedicineName.setTextSize(16);
        tvMedicineName.setTextColor(0xFF1F2937);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvMedicineName.setLayoutParams(nameParams);

        TextView tvTime = new TextView(getContext());
        tvTime.setText(log.takeTime != null ? log.takeTime : "未知时间");
        tvTime.setTextSize(14);
        tvTime.setTextColor(0xFF6B7280);

        nameTimeLayout.addView(tvMedicineName);
        nameTimeLayout.addView(tvTime);

        // 状态和日期
        LinearLayout statusDateLayout = new LinearLayout(getContext());
        statusDateLayout.setOrientation(LinearLayout.HORIZONTAL);
        statusDateLayout.setPadding(0, 8, 0, 0);

        TextView tvStatus = new TextView(getContext());
        if (log.status != null) {
            if (log.status == 1) {
                tvStatus.setText("✅ 按时服药");
                tvStatus.setTextColor(0xFF10B981);
            } else {
                tvStatus.setText("⚠️ 漏服");
                tvStatus.setTextColor(0xFFEF4444);
            }
        } else {
            tvStatus.setText("状态未知");
            tvStatus.setTextColor(0xFF6B7280);
        }
        tvStatus.setTextSize(14);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvStatus.setLayoutParams(statusParams);

        TextView tvDate = new TextView(getContext());
        tvDate.setText(log.logDate != null ? log.logDate : "");
        tvDate.setTextSize(12);
        tvDate.setTextColor(0xFF9CA3AF);

        statusDateLayout.addView(tvStatus);
        statusDateLayout.addView(tvDate);

        card.addView(nameTimeLayout);
        card.addView(statusDateLayout);

        return card;
    }

    private void showNoLogsMessage() {
        TextView tvEmpty = new TextView(getContext());
        tvEmpty.setText("今日暂无服药记录");
        tvEmpty.setTextSize(14);
        tvEmpty.setTextColor(0xFF6B7280);
        tvEmpty.setPadding(16, 32, 16, 32);
        tvEmpty.setGravity(Gravity.CENTER);
        medicationLogContainer.addView(tvEmpty);
    }

    private void recordMedicationTaken() {
        // 获取当前日期和时间
        SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String today = dateSdf.format(new Date());
        String currentTime = timeSdf.format(new Date());

        // 调用API记录服药到数据库
        apiService.recordMedicationTaken(
                currentUserId,
                currentMedicineName,
                today,
                currentTime,
                1  // 状态：按时服药
        ).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SmsResponse smsResponse = response.body();
                    if (smsResponse.code == 200) {
                        Toast.makeText(getContext(), "服药记录已保存到云端", Toast.LENGTH_SHORT).show();

                        // 刷新今日用药记录
                        loadTodayMedicationLogs();

                        // 更新日历显示
                        loadMedicationStatus();

                        // 清除提醒
                        if (currentRequestCode != 0) {
                            cancelCurrentReminder();
                        }
                    } else {
                        Toast.makeText(getContext(), "保存失败: " + smsResponse.msg, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "保存失败，请检查网络", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SmsResponse> call, Throwable t) {
                Toast.makeText(getContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scheduleReminderIn5Minutes() {
        // 修改为设置2分钟后的第二次提醒
        AlarmManager alarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 2); // 改为2分钟

        Intent intent = new Intent(getActivity(), MedicationReminderReceiver.class);
        intent.putExtra("medicine_name", currentMedicineName);
        intent.putExtra("dosage", currentDosage);
        intent.putExtra("time_label", "再次提醒");
        intent.putExtra("request_code", currentRequestCode);
        intent.putExtra("reminder_type", 2); // 第二次提醒

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getActivity(),
                currentRequestCode + 2000, // 使用不同的requestCode
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }

        Toast.makeText(getContext(), "2分钟后再次提醒", Toast.LENGTH_SHORT).show(); // 修改提示信息
    }

    private void cancelCurrentReminder() {
        AlarmManager alarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);

        // 取消第一次提醒
        Intent firstIntent = new Intent(getActivity(), MedicationReminderReceiver.class);
        PendingIntent firstPendingIntent = PendingIntent.getBroadcast(
                getActivity(),
                currentRequestCode,
                firstIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(firstPendingIntent);

        // 取消第二次提醒
        Intent secondIntent = new Intent(getActivity(), MedicationReminderReceiver.class);
        PendingIntent secondPendingIntent = PendingIntent.getBroadcast(
                getActivity(),
                currentRequestCode + 2000,
                secondIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(secondPendingIntent);
    }

    private void updateMedicationStatusDisplay() {
        // 更新日历显示
        loadMedicationStatus();

        // 更新今日用药列表
        loadTodayMedications();
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.71.34:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void loadTodayMedications() {
        // 清除现有视图
        medicationContainer.removeAllViews();

        // 显示标题
        TextView tvTitle = new TextView(getContext());
        tvTitle.setText("今日用药计划");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(16, 16, 16, 8);
        tvTitle.setTextColor(0xFF1F2937);
        medicationContainer.addView(tvTitle);

        // 模拟今日用药数据，实际应从API获取
        List<Medication> todayMeds = getTodayMedications();

        if (todayMeds.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("今日暂无用药计划");
            tvEmpty.setTextSize(14);
            tvEmpty.setTextColor(0xFF6B7280);
            tvEmpty.setPadding(16, 32, 16, 32);
            tvEmpty.setGravity(Gravity.CENTER);
            medicationContainer.addView(tvEmpty);
        } else {
            for (Medication med : todayMeds) {
                View medCard = createTodayMedCard(med);
                medicationContainer.addView(medCard);
            }

            // 添加服药记录按钮
            Button btnRecordAll = new Button(getContext());
            btnRecordAll.setText("✅ 记录全部已服");
            btnRecordAll.setBackgroundColor(0xFF10B981);
            btnRecordAll.setTextColor(0xFFFFFFFF);
            btnRecordAll.setPadding(16, 16, 16, 16);
            btnRecordAll.setOnClickListener(v -> {
                recordAllMedications(todayMeds);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(16, 16, 16, 32);
            btnRecordAll.setLayoutParams(params);
            medicationContainer.addView(btnRecordAll);
        }
    }

    private List<Medication> getTodayMedications() {
        // 这里应该从API获取今日用药
        // 暂时返回空列表
        return new ArrayList<>();
    }

    private View createTodayMedCard(Medication medication) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 16, 16, 16);
        card.setBackgroundResource(R.drawable.med_card_bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(16, 8, 16, 8);
        card.setLayoutParams(params);

        // 药品名称和状态
        LinearLayout nameLayout = new LinearLayout(getContext());
        nameLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvName = new TextView(getContext());
        tvName.setText(medication.medicineName);
        tvName.setTextSize(16);
        tvName.setTextColor(0xFF1F2937);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvName.setLayoutParams(nameParams);

        Button btnTaken = new Button(getContext());
        btnTaken.setText("已服");
        btnTaken.setBackgroundColor(0xFF3B82F6);
        btnTaken.setTextColor(0xFFFFFFFF);
        btnTaken.setPadding(16, 8, 16, 8);
        btnTaken.setOnClickListener(v -> {
            recordSingleMedication(medication);
            btnTaken.setText("✅ 已记录");
            btnTaken.setBackgroundColor(0xFF10B981);
            btnTaken.setEnabled(false);
        });

        nameLayout.addView(tvName);
        nameLayout.addView(btnTaken);

        // 剂量和时间
        TextView tvDetails = new TextView(getContext());
        tvDetails.setText("剂量: " + medication.dosage + " | 时间: " +
                (medication.takeTimeMorning != null ? medication.takeTimeMorning : "") +
                (medication.takeTimeEvening != null ? " " + medication.takeTimeEvening : ""));
        tvDetails.setTextSize(14);
        tvDetails.setTextColor(0xFF6B7280);
        tvDetails.setPadding(0, 8, 0, 0);

        card.addView(nameLayout);
        card.addView(tvDetails);

        return card;
    }

    private void recordSingleMedication(Medication medication) {
        SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String today = dateSdf.format(new Date());
        String currentTime = timeSdf.format(new Date());

        apiService.recordMedicationTaken(
                currentUserId,
                medication.medicineName,
                today,
                currentTime,
                1  // 状态：按时服药
        ).enqueue(new Callback<SmsResponse>() {
            @Override
            public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SmsResponse smsResponse = response.body();
                    if (smsResponse.code == 200) {
                        Toast.makeText(getContext(), medication.medicineName + " 服药记录已保存到云端", Toast.LENGTH_SHORT).show();
                        loadTodayMedicationLogs();
                    } else {
                        Toast.makeText(getContext(), "保存失败: " + smsResponse.msg, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<SmsResponse> call, Throwable t) {
                Toast.makeText(getContext(), "网络错误", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void recordAllMedications(List<Medication> medications) {
        if (medications.isEmpty()) return;

        SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String today = dateSdf.format(new Date());
        String currentTime = timeSdf.format(new Date());

        // 创建计数器对象
        class RequestCounter {
            int success = 0;
            int completed = 0;
            int total = medications.size();

            synchronized void incrementSuccess() {
                success++;
                completed++;
                checkCompletion();
            }

            synchronized void incrementCompleted() {
                completed++;
                checkCompletion();
            }

            private void checkCompletion() {
                if (completed == total) {
                    getActivity().runOnUiThread(() -> {
                        int failed = total - success;
                        String message;
                        if (failed == 0) {
                            message = "已成功保存 " + success + " 种药品的服药记录到云端";
                        } else {
                            message = "保存完成：" + success + " 种成功，" + failed + " 种失败";
                        }

                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                        loadTodayMedicationLogs();
                    });
                }
            }
        }

        final RequestCounter counter = new RequestCounter();

        for (Medication med : medications) {
            apiService.recordMedicationTaken(
                    currentUserId,
                    med.medicineName,
                    today,
                    currentTime,
                    1
            ).enqueue(new Callback<SmsResponse>() {
                @Override
                public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().code == 200) {
                            counter.incrementSuccess();
                        } else {
                            counter.incrementCompleted();
                        }
                    } else {
                        counter.incrementCompleted();
                    }
                }

                @Override
                public void onFailure(Call<SmsResponse> call, Throwable t) {
                    counter.incrementCompleted();
                }
            });
        }
    }

    private void loadMedicationStatus() {
        apiService.getMedLogs(currentUserId).enqueue(new Callback<List<MedicationLog>>() {
            @Override
            public void onResponse(Call<List<MedicationLog>> call, Response<List<MedicationLog>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<MedicationLog> logs = response.body();

                    // 在这里处理日历标记
                    if (logs.size() > 0) {
                        // 可以在这里更新日历的标记
                        updateCalendarMarks(logs);
                    }

                    // 简单示例：控制台输出
                    for (MedicationLog log : logs) {
                        System.out.println("日期: " + log.logDate + ", 状态: " + (log.status == 1 ? "按时" : "漏服"));
                    }
                } else {
                    Toast.makeText(getContext(), "获取数据失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<MedicationLog>> call, Throwable t) {
                Toast.makeText(getContext(), "连接服务器失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCalendarMarks(List<MedicationLog> logs) {
        // 这里可以设置日历的日期标记
        // 需要自定义CalendarView或使用其他库来实现日期标记
        // 暂时留空
    }

    /**
     * 新增方法：同步用药数据
     * 当从通知进入应用时，确保数据是最新的
     */
    private void syncMedicationData() {
        // 当用户从通知进入时，自动刷新数据
        if (isFromNotification && currentUserId != -1L) {
            // 延迟刷新，确保API调用完成
            new android.os.Handler().postDelayed(() -> {
                loadTodayMedicationLogs();
                loadMedicationStatus();
            }, 1000);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到页面都检查是否有通知跳转
        checkIntentFromNotification();
        // 新增：同步数据
        syncMedicationData();
    }
}
