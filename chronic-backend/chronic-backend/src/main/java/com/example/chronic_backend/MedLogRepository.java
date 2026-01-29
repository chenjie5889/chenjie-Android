package com.example.chronic_backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface MedLogRepository extends JpaRepository<MedicationLog, Long> {
    List<MedicationLog> findByUserId(Long userId);
    
    // 新增方法：按用户ID和日期查询
    List<MedicationLog> findByUserIdAndLogDate(Long userId, LocalDate logDate);
    
    // 新增方法：获取用户某月的用药记录
    @Query("SELECT ml FROM MedicationLog ml WHERE ml.userId = :userId AND YEAR(ml.logDate) = :year AND MONTH(ml.logDate) = :month")
    List<MedicationLog> findByUserIdAndMonth(Long userId, int year, int month);
}