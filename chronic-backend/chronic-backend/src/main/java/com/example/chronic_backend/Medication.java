package com.example.chronic_backend;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "medications")
@Data
public class Medication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "disease_id")
    private Long diseaseId;  // 关联的疾病ID
    
    @Column(nullable = false)
    private String medicineName;  // 药品名称
    
    private String genericName;   // 通用名称
    
    private String dosage;        // 剂量，如：10mg
    
    private String frequency;     // 服用频率，如：每日一次
    
    @Column(name = "take_time_morning")
    private String takeTimeMorning;  // 改为String类型，存储格式：HH:mm
    
    @Column(name = "take_time_noon")
    private String takeTimeNoon;     // 改为String类型
    
    @Column(name = "take_time_evening")
    private String takeTimeEvening;  // 改为String类型
    
    @Column(name = "take_time_night")
    private String takeTimeNight;    // 改为String类型
    
    private String instructions;  // 服用说明，如：饭前、饭后
    
    @Column(columnDefinition = "TEXT")
    private String precautions;   // 注意事项
    
    @Column(columnDefinition = "TEXT")
    private String sideEffects;   // 副作用
    
    @Column(columnDefinition = "TEXT")
    private String contraindications;  // 禁忌症
    
    @Column(columnDefinition = "TEXT")
    private String mechanism;     // 作用机制
    
    @Column(columnDefinition = "TEXT")
    private String storage;       // 储存方式
    
    private String startDate;  // 改为String类型，存储格式：yyyy-MM-dd
    
    private String endDate;    // 改为String类型，存储格式：yyyy-MM-dd
    
    private Boolean isActive = true;  // 是否在用
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    // 将String转换为LocalTime的便捷方法
    public LocalTime getTakeTimeMorningAsLocalTime() {
        return parseTimeString(takeTimeMorning);
    }
    
    public LocalTime getTakeTimeNoonAsLocalTime() {
        return parseTimeString(takeTimeNoon);
    }
    
    public LocalTime getTakeTimeEveningAsLocalTime() {
        return parseTimeString(takeTimeEvening);
    }
    
    public LocalTime getTakeTimeNightAsLocalTime() {
        return parseTimeString(takeTimeNight);
    }
    
    // 将String转换为LocalDate的便捷方法
    public LocalDate getStartDateAsLocalDate() {
        return parseDateString(startDate);
    }
    
    public LocalDate getEndDateAsLocalDate() {
        return parseDateString(endDate);
    }
    
    // 设置LocalTime的方法
    public void setTakeTimeMorningFromLocalTime(LocalTime time) {
        this.takeTimeMorning = time != null ? time.format(DateTimeFormatter.ofPattern("HH:mm")) : null;
    }
    
    public void setTakeTimeNoonFromLocalTime(LocalTime time) {
        this.takeTimeNoon = time != null ? time.format(DateTimeFormatter.ofPattern("HH:mm")) : null;
    }
    
    public void setTakeTimeEveningFromLocalTime(LocalTime time) {
        this.takeTimeEvening = time != null ? time.format(DateTimeFormatter.ofPattern("HH:mm")) : null;
    }
    
    public void setTakeTimeNightFromLocalTime(LocalTime time) {
        this.takeTimeNight = time != null ? time.format(DateTimeFormatter.ofPattern("HH:mm")) : null;
    }
    
    // 设置LocalDate的方法
    public void setStartDateFromLocalDate(LocalDate date) {
        this.startDate = date != null ? date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;
    }
    
    public void setEndDateFromLocalDate(LocalDate date) {
        this.endDate = date != null ? date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;
    }
    
    private LocalTime parseTimeString(String timeStr) {
        if (timeStr != null && !timeStr.trim().isEmpty()) {
            try {
                return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    private LocalDate parseDateString(String dateStr) {
        if (dateStr != null && !dateStr.trim().isEmpty()) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}