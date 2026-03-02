package com.example.chronic_backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@CrossOrigin
@RestController
@RequestMapping("/api/custom-metrics")
public class CustomMetricController {

    @Autowired
    private CustomMetricRepository metricRepository;
    
    @Autowired
    private CustomMetricRecordRepository recordRepository;

    // 获取用户的所有自定义指标
    @GetMapping("/list")
    public List<CustomMetric> getMetrics(@RequestParam Long userId) {
        return metricRepository.findByUserId(userId);
    }

    // 添加自定义指标
    @PostMapping("/add")
    public SmsResponse addMetric(@RequestBody CustomMetric metric) {
        try {
            if (metric.getMetricName() == null || metric.getMetricName().trim().isEmpty()) {
                return new SmsResponse(400, "指标名称不能为空", null);
            }
            metricRepository.save(metric);
            return new SmsResponse(200, "指标添加成功", String.valueOf(metric.getId()));
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "添加失败: " + e.getMessage(), null);
        }
    }

    // 删除自定义指标
    @DeleteMapping("/delete")
    public SmsResponse deleteMetric(@RequestParam Long metricId) {
        try {
            metricRepository.deleteById(metricId);
            return new SmsResponse(200, "删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "删除失败: " + e.getMessage(), null);
        }
    }

    // 获取指定指标的记录
    @GetMapping("/records")
    public List<CustomMetricRecord> getMetricRecords(
            @RequestParam Long userId,
            @RequestParam Long metricId) {
        return recordRepository.findByUserIdAndMetricIdOrderByRecordDateDesc(userId, metricId);
    }

    // 添加记录
    @PostMapping("/record/add")
    public SmsResponse addRecord(@RequestBody CustomMetricRecord record) {
        try {
            if (record.getRecordValue() == null || record.getRecordValue().trim().isEmpty()) {
                return new SmsResponse(400, "记录值不能为空", null);
            }
            if (record.getRecordDate() == null) {
                record.setRecordDate(LocalDate.now());
            }
            recordRepository.save(record);
            return new SmsResponse(200, "记录添加成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "添加失败: " + e.getMessage(), null);
        }
    }

    // 删除记录
    @DeleteMapping("/record/delete")
    public SmsResponse deleteRecord(@RequestParam Long recordId) {
        try {
            recordRepository.deleteById(recordId);
            return new SmsResponse(200, "删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "删除失败: " + e.getMessage(), null);
        }
    }

    // 获取用户某天的所有记录
    @GetMapping("/records/by-date")
    public List<CustomMetricRecord> getRecordsByDate(
            @RequestParam Long userId,
            @RequestParam String date) {
        LocalDate targetDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return recordRepository.findByUserIdAndRecordDate(userId, targetDate);
    }
}