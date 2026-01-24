package com.example.chronicdiseasemedmanager;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeFragment extends Fragment {

    private CalendarView calendarView;
    private ApiService apiService;
    private Long currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        calendarView = view.findViewById(R.id.mainCalendar);

        // 获取当前用户ID
        SharedPreferences sp = getActivity().getSharedPreferences("user_info", getActivity().MODE_PRIVATE);
        currentUserId = sp.getLong("userId", -1L);

        initRetrofit();

        if (currentUserId != -1L) {
            loadMedicationStatus();
        } else {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void initRetrofit() {
        apiService = new Retrofit.Builder()
                .baseUrl("http://192.168.71.29:8080/")
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
                        Toast.makeText(getContext(), "获取到 " + logs.size() + " 条用药记录", Toast.LENGTH_SHORT).show();
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
}