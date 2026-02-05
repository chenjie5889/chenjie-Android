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
    private LinearLayout medicationLogContainer;
    private TextView tvNoMedication;
    private TextView tvNoLogs;
    private String selectedDate; // 新增：记录选中的日期
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
        medicationLogContainer = view.findViewById(R.id.rvTodayMedLogs);
        tvNoMedication = new TextView(getContext());

        // 设置日历日期选择监听器
        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                // month是从0开始的，所以要+1
                selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                Log.d("HomeFragment", "选中日期: " + selectedDate);

                // 加载选中日期的用药记录
                loadMedicationLogsByDate(selectedDate);

                // 更新标题显示
                updateMedicationLogTitle(selectedDate);
            }
        });

        // 获取当前日期作为默认选中
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        selectedDate = sdf.format(new Date());
        // 检查是否从通知跳转过来
        checkIntentFromNotification();

        // 获取当前用户ID
        SharedPreferences sp = getActivity().getSharedPreferences("user_info", getActivity().MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);

        initRetrofit();

        if (currentUserId != -1L) {
            loadTodayMedicationLogs();
            loadMedicationStatus();
        } else {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    /**
     * 新增方法：加载指定日期的服药记录
     */
    private void loadMedicationLogsByDate(String date) {
        if (medicationLogContainer == null || apiService == null || currentUserId == null) {
            return;
        }

        // 清除现有视图
        medicationLogContainer.removeAllViews();

        // 显示加载中
        TextView tvLoading = new TextView(getContext());
        tvLoading.setText("正在加载 " + date + " 的服药记录...");
        tvLoading.setTextSize(14);
        tvLoading.setTextColor(0xFF6B7280);
        tvLoading.setGravity(Gravity.CENTER);
        tvLoading.setPadding(16, 32, 16, 32);
        medicationLogContainer.addView(tvLoading);

        apiService.getMedLogsByDate(currentUserId, date).enqueue(new Callback<List<MedicationLogResponse>>() {
            @Override
            public void onResponse(Call<List<MedicationLogResponse>> call, Response<List<MedicationLogResponse>> response) {
                medicationLogContainer.removeAllViews();

                if (response.isSuccessful() && response.body() != null) {
                    List<MedicationLogResponse> logs = response.body();

                    if (logs.isEmpty()) {
                        showNoLogsMessage(date);
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

                        // 显示标题
                        TextView tvTitle = new TextView(getContext());
                        tvTitle.setText(date + " 服药记录");
                        tvTitle.setTextSize(18);
                        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                        tvTitle.setPadding(16, 16, 16, 8);
                        tvTitle.setTextColor(0xFF1F2937);
                        medicationLogContainer.addView(tvTitle);

                        for (MedicationLogResponse log : logs) {
                            View logCard = createMedicationLogCard(log);
                            medicationLogContainer.addView(logCard);
                        }
                    }
                } else {
                    showNoLogsMessage(date);
                }
            }

            @Override
            public void onFailure(Call<List<MedicationLogResponse>> call, Throwable t) {
                medicationLogContainer.removeAllViews();
                showNoLogsMessage(date);
                Log.e("HomeFragment", "获取指定日期用药记录失败: " + t.getMessage());
            }
        });
    }

    /**
     * 新增方法：更新服药记录标题
     */
    private void updateMedicationLogTitle(String date) {
        // 这个方法主要是为了更新UI标题，但我们在loadMedicationLogsByDate中已经处理了
        // 可以留作后续扩展使用
    }

    /**
     * 修改方法：显示无记录消息（添加日期参数）
     */
    private void showNoLogsMessage(String date) {
        TextView tvEmpty = new TextView(getContext());
        tvEmpty.setText(date + " 暂无服药记录");
        tvEmpty.setTextSize(14);
        tvEmpty.setTextColor(0xFF6B7280);
        tvEmpty.setPadding(16, 32, 16, 32);
        tvEmpty.setGravity(Gravity.CENTER);
        medicationLogContainer.addView(tvEmpty);
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
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date());
        loadMedicationLogsByDate(today);
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


    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.71.34:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    private void loadMedicationStatus() {
        apiService.getMedLogs(currentUserId).enqueue(new Callback<List<MedicationLog>>() {
            @Override
            public void onResponse(Call<List<MedicationLog>> call, Response<List<MedicationLog>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<MedicationLog> logs = response.body();

                    // 在这里处理日历标记
                    if (logs.size() > 0) {

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

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到页面都检查是否有通知跳转
        checkIntentFromNotification();
    }
}
