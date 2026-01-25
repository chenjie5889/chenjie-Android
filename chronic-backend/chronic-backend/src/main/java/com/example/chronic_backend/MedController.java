package com.example.chronic_backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@CrossOrigin
@RestController
@RequestMapping("/api/med")
public class MedController {
    
    @Autowired
    private DiseaseRepository diseaseRepository;
    
    @Autowired
    private MedicationRepository medicationRepository;
    
    // --- 疾病管理接口 ---
    @GetMapping("/diseases")
    public List<Disease> getDiseases(@RequestParam Long userId) {
        List<Disease> diseases = diseaseRepository.findByUserId(userId);
        System.out.println("获取疾病数据，用户ID: " + userId + ", 数量: " + diseases.size());
        return diseases;
    }
    
    @PostMapping("/disease/add")
    public SmsResponse addDisease(@RequestBody Disease disease) {
        try {
            System.out.println("添加疾病请求: " + disease);
            
            // 验证必要字段
            if (disease.getDiseaseName() == null || disease.getDiseaseName().trim().isEmpty()) {
                return new SmsResponse(400, "疾病名称不能为空", null);
            }
            if (disease.getUserId() == null) {
                return new SmsResponse(400, "用户ID不能为空", null);
            }
            
            diseaseRepository.save(disease);
            System.out.println("疾病保存成功，ID: " + disease.getId());
            return new SmsResponse(200, "疾病信息添加成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "添加失败: " + e.getMessage(), null);
        }
    }
    
    @PostMapping("/disease/update")
    public SmsResponse updateDisease(@RequestBody Disease disease) {
        try {
            System.out.println("更新疾病请求: " + disease);
            
            Optional<Disease> existing = diseaseRepository.findById(disease.getId());
            if (existing.isPresent()) {
                Disease existingDisease = existing.get();
                // 只更新允许修改的字段
                existingDisease.setDiseaseName(disease.getDiseaseName());
                existingDisease.setDiseaseType(disease.getDiseaseType());
                existingDisease.setDiagnosisInfo(disease.getDiagnosisInfo());
                existingDisease.setSymptoms(disease.getSymptoms());
                existingDisease.setDiagnosisDate(disease.getDiagnosisDate());
                existingDisease.setHospital(disease.getHospital());
                existingDisease.setDoctor(disease.getDoctor());
                
                diseaseRepository.save(existingDisease);
                return new SmsResponse(200, "更新成功", null);
            } else {
                return new SmsResponse(404, "疾病信息不存在", null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "更新失败: " + e.getMessage(), null);
        }
    }
    
    @DeleteMapping("/disease/delete")
    public SmsResponse deleteDisease(@RequestParam Long id) {
        try {
            if (!diseaseRepository.existsById(id)) {
                return new SmsResponse(404, "疾病信息不存在", null);
            }
            diseaseRepository.deleteById(id);
            return new SmsResponse(200, "删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "删除失败: " + e.getMessage(), null);
        }
    }
    
    // --- 用药管理接口 ---
    @GetMapping("/medications")
    public List<Medication> getMedications(@RequestParam Long userId) {
        List<Medication> medications = medicationRepository.findByUserId(userId);
        System.out.println("获取用药数据，用户ID: " + userId + ", 数量: " + medications.size());
        return medications;
    }
    
    @GetMapping("/medications/byDisease")
    public List<Medication> getMedicationsByDisease(@RequestParam Long userId, @RequestParam Long diseaseId) {
        return medicationRepository.findByUserIdAndDiseaseId(userId, diseaseId);
    }
    
    @PostMapping("/medication/add")
    public SmsResponse addMedication(@RequestBody Medication medication) {
        try {
            System.out.println("添加用药请求: " + medication);
            
            // 验证必要字段
            if (medication.getMedicineName() == null || medication.getMedicineName().trim().isEmpty()) {
                return new SmsResponse(400, "药品名称不能为空", null);
            }
            if (medication.getUserId() == null) {
                return new SmsResponse(400, "用户ID不能为空", null);
            }
            
            // 设置默认值
            if (medication.getIsActive() == null) {
                medication.setIsActive(true);
            }
            
            medicationRepository.save(medication);
            System.out.println("用药保存成功，ID: " + medication.getId());
            return new SmsResponse(200, "用药方案添加成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "添加失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/medication/update")
    public SmsResponse updateMedication(@RequestBody Medication medication) {
        try {
            System.out.println("更新用药请求: " + medication);
            
            Optional<Medication> existing = medicationRepository.findById(medication.getId());
            if (existing.isPresent()) {
                Medication existingMed = existing.get();
                // 更新所有字段
                existingMed.setMedicineName(medication.getMedicineName());
                existingMed.setGenericName(medication.getGenericName());
                existingMed.setDosage(medication.getDosage());
                existingMed.setFrequency(medication.getFrequency());
                existingMed.setTakeTimeMorning(medication.getTakeTimeMorning());
                existingMed.setTakeTimeNoon(medication.getTakeTimeNoon());
                existingMed.setTakeTimeEvening(medication.getTakeTimeEvening());
                existingMed.setTakeTimeNight(medication.getTakeTimeNight());
                existingMed.setInstructions(medication.getInstructions());
                existingMed.setPrecautions(medication.getPrecautions());
                existingMed.setSideEffects(medication.getSideEffects());
                existingMed.setContraindications(medication.getContraindications());
                existingMed.setMechanism(medication.getMechanism());
                existingMed.setStorage(medication.getStorage());
                existingMed.setStartDate(medication.getStartDate());
                existingMed.setEndDate(medication.getEndDate());
                existingMed.setIsActive(medication.getIsActive());
                existingMed.setDiseaseId(medication.getDiseaseId());
                
                medicationRepository.save(existingMed);
                return new SmsResponse(200, "更新成功", null);
            } else {
                return new SmsResponse(404, "用药方案不存在", null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "更新失败: " + e.getMessage(), null);
        }
    }
    
    @DeleteMapping("/medication/delete")
    public SmsResponse deleteMedication(@RequestParam Long id) {
        try {
            if (!medicationRepository.existsById(id)) {
                return new SmsResponse(404, "用药方案不存在", null);
            }
            medicationRepository.deleteById(id);
            return new SmsResponse(200, "删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "删除失败: " + e.getMessage(), null);
        }
    }
    
    // --- 统计接口 ---
    @GetMapping("/stats")
    public MedStats getMedStats(@RequestParam Long userId) {
        MedStats stats = new MedStats();
        try {
            stats.totalDiseases = diseaseRepository.countByUserId(userId);
            stats.totalMedications = medicationRepository.countByUserId(userId);
            stats.activeMedications = medicationRepository.countByUserIdAndIsActive(userId, true);
            System.out.println("统计信息 - 疾病: " + stats.totalDiseases + ", 用药: " + stats.totalMedications + ", 在用: " + stats.activeMedications);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stats;
    }
    
    // 统计类
    @lombok.Data
    public static class MedStats {
        private long totalDiseases;
        private long totalMedications;
        private long activeMedications;
    }
}