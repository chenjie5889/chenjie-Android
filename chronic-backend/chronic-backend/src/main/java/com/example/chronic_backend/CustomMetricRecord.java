package com.example.chronic_backend;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "custom_metric_records")
@Data
public class CustomMetricRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "metric_id", nullable = false)
    private Long metricId;  // 关联的指标ID
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private String recordValue;  // 记录值，如：120/80
    
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;  // 记录日期
    
    private String note;  // 备注，如：早餐前测量
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}