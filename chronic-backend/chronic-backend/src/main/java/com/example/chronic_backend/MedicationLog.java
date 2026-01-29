package com.example.chronic_backend;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "medication_logs")
@Data
public class MedicationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "medicine_name")
    private String medicineName;
    
    @Column(name = "take_time")
    private String takeTime;
    
    @Column(name = "log_date")
    private LocalDate logDate;
    
    @Column(name = "status")
    private Integer status; // 0:红色(异常/未服), 1:蓝色(按时)
    
    @Column(name = "created_at")
    private LocalDate createdAt = LocalDate.now();
}