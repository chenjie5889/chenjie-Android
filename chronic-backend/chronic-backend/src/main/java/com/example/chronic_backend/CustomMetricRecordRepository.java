package com.example.chronic_backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CustomMetricRecordRepository extends JpaRepository<CustomMetricRecord, Long> {
    List<CustomMetricRecord> findByUserIdAndMetricIdOrderByRecordDateDesc(Long userId, Long metricId);
    
    List<CustomMetricRecord> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);
    
    @Query("SELECT r FROM CustomMetricRecord r WHERE r.userId = :userId AND r.recordDate BETWEEN :startDate AND :endDate ORDER BY r.recordDate DESC")
    List<CustomMetricRecord> findByUserIdAndDateRange(Long userId, LocalDate startDate, LocalDate endDate);
}