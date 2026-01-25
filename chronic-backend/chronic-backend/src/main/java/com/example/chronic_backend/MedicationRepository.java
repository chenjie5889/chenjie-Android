package com.example.chronic_backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findByUserId(Long userId);
    List<Medication> findByUserIdAndDiseaseId(Long userId, Long diseaseId);
    long countByUserId(Long userId);
    long countByUserIdAndIsActive(Long userId, Boolean isActive);
}