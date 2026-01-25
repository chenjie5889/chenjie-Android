package com.example.chronic_backend;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "diseases")
@Data
public class Disease {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private String diseaseName;  // 疾病名称，如：高血压、糖尿病
    
    private String diseaseType;  // 疾病类型，如：原发性、继发性
    
    @Column(columnDefinition = "TEXT")
    private String diagnosisInfo;  // 诊断信息
    
    @Column(columnDefinition = "TEXT")
    private String symptoms;  // 症状描述
    
    private String diagnosisDate;  // 改为String类型，存储格式：yyyy-MM-dd
    
    private String hospital;  // 确诊医院
    
    private String doctor;    // 主治医生
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    // 将String转换为LocalDate的便捷方法
    public LocalDate getDiagnosisDateAsLocalDate() {
        if (diagnosisDate != null && !diagnosisDate.trim().isEmpty()) {
            try {
                return LocalDate.parse(diagnosisDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    // 设置LocalDate的方法
    public void setDiagnosisDateFromLocalDate(LocalDate date) {
        if (date != null) {
            this.diagnosisDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            this.diagnosisDate = null;
        }
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