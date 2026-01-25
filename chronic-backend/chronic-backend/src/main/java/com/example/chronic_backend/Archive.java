package com.example.chronic_backend;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@Entity
@Table(name = "health_archives")
public class Archive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;
    
    private String gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    private Double height;
    private Double weight;
    
    @Column(columnDefinition = "TEXT")
    private String medicalHistory;
    
    // 添加toString方法以便调试
    @Override
    public String toString() {
        return "Archive{" +
                "id=" + id +
                ", userId=" + userId +
                ", gender='" + gender + '\'' +
                ", birthday=" + birthday +
                ", height=" + height +
                ", weight=" + weight +
                ", medicalHistory='" + medicalHistory + '\'' +
                '}';
    }
}