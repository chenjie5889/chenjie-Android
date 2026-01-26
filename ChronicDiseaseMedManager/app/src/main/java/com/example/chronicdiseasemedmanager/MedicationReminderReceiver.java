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

import java.util.concurrent.TimeUnit;

public class MedicationReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "MedReminderReceiver";
    private static final String CHANNEL_ID = "medication_reminder";

    // 用于存储未处理的提醒
    private static final String PREF_UNHANDLED_REMINDERS = "unhandled_reminders";
    private static final String KEY_PREFIX = "reminder_";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "收到用药提醒广播");

        String medicineName = intent.getStringExtra("medicine_name");
        String dosage = intent.getStringExtra("dosage");
        String timeLabel = intent.getStringExtra("time_label");
        int requestCode = intent.getIntExtra("request_code", 0);

        Log.d(TAG, "药品: " + medicineName + ", 剂量: " + dosage + ", 时间: " + timeLabel + ", 请求码: " + requestCode);

        // 检查是否是"再次提醒"
        boolean isRepeatReminder = timeLabel != null && timeLabel.contains("再次");

        if (!isRepeatReminder) {
            // 首次提醒，记录未处理提醒
            saveUnhandledReminder(context, requestCode, medicineName, dosage);

            // 设置5分钟后自动重复提醒
            scheduleRepeatReminder(context, intent, requestCode, medicineName, dosage);
        }

        // 显示通知
        showNotification(context, medicineName, dosage, timeLabel, requestCode, isRepeatReminder);

        // 显示Toast提示
        String toastMsg = isRepeatReminder ?
                "再次提醒：" + medicineName + " 请及时服药！" :
                "用药提醒：" + medicineName + " 时间到了";
        Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show();
    }

    private void saveUnhandledReminder(Context context, int requestCode, String medicineName, String dosage) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_UNHANDLED_REMINDERS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String key = KEY_PREFIX + requestCode;
        String value = System.currentTimeMillis() + "|" + medicineName + "|" + (dosage != null ? dosage : "");

        editor.putString(key, value);
        editor.apply();

        Log.d(TAG, "保存未处理提醒: " + key + " = " + value);
    }

    private void removeUnhandledReminder(Context context, int requestCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_UNHANDLED_REMINDERS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String key = KEY_PREFIX + requestCode;
        editor.remove(key);
        editor.apply();

        Log.d(TAG, "移除未处理提醒: " + key);
    }

    private void scheduleRepeatReminder(Context context, Intent originalIntent, int requestCode,
                                        String medicineName, String dosage) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 5分钟后
        long triggerTime = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(5);

        // 创建重复提醒的Intent
        Intent repeatIntent = new Intent(context, MedicationReminderReceiver.class);
        repeatIntent.putExtra("medicine_name", medicineName);
        repeatIntent.putExtra("dosage", dosage);
        repeatIntent.putExtra("time_label", "再次提醒");
        repeatIntent.putExtra("request_code", requestCode + 5000); // 不同的requestCode

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 5000,
                repeatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 使用setExactAndAllowWhileIdle确保准时提醒
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        }

        Log.d(TAG, "已设置5分钟后重复提醒: " + medicineName);
    }

    private void showNotification(Context context, String medicineName, String dosage,
                                  String timeLabel, int requestCode, boolean isRepeat) {
        // 创建通知渠道（Android 8.0+需要）
        createNotificationChannel(context);

        // 创建点击通知的Intent - 跳转到首页并携带药品信息
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

        // 创建"已服药"按钮的Intent
        Intent takenIntent = new Intent(context, MedicationReminderReceiver.class);
        takenIntent.setAction("ACTION_MEDICATION_TAKEN");
        takenIntent.putExtra("medicine_name", medicineName);
        takenIntent.putExtra("request_code", requestCode);

        PendingIntent takenPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 10000,
                takenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(isRepeat ? "⏰ 再次提醒" : "💊 用药提醒")
                .setContentText(timeLabel + " - " + medicineName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("用药时间" + (isRepeat ? "已过5分钟" : "到了") + "！\n药品：" + medicineName +
                                (dosage != null && !dosage.isEmpty() ? "\n剂量：" + dosage : "") +
                                "\n请按时服药"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setColor(isRepeat ? Color.RED : Color.BLUE)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "稍后提醒",
                        createLaterReminderIntent(context, requestCode, medicineName, dosage))
                .addAction(android.R.drawable.ic_input_add, "✅ 已服药", takenPendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 显示通知
        notificationManager.notify(requestCode, builder.build());
    }

    private PendingIntent createLaterReminderIntent(Context context, int requestCode,
                                                    String medicineName, String dosage) {
        Intent laterIntent = new Intent(context, MedicationReminderReceiver.class);
        laterIntent.putExtra("medicine_name", medicineName);
        laterIntent.putExtra("dosage", dosage);
        laterIntent.putExtra("time_label", "稍后提醒");
        laterIntent.putExtra("request_code", requestCode + 20000);

        return PendingIntent.getBroadcast(
                context,
                requestCode + 20000,
                laterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
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

            // 设置通知声音
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // 处理"已服药"操作
    public static void handleMedicationTaken(Context context, String medicineName, int requestCode) {
        // 移除未处理提醒
        SharedPreferences prefs = context.getSharedPreferences(PREF_UNHANDLED_REMINDERS, Context.MODE_PRIVATE);
        String key = KEY_PREFIX + requestCode;
        if (prefs.contains(key)) {
            prefs.edit().remove(key).apply();
            Log.d(TAG, "已处理提醒: " + medicineName + ", requestCode: " + requestCode);
        }

        // 取消重复提醒
        cancelRepeatReminder(context, requestCode);

        // 可以在这里调用API记录服药到服务器
        Toast.makeText(context, medicineName + " 服药记录已保存", Toast.LENGTH_SHORT).show();
    }

    private static void cancelRepeatReminder(Context context, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 取消5分钟后的重复提醒
        Intent repeatIntent = new Intent(context, MedicationReminderReceiver.class);
        PendingIntent repeatPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 5000,
                repeatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(repeatPendingIntent);

        // 取消稍后提醒
        Intent laterIntent = new Intent(context, MedicationReminderReceiver.class);
        PendingIntent laterPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 20000,
                laterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(laterPendingIntent);
    }
}