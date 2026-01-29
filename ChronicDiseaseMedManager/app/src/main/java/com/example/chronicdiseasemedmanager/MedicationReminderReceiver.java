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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MedicationReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "MedReminderReceiver";
    private static final String CHANNEL_ID = "medication_reminder";
    private static final String ACTION_MEDICATION_TAKEN = "ACTION_MEDICATION_TAKEN";
    private static final String ACTION_LATER_REMINDER = "ACTION_LATER_REMINDER";

    // 定义提醒类型
    private static final String EXTRA_REMINDER_TYPE = "reminder_type";
    private static final int REMINDER_TYPE_FIRST = 1;    // 第一次提醒
    private static final int REMINDER_TYPE_SECOND = 2;   // 第二次提醒
    private static final int REMINDER_TYPE_LATER = 3;    // 稍后提醒

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

        Log.d(TAG, "药品: " + medicineName + ", 剂量: " + dosage + ", 时间: " + timeLabel +
                ", 请求码: " + requestCode + ", 提醒类型: " + getReminderTypeText(reminderType));

        if (reminderType == REMINDER_TYPE_FIRST) {
            // 第一次提醒
            // 1. 自动设置明天的相同提醒
            scheduleTomorrowReminder(context, hour, minute, medicineName, dosage, timeLabel, requestCode, medicationId);

            // 2. 设置2分钟后的第二次提醒
            scheduleSecondReminder(context, medicineName, dosage, timeLabel, requestCode);

            // 3. 显示通知
            showNotification(context, medicineName, dosage, timeLabel, requestCode, false);

            // 4. 显示Toast
            Toast.makeText(context, "用药提醒：" + medicineName + " 时间到了", Toast.LENGTH_LONG).show();

        } else if (reminderType == REMINDER_TYPE_SECOND) {
            // 第二次提醒
            showNotification(context, medicineName, dosage, "再次提醒", requestCode, true);
            Toast.makeText(context, "再次提醒：" + medicineName + " 请及时服药！", Toast.LENGTH_LONG).show();

        } else if (reminderType == REMINDER_TYPE_LATER) {
            // 稍后提醒（立即显示）
            showNotification(context, medicineName, dosage, "稍后提醒", requestCode, false);
            Toast.makeText(context, "稍后提醒：" + medicineName + " 请及时服药！", Toast.LENGTH_LONG).show();
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

    private void scheduleSecondReminder(Context context, String medicineName, String dosage,
                                        String timeLabel, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 2分钟后
        long triggerTime = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(2);

        Intent secondIntent = new Intent(context, MedicationReminderReceiver.class);
        secondIntent.putExtra("medicine_name", medicineName);
        secondIntent.putExtra("dosage", dosage);
        secondIntent.putExtra("time_label", timeLabel);
        secondIntent.putExtra("request_code", requestCode);
        secondIntent.putExtra(EXTRA_REMINDER_TYPE, REMINDER_TYPE_SECOND);

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
     * 处理"已服药"操作
     */
    private void handleMedicationTaken(Context context, Intent intent) {
        String medicineName = intent.getStringExtra("medicine_name");
        int requestCode = intent.getIntExtra("request_code", 0);

        Log.d(TAG, "用户点击已服药：" + medicineName);

        // 1. 取消所有相关提醒
        cancelAllRelatedReminders(context, requestCode);

        // 2. 显示确认
        Toast.makeText(context, medicineName + " 服药记录已保存", Toast.LENGTH_SHORT).show();

        // 3. 通知需要更新
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(requestCode);
    }

    /**
     * 处理"稍后提醒"操作
     */
    private void handleLaterReminder(Context context, Intent intent) {
        String medicineName = intent.getStringExtra("medicine_name");
        String dosage = intent.getStringExtra("dosage");
        int requestCode = intent.getIntExtra("request_code", 0);

        Log.d(TAG, "用户点击稍后提醒：" + medicineName);

        // 设置2分钟后的提醒
        scheduleLaterReminder(context, medicineName, dosage, requestCode);

        // 显示Toast
        Toast.makeText(context, "2分钟后再次提醒您", Toast.LENGTH_SHORT).show();

        // 关闭当前通知
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(requestCode);
    }

    private void scheduleLaterReminder(Context context, String medicineName,
                                       String dosage, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 2分钟后
        long triggerTime = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(2);

        Intent laterIntent = new Intent(context, MedicationReminderReceiver.class);
        laterIntent.putExtra("medicine_name", medicineName);
        laterIntent.putExtra("dosage", dosage);
        laterIntent.putExtra("time_label", "稍后提醒");
        laterIntent.putExtra("request_code", requestCode);
        laterIntent.putExtra(EXTRA_REMINDER_TYPE, REMINDER_TYPE_LATER);

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

        Log.d(TAG, "已设置稍后提醒（2分钟后）: " + medicineName);
    }

    /**
     * 取消所有相关提醒
     */
    private void cancelAllRelatedReminders(Context context, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 取消第一次提醒
        try {
            Intent firstIntent = new Intent(context, MedicationReminderReceiver.class);
            PendingIntent firstPendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    firstIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(firstPendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "取消第一次提醒失败", e);
        }

        // 取消第二次提醒
        try {
            Intent secondIntent = new Intent(context, MedicationReminderReceiver.class);
            PendingIntent secondPendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + 2000,
                    secondIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(secondPendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "取消第二次提醒失败", e);
        }

        // 取消稍后提醒
        try {
            Intent laterIntent = new Intent(context, MedicationReminderReceiver.class);
            PendingIntent laterPendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + 3000,
                    laterIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(laterPendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "取消稍后提醒失败", e);
        }

        Log.d(TAG, "已取消所有相关提醒，requestCode: " + requestCode);
    }

    private void showNotification(Context context, String medicineName, String dosage,
                                  String timeLabel, int requestCode, boolean isSecondReminder) {
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
        takenIntent.putExtra("request_code", requestCode);

        PendingIntent takenPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 10000,
                takenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "稍后提醒"按钮Intent
        Intent laterIntent = new Intent(context, MedicationReminderReceiver.class);
        laterIntent.setAction(ACTION_LATER_REMINDER);
        laterIntent.putExtra("medicine_name", medicineName);
        laterIntent.putExtra("dosage", dosage);
        laterIntent.putExtra("request_code", requestCode);

        PendingIntent laterPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 20000,
                laterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

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
        if (!isSecondReminder && !"再次提醒".equals(timeLabel)) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "2分钟后提醒", laterPendingIntent);
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(requestCode, builder.build());
    }

    // 辅助方法
    private String getReminderTypeText(int type) {
        switch (type) {
            case REMINDER_TYPE_FIRST: return "第一次提醒";
            case REMINDER_TYPE_SECOND: return "第二次提醒";
            case REMINDER_TYPE_LATER: return "稍后提醒";
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
            sb.append("第二次提醒！\n");
        } else {
            sb.append("用药时间到了！\n");
        }

        sb.append("药品：").append(medicineName);

        if (dosage != null && !dosage.isEmpty()) {
            sb.append("\n剂量：").append(dosage);
        }

        sb.append("\n请按时服药");

        return sb.toString();
    }

    private int getNotificationColor(String timeLabel, boolean isSecondReminder) {
        if ("再次提醒".equals(timeLabel) || isSecondReminder) {
            return Color.RED;
        } else if ("稍后提醒".equals(timeLabel)) {
            return Color.YELLOW;
        } else {
            return Color.BLUE;
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