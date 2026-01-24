package com.example.chronicdiseasemedmanager;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.EditText;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DatePickerHelper {
    private Calendar calendar;
    private SimpleDateFormat dateFormatter;

    public DatePickerHelper() {
        calendar = Calendar.getInstance();
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    }

    public void showDatePicker(Context context, EditText editText) {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                context,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    calendar.set(selectedYear, selectedMonth, selectedDay);
                    String selectedDate = dateFormatter.format(calendar.getTime());
                    editText.setText(selectedDate);
                },
                year, month, day
        );

        // 设置最大日期为今天
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());

        datePickerDialog.show();
    }
}