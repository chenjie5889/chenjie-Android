package com.example.chronicdiseasemedmanager;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public class MedicationReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "MedReminderReceiver";
    private static final String CHANNEL_ID = "medication_reminder";
    private static final String ACTION_MEDICATION_TAKEN = "ACTION_MEDICATION_TAKEN";
    private static final String ACTION_LATER_REMINDER = "ACTION_LATER_REMINDER";
    private static final String ACTION_MISSED_MEDICATION = "ACTION_MISSED_MEDICATION"; // 新增：漏服操作

    // 定义提醒类型
    private static final String EXTRA_REMINDER_TYPE = "reminder_type";
    private static final int REMINDER_TYPE_FIRST = 1;    // 第一次提醒
    private static final int REMINDER_TYPE_SECOND = 2;   // 第二次提醒（2分钟后）
    private static final int REMINDER_TYPE_MISSED = 3;   // 漏服记录（5分钟后） // 新增

    // 在广播接收器中定义独立的响应类
    public static class SmsResponse {
        @SerializedName("code")
        public int code;

        @SerializedName("msg")
        public String msg;

        @SerializedName("data")
        public String data;

        public SmsResponse() {}
    }

    // 在广播接收器中定义独立的API接口
    public interface LocalApiService {
        @FormUrlEncoded
        @POST("api/recordMedicationTaken")
        Call<SmsResponse> recordMedicationTaken(
                @Field("userId") Long userId,
                @Field("medicineName") String medicineName,
                @Field("date") String date,
                @Field("time") String time,
                @Field("status") Integer status
        );
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "收到用药提醒广播，Action: " + intent.getAction());

        // 处理不同的Action
        String action = intent.getAction();
        if (ACTION_MEDICATION_TAKEN.equals(action)) {
            handleMedicationTaken(context, intent);
            return;
        } else if (ACTION_LATER_REMINDER.equals(action)) {
            handleLaterReminder(context, intent);
            return;
        } else if (ACTION_MISSED_MEDICATION.equals(action)) { // 新增：处理漏服
            handleMissedMedication(context, intent);
            return;
        }

        // 正常提醒处理
        String medicineName = intent.getStringExtra("medicine_name");
        String dosage = intent.getStringExtra("dosage");
        String timeLabel = intent.getStringExtra("time_label");
        int requestCode = intent.getIntExtra("request_code", 0);
        int reminderType = intent.getIntExtra(EXTRA_REMINDER_TYPE, REMINDER_TYPE_FIRST);

        // 获取时间信息用于自动重设
        int hour = intent.getIntExtra("hour", 8);
        int minute = intent.getIntExtra("minute", 0);
        long medicationId = intent.getLongExtra("medication_id", 0);

        // 新增：获取计划服药时间（用于计算5分钟超时）
        String plannedTime = intent.getStringExtra("planned_time");

        Log.d(TAG, "药品: " + medicineName + ", 剂量: " + dosage + ", 时间: " + timeLabel +
                ", 请求码: " + requestCode + ", 提醒类型: " + getReminderTypeText(reminderType));

        if (reminderType == REMINDER_TYPE_FIRST) {
            // 第一次提醒
            // 1. 自动设置明天的相同提醒
            scheduleTomorrowReminder(context, hour, minute, medicineName, dosage, timeLabel, requestCode, medicationId);

            // 2. 设置2分钟后的第二次提醒
            scheduleSecondReminder(context, medicineName, dosage, timeLabel, requestCode, hour, minute, medicationId);

            // 3. 新增：设置5分钟后的漏服检查
            scheduleMissedMedicationCheck(context, medicineName, dosage, timeLabel, requestCode, hour, minute, medicationId);

            // 4. 显示通知
            showNotification(context, medicineName, dosage, timeLabel, requestCode, false, REMINDER_TYPE_FIRST);

            // 5. 显示Toast
            Toast.makeText(context, "用药提醒：" + medicineName + " 时间到了", Toast.LENGTH_LONG).show();

        } else if (reminderType == REMINDER_TYPE_SECOND) {
            // 第二次提醒（2分钟后）
            showNotification(context, medicineName, dosage, "再次提醒", requestCode, true, REMINDER_TYPE_SECOND);
            Toast.makeText(context, "再次提醒：" + medicineName + " 请及时服药！", Toast.LENGTH_LONG).show();

        } else if (reminderType == REMINDER_TYPE_MISSED) {
            // 漏服处理（5分钟后） - 直接记录为漏服
            handleMissedMedication(context, intent);
        }
    }

    /**
     * 处理"已服药"操作
     */
    private void handleMedicationTaken(Context context, Intent intent) {
        String medicineName = intent.getStringExtra("medicine_name");
        int requestCode = intent.getIntExtra("request_code", 0);
        String dosage = intent.getStringExtra("dosage");
        String plannedTime = intent.getStringExtra("planned_time"); // 新增：获取计划时间

        Log.d(TAG, "用户点击已服药：" + medicineName + "，剂量：" + dosage + "，计划时间：" + plannedTime);

        // 1. 调用API保存服药记录（状态为按时服药）
        callApiRecordMedication(context, medicineName, dosage, requestCode, plannedTime, 1); // status=1 按时

        // 2. 取消所有相关提醒（包括漏服检查）
        cancelAllRelatedReminders(context, requestCode);

        // 3. 通知需要更新
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(requestCode);
    }

    /**
     * 新增方法：处理漏服药品
     */
    private void handleMissedMedication(Context context, Intent intent) {
        String medicineName = intent.getStringExtra("medicine_name");
        int requestCode = intent.getIntExtra("request_code", 0);
        String dosage = intent.getStringExtra("dosage");
        String plannedTime = intent.getStringExtra("planned_time");
        int hour = intent.getIntExtra("hour", 8);
        int minute = intent.getIntExtra("minute", 0);

        Log.d(TAG, "5分钟已过，自动记录为漏服：" + medicineName + "，计划时间：" + hour + ":" + minute);

        // 1. 调用API保存服药记录（状态为漏服）
        callApiRecordMedication(context, medicineName, dosage, requestCode, plannedTime, 0); // status=0 漏服

        // 2. 取消相关提醒（但保留明天的提醒）
        cancelMissedCheckReminder(context, requestCode);

        // 3. 显示通知（可选）
        showMissedNotification(context, medicineName, requestCode);

        // 4. 显示Toast
        Toast.makeText(context, medicineName + " 已记录为漏服", Toast.LENGTH_SHORT).show();
    }

    /**
     * 修改：调用API保存服药记录，增加status参数
     */
    private void callApiRecordMedication(Context context, String medicineName, String dosage,
                                         int requestCode, String plannedTime, int status) {
        try {
            // 1. 获取当前用户ID
            SharedPreferences sp = context.getSharedPreferences("user_info", Context.MODE_PRIVATE);
            Long userId = sp.getLong("userId", -1L);

            if (userId == -1L) {
                Log.e(TAG, "用户未登录，无法保存记录");
                Toast.makeText(context, "请先登录应用", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. 获取当前日期和时间
            SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String today = dateSdf.format(new java.util.Date());

            // 如果是漏服，使用计划服药时间；如果是按时，使用当前时间
            String recordTime;
            if (status == 1) { // 按时服药，用当前时间
                recordTime = timeSdf.format(new java.util.Date());
            } else { // 漏服，用计划时间
                recordTime = plannedTime != null ? plannedTime : timeSdf.format(new java.util.Date());
            }

            // 3. 初始化Retrofit
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://192.168.71.34:8080/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            // 4. 创建API服务
            LocalApiService apiService = retrofit.create(LocalApiService.class);

            // 5. 调用API
            Call<SmsResponse> call = apiService.recordMedicationTaken(
                    userId,
                    medicineName,
                    today,
                    recordTime,
                    status  // 状态：1按时，0漏服
            );

            call.enqueue(new Callback<SmsResponse>() {
                @Override
                public void onResponse(Call<SmsResponse> call, Response<SmsResponse> response) {
                    handleApiResponse(context, response, medicineName, status);
                }

                @Override
                public void onFailure(Call<SmsResponse> call, Throwable t) {
                    Log.e(TAG, "保存服药记录失败: " + t.getMessage());
                    Toast.makeText(context, "网络错误，请稍后重试", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "调用API保存记录异常: " + e.getMessage());
            Toast.makeText(context, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 修改：处理API响应，区分按时和漏服
     */
    private void handleApiResponse(Context context, Response<SmsResponse> response,
                                   String medicineName, int status) {
        if (response.isSuccessful() && response.body() != null) {
            SmsResponse smsResponse = response.body();
            if (smsResponse.code == 200) {
                String statusText = (status == 1) ? "按时服药" : "漏服";
                Log.d(TAG, "服药记录保存到云端成功: " + medicineName + " (" + statusText + ")");
                Toast.makeText(context, medicineName + " " + statusText + "记录已保存", Toast.LENGTH_SHORT).show();

                // 可选：发送广播通知其他组件更新UI
                Intent updateIntent = new Intent("MEDICATION_RECORD_UPDATED");
                context.sendBroadcast(updateIntent);

            } else {
                Log.e(TAG, "保存失败: " + smsResponse.msg);
                Toast.makeText(context, "保存失败: " + smsResponse.msg, Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "API响应失败: " + response.code());
            Toast.makeText(context, "保存失败，请检查网络", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 自动设置明天的相同提醒（关键：实现日重复）
     */
    private void scheduleTomorrowReminder(Context context, int hour, int minute,
                                          String medicineName, String dosage,
                                          String timeLabel, int requestCode, long medicationId) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            // 设置明天同一时间
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 1); // 明天
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            Log.d(TAG, "自动设置明天提醒：" + medicineName + " " + hour + ":" + minute +
                    "，时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(calendar.getTime()));

            // 创建Intent
            Intent tomorrowIntent = new Intent(context, MedicationReminderReceiver.class);
            tomorrowIntent.putExtra("medicine_name", medicineName);
            tomorrowIntent.putExtra("dosage", dosage);
            tomorrowIntent.putExtra("time_label", timeLabel);
            tomorrowIntent.putExtra("request_code", requestCode);
            tomorrowIntent.putExtra(EXTRA_REMINDER_TYPE, REMINDER_TYPE_FIRST);
            tomorrowIntent.putExtra("hour", hour);
            tomorrowIntent.putExtra("minute", minute);
            tomorrowIntent.putExtra("medication_id", medicationId);
            tomorrowIntent.putExtra("planned_time", String.format("%02d:%02d", hour, minute)); // 新增：计划时间

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    tomorrowIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 使用精确闹钟
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
                } else {
                    // 降级处理
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }

            Log.d(TAG, "明天提醒设置成功：" + medicineName);

        } catch (Exception e) {
            Log.e(TAG, "设置明天提醒失败：" + e.getMessage(), e);
        }
    }

    /**
     * 修改：设置2分钟后的第二次提醒
     */
    private void scheduleSecondReminder(Context context, String medicineName, String dosage,
                                        String timeLabel, int requestCode,
                                        int hour, int minute, long medicationId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 2分钟后
        long triggerTime = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(2);

        Intent secondIntent = new Intent(context, MedicationReminderReceiver.class);
        secondIntent.putExtra("medicine_name", medicineName);
        secondIntent.putExtra("dosage", dosage);
        secondIntent.putExtra("time_label", timeLabel);
        secondIntent.putExtra("request_code", requestCode);
        secondIntent.putExtra(EXTRA_REMINDER_TYPE, REMINDER_TYPE_SECOND);
        secondIntent.putExtra("hour", hour);
        secondIntent.putExtra("minute", minute);
        secondIntent.putExtra("medication_id", medicationId);
        secondIntent.putExtra("planned_time", String.format("%02d:%02d", hour, minute)); // 新增：计划时间

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 2000,
                secondIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
        );

        Log.d(TAG, "已设置2分钟后第二次提醒: " + medicineName);
    }

    /**
     * 新增方法：设置5分钟后的漏服检查
     */
    private void scheduleMissedMedicationCheck(Context context, String medicineName, String dosage,
                                               String timeLabel, int requestCode,
                                               int hour, int minute, long medicationId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 5分钟后检查是否漏服
        long triggerTime = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(5);

        Intent missedIntent = new Intent(context, MedicationReminderReceiver.class);
        missedIntent.setAction(ACTION_MISSED_MEDICATION); // 使用Action来区分
        missedIntent.putExtra("medicine_name", medicineName);
        missedIntent.putExtra("dosage", dosage);
        missedIntent.putExtra("time_label", timeLabel);
        missedIntent.putExtra("request_code", requestCode);
        missedIntent.putExtra("hour", hour);
        missedIntent.putExtra("minute", minute);
        missedIntent.putExtra("medication_id", medicationId);
        missedIntent.putExtra("planned_time", String.format("%02d:%02d", hour, minute));

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 4000, // 使用不同的requestCode（4000系列）
                missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
        );

        Log.d(TAG, "已设置5分钟后漏服检查: " + medicineName + " (计划时间: " + hour + ":" + minute + ")");
    }

    /**
     * 处理"稍后提醒"操作
     */
    private void handleLaterReminder(Context context, Intent intent) {
        String medicineName = intent.getStringExtra("medicine_name");
        String dosage = intent.getStringExtra("dosage");
        int requestCode = intent.getIntExtra("request_code", 0);
        String plannedTime = intent.getStringExtra("planned_time");
        int hour = intent.getIntExtra("hour", 8);
        int minute = intent.getIntExtra("minute", 0);
        long medicationId = intent.getLongExtra("medication_id", 0);

        Log.d(TAG, "用户点击稍后提醒：" + medicineName);

        // 取消之前的漏服检查
        cancelMissedCheckReminder(context, requestCode);

        // 设置2分钟后的提醒（重新开始5分钟计时）
        scheduleLaterReminder(context, medicineName, dosage, requestCode, hour, minute, medicationId, plannedTime);

        // 显示Toast
        Toast.makeText(context, "2分钟后再次提醒您", Toast.LENGTH_SHORT).show();

        // 关闭当前通知
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(requestCode);
    }

    /**
     * 修改：设置2分钟后的稍后提醒（重新开始5分钟计时）
     */
    private void scheduleLaterReminder(Context context, String medicineName,
                                       String dosage, int requestCode,
                                       int hour, int minute, long medicationId, String plannedTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 2分钟后
        long triggerTime = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(2);

        Intent laterIntent = new Intent(context, MedicationReminderReceiver.class);
        laterIntent.putExtra("medicine_name", medicineName);
        laterIntent.putExtra("dosage", dosage);
        laterIntent.putExtra("time_label", "稍后提醒");
        laterIntent.putExtra("request_code", requestCode);
        laterIntent.putExtra(EXTRA_REMINDER_TYPE, REMINDER_TYPE_FIRST); // 重新开始
        laterIntent.putExtra("hour", hour);
        laterIntent.putExtra("minute", minute);
        laterIntent.putExtra("medication_id", medicationId);
        laterIntent.putExtra("planned_time", plannedTime);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 3000, // 使用不同的requestCode
                laterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
        );

        // 重新设置5分钟后的漏服检查
        scheduleMissedMedicationCheck(context, medicineName, dosage, "稍后提醒",
                requestCode, hour, minute, medicationId);

        Log.d(TAG, "已设置稍后提醒（2分钟后重新开始计时）: " + medicineName);
    }

    /**
     * 修改：取消所有相关提醒
     */
    private void cancelAllRelatedReminders(Context context, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 取消第一次提醒
        cancelPendingIntent(context, alarmManager, requestCode);

        // 取消第二次提醒
        cancelPendingIntent(context, alarmManager, requestCode + 2000);

        // 取消稍后提醒
        cancelPendingIntent(context, alarmManager, requestCode + 3000);

        // 取消漏服检查（新增）
        cancelPendingIntent(context, alarmManager, requestCode + 4000);

        Log.d(TAG, "已取消所有相关提醒，requestCode: " + requestCode);
    }

    /**
     * 新增：辅助方法取消单个PendingIntent
     */
    private void cancelPendingIntent(Context context, AlarmManager alarmManager, int requestCode) {
        try {
            Intent intent = new Intent(context, MedicationReminderReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "取消提醒失败 requestCode=" + requestCode, e);
        }
    }

    /**
     * 新增：只取消漏服检查
     */
    private void cancelMissedCheckReminder(Context context, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        cancelPendingIntent(context, alarmManager, requestCode + 4000);
    }

    /**
     * 修改：显示通知，增加reminderType参数
     */
    private void showNotification(Context context, String medicineName, String dosage,
                                  String timeLabel, int requestCode,
                                  boolean isSecondReminder, int reminderType) {
        createNotificationChannel(context);

        // 主Intent - 点击通知跳转
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.putExtra("open_med_fragment", true);
        appIntent.putExtra("medicine_name", medicineName);
        appIntent.putExtra("dosage", dosage);
        appIntent.putExtra("time_label", timeLabel);
        appIntent.putExtra("request_code", requestCode);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "已服药"按钮Intent
        Intent takenIntent = new Intent(context, MedicationReminderReceiver.class);
        takenIntent.setAction(ACTION_MEDICATION_TAKEN);
        takenIntent.putExtra("medicine_name", medicineName);
        takenIntent.putExtra("dosage", dosage);
        takenIntent.putExtra("request_code", requestCode);
        takenIntent.putExtra("planned_time", getPlannedTimeFromRequestCode(context, requestCode)); // 新增：传递计划时间

        PendingIntent takenPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 10000,
                takenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "稍后提醒"按钮Intent（只在第一次提醒时显示）
        PendingIntent laterPendingIntent = null;
        if (reminderType == REMINDER_TYPE_FIRST) {
            Intent laterIntent = new Intent(context, MedicationReminderReceiver.class);
            laterIntent.setAction(ACTION_LATER_REMINDER);
            laterIntent.putExtra("medicine_name", medicineName);
            laterIntent.putExtra("dosage", dosage);
            laterIntent.putExtra("request_code", requestCode);
            laterIntent.putExtra("planned_time", getPlannedTimeFromRequestCode(context, requestCode));
            laterIntent.putExtra("hour", getHourFromRequestCode(context, requestCode));
            laterIntent.putExtra("minute", getMinuteFromRequestCode(context, requestCode));
            laterIntent.putExtra("medication_id", getMedicationIdFromRequestCode(context, requestCode));

            laterPendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + 20000,
                    laterIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        // 构建通知
        String title = getNotificationTitle(timeLabel, isSecondReminder);
        String contentText = timeLabel + " - " + medicineName;
        String bigText = getBigText(medicineName, dosage, timeLabel, isSecondReminder);
        int color = getNotificationColor(timeLabel, isSecondReminder);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setColor(color)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_input_add, "✅ 已服药", takenPendingIntent);

        // 如果不是第二次提醒，添加"稍后提醒"按钮
        if (laterPendingIntent != null && !isSecondReminder && !"再次提醒".equals(timeLabel)) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "2分钟后提醒", laterPendingIntent);
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(requestCode, builder.build());
    }

    /**
     * 新增：显示漏服通知
     */
    private void showMissedNotification(Context context, String medicineName, int requestCode) {
        createNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⏰ 用药提醒")
                .setContentText(medicineName + " 已记录为漏服")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("5分钟内未确认服药，" + medicineName + " 已自动记录为漏服。\n请及时补服并注意健康。"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setColor(Color.RED);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(requestCode + 5000, builder.build()); // 使用不同的ID
    }

    /**
     * 新增：从存储中获取计划时间（简化实现）
     */
    private String getPlannedTimeFromRequestCode(Context context, int requestCode) {
        // 这里应该从数据库或SharedPreferences中获取
        // 简化实现：返回当前时间
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    private int getHourFromRequestCode(Context context, int requestCode) {
        // 简化实现
        return 8;
    }

    private int getMinuteFromRequestCode(Context context, int requestCode) {
        // 简化实现
        return 0;
    }

    private long getMedicationIdFromRequestCode(Context context, int requestCode) {
        // 简化实现
        return 1L;
    }

    // 辅助方法
    private String getReminderTypeText(int type) {
        switch (type) {
            case REMINDER_TYPE_FIRST: return "第一次提醒";
            case REMINDER_TYPE_SECOND: return "第二次提醒";
            case REMINDER_TYPE_MISSED: return "漏服记录";
            default: return "未知";
        }
    }

    private String getNotificationTitle(String timeLabel, boolean isSecondReminder) {
        if ("再次提醒".equals(timeLabel)) {
            return "⏰ 再次提醒";
        } else if ("稍后提醒".equals(timeLabel)) {
            return "⏰ 稍后提醒";
        } else if (isSecondReminder) {
            return "⏰ 第二次提醒";
        } else {
            return "💊 用药提醒";
        }
    }

    private String getBigText(String medicineName, String dosage,
                              String timeLabel, boolean isSecondReminder) {
        StringBuilder sb = new StringBuilder();

        if ("再次提醒".equals(timeLabel)) {
            sb.append("用药时间已过2分钟！\n");
        } else if ("稍后提醒".equals(timeLabel)) {
            sb.append("您设置的稍后提醒时间到了！\n");
        } else if (isSecondReminder) {
            sb.append("第二次提醒！距离用药时间已过2分钟\n");
        } else {
            sb.append("用药时间到了！\n");
        }

        sb.append("药品：").append(medicineName);

        if (dosage != null && !dosage.isEmpty()) {
            sb.append("\n剂量：").append(dosage);
        }

        sb.append("\n请在5分钟内确认服药，否则将记录为漏服");

        return sb.toString();
    }

    private int getNotificationColor(String timeLabel, boolean isSecondReminder) {
        if ("再次提醒".equals(timeLabel) || isSecondReminder) {
            return Color.YELLOW;
        } else if ("稍后提醒".equals(timeLabel)) {
            return Color.BLUE;
        } else {
            return Color.GREEN;
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "用药提醒", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("提醒用户按时服药");
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{1000, 1000});

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }
}