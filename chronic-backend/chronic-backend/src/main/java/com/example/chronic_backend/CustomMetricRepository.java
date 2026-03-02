package com.example.chronic_backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CustomMetricRepository extends JpaRepository<CustomMetric, Long> {
    List<CustomMetric> findByUserId(Long userId);
}