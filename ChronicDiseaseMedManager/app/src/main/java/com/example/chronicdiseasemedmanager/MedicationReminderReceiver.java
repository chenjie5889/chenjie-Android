package com.example.chronicdiseasemedmanager;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class MedicationReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "MedReminderReceiver";
    private static final String CHANNEL_ID = "medication_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "收到用药提醒广播");
        Log.d(TAG, "Intent Action: " + intent.getAction());
        Log.d(TAG, "Intent Extras: " + intent.getExtras());

        String medicineName = intent.getStringExtra("medicine_name");
        String dosage = intent.getStringExtra("dosage");
        String timeLabel = intent.getStringExtra("time_label");
        int requestCode = intent.getIntExtra("request_code", 0);

        Log.d(TAG, "药品: " + medicineName + ", 剂量: " + dosage + ", 时间: " + timeLabel + ", 请求码: " + requestCode);

        // 显示通知
        showNotification(context, medicineName, dosage, timeLabel, requestCode);

        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);

        if (hour != -1 && minute != -1) {
            scheduleNextAlarm(context, intent, hour, minute, requestCode);
        }
        // 显示Toast提示
        Toast.makeText(context, "用药提醒：" + medicineName, Toast.LENGTH_LONG).show();
    }

    private void scheduleNextAlarm(Context context, Intent originalIntent, int hour, int minute, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 计算明天的时间
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
        calendar.set(java.util.Calendar.MINUTE, minute);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);

        // 强制加1天（因为这是重复闹钟，这次响了，下次肯定是明天）
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);

        // 重新构建 PendingIntent (必须与之前的匹配)
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                originalIntent, // 使用原始Intent，包含所有extras
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 再次设置精确闹钟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
                Log.d(TAG, "已自动设置明天的重复提醒");
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }
    private void showNotification(Context context, String medicineName, String dosage, String timeLabel, int requestCode) {
        // 创建通知渠道（Android 8.0+需要）
        createNotificationChannel(context);

        // 创建点击通知的Intent
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.putExtra("open_med_fragment", true);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("💊 用药提醒")
                .setContentText(timeLabel + " - " + medicineName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("用药时间到了！\n药品：" + medicineName +
                                (dosage != null && !dosage.isEmpty() ? "\n剂量：" + dosage : "") +
                                "\n请按时服药"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setColor(Color.BLUE)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 显示通知
        notificationManager.notify(requestCode, builder.build());
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "用药提醒";
            String description = "提醒用户按时服药";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
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