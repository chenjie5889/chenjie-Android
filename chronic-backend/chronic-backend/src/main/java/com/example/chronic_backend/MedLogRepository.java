package com.example.chronic_backend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MedLogRepository extends JpaRepository<MedicationLog, Long> {
    List<MedicationLog> findByUserId(Long userId);
}