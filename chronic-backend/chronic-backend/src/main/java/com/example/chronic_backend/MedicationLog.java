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

    private Long userId;
    private LocalDate logDate;
    private Integer status; // 0:红色(异常/未服), 1:蓝色(按时)
}