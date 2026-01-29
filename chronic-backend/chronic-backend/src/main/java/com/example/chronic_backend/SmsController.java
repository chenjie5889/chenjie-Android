package com.example.chronic_backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CrossOrigin
@RestController
@RequestMapping("/api")
public class SmsController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ArchiveRepository archiveRepository;
    
    @Autowired
    private MedLogRepository medLogRepository;

    @Autowired
    private MedicationRepository medicationRepository;
    private Map<String, String> codeMap = new ConcurrentHashMap<>();

    // --- 用户认证模块 ---
    @PostMapping("/login")
    public LoginResponse login(@RequestParam String phone, @RequestParam String password) {
        User user = userRepository.findByPhone(phone);
        if (user != null && user.getPassword().equals(password)) {
            if (user.getIdCard() == null || user.getIdCard().isEmpty()) {
                return new LoginResponse(201, "请先完成身份绑定", user.getNickname(), user.getId());
            }
            return new LoginResponse(200, "登录成功", user.getNickname(), user.getId());
        }
        return new LoginResponse(400, "手机号或密码错误", null, null);
    }

    @PostMapping("/register")
    public SmsResponse register(@RequestParam String phone, @RequestParam String code, @RequestParam String password) {
        if (!code.equals(codeMap.get(phone))) {
            return new SmsResponse(400, "验证码错误", null);
        }
        if (userRepository.findByPhone(phone) != null) {
            return new SmsResponse(400, "该手机号已注册", null);
        }
        User user = new User();
        user.setPhone(phone);
        user.setPassword(password);
        user.setNickname("用户" + phone.substring(7));
        userRepository.save(user);
        return new SmsResponse(200, "注册成功", null);
    }

    @PostMapping("/bindIdentity")
    public SmsResponse bindIdentity(@RequestParam String phone, @RequestParam String realName, @RequestParam String idCard) {
        User user = userRepository.findByPhone(phone);
        if (user != null) {
            user.setRealName(realName);
            user.setIdCard(idCard);
            userRepository.save(user);
            return new SmsResponse(200, "绑定成功", null);
        }
        return new SmsResponse(400, "用户不存在", null);
    }

    @PostMapping("/sendSms")
    public SmsResponse sendSms(@RequestParam String phone) {
        String code = String.valueOf((int)((Math.random() * 9 + 1) * 100000));
        codeMap.put(phone, code);
        return new SmsResponse(200, "验证码已发送", code);
    }

    // --- 健康档案模块 ---
    @GetMapping("/getArchive")
    public ArchivePlusResponse getArchive(@RequestParam Long userId) {
        Optional<Archive> archiveOpt = archiveRepository.findByUserId(userId);
        User user = userRepository.findById(userId).orElse(null);
        
        Archive resArchive;
        String realName = (user != null && user.getRealName() != null) ? user.getRealName() : "未绑定";

        if (archiveOpt.isPresent()) {
            resArchive = archiveOpt.get();
            return new ArchivePlusResponse(resArchive, realName, true);
        } else {
            // 数据库无档案，创建临时对象并从身份证提取信息
            resArchive = new Archive();
            resArchive.setUserId(userId);
            if (user != null && user.getIdCard() != null && user.getIdCard().length() == 18) {
                String idCard = user.getIdCard();
                try {
                    // 提取生日 (6-14位)
                    String birthStr = idCard.substring(6, 14);
                    LocalDate birthday = LocalDate.parse(birthStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                    resArchive.setBirthday(birthday);
                    // 提取性别 (17位，奇男偶女)
                    int genderCode = Character.getNumericValue(idCard.charAt(16));
                    resArchive.setGender(genderCode % 2 == 1 ? "男" : "女");
                } catch (Exception e) {
                    // 身份证格式错误，使用默认值
                    resArchive.setGender("未设定");
                }
            } else {
                // 无身份证信息
                resArchive.setGender("未设定");
            }
            return new ArchivePlusResponse(resArchive, realName, false);
        }
    }

    @PostMapping("/updateArchive")
    public SmsResponse updateArchive(@RequestBody Archive archive) {
        try {
            // 验证生日格式
            if (archive.getBirthday() != null) {
                try {
                    LocalDate.parse(archive.getBirthday().toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } catch (Exception e) {
                    return new SmsResponse(400, "生日格式错误，应为YYYY-MM-DD", null);
                }
            }
            
            // 关键：通过 userId 查找现有记录实现覆盖更新，确保档案唯一
            Optional<Archive> existing = archiveRepository.findByUserId(archive.getUserId());
            if (existing.isPresent()) {
                Archive existingArchive = existing.get();
                // 只更新允许修改的字段
                existingArchive.setGender(archive.getGender());
                existingArchive.setBirthday(archive.getBirthday());
                existingArchive.setHeight(archive.getHeight());
                existingArchive.setWeight(archive.getWeight());
                existingArchive.setMedicalHistory(archive.getMedicalHistory());
                archiveRepository.save(existingArchive);
            } else {
                // 创建新档案
                archiveRepository.save(archive);
            }
            return new SmsResponse(200, "保存成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "保存失败: " + e.getMessage(), null);
        }
    }

    // --- 用药记录模块 ---
    @GetMapping("/getMedLogs")
    public List<MedicationLog> getMedLogs(@RequestParam Long userId) {
        return medLogRepository.findByUserId(userId);
    }

@PostMapping("/recordMedicationTaken")
public SmsResponse recordMedicationTaken(
    @RequestParam Long userId,
    @RequestParam String medicineName,
    @RequestParam String date,
    @RequestParam String time,
    @RequestParam Integer status
) {
    try {
        // 1. 保存到 medication_logs 表
        MedicationLog log = new MedicationLog();
        log.setUserId(userId);
        log.setLogDate(LocalDate.parse(date));
        log.setStatus(status);
        log.setMedicineName(medicineName);  // 新增字段
        log.setTakeTime(time);  // 新增字段
        medLogRepository.save(log);
        
        // 2. 记录详细的用药日志
        saveMedicationDetailLog(userId, medicineName, date, time, status);
        
        return new SmsResponse(200, "服药记录保存成功", null);
    } catch (Exception e) {
        e.printStackTrace();
        return new SmsResponse(500, "保存失败: " + e.getMessage(), null);
    }
}

// 新增方法：保存详细用药记录
private void saveMedicationDetailLog(Long userId, String medicineName, 
                                     String date, String time, Integer status) {
    try {
        // 这里可以创建更详细的日志表，暂时记录到控制台
        System.out.println("详细用药记录 - 用户ID: " + userId + 
                          ", 药品: " + medicineName + 
                          ", 日期: " + date + 
                          ", 时间: " + time + 
                          ", 状态: " + (status == 1 ? "按时" : "漏服"));
    } catch (Exception e) {
        e.printStackTrace();
    }
}

// 新增方法：获取用户今日用药记录
@GetMapping("/getTodayMedicationLogs")
public List<MedicationLogResponse> getTodayMedicationLogs(@RequestParam Long userId) {
    try {
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // 从数据库中获取今日用药记录
        List<MedicationLog> logs = medLogRepository.findByUserIdAndLogDate(userId, today);
        
        List<MedicationLogResponse> responses = new ArrayList<>();
        for (MedicationLog log : logs) {
            MedicationLogResponse response = new MedicationLogResponse();
            response.setMedicineName(log.getMedicineName());
            response.setTakeTime(log.getTakeTime());
            response.setStatus(log.getStatus());
            response.setLogDate(log.getLogDate().toString());
            responses.add(response);
        }
        
        return responses;
    } catch (Exception e) {
        e.printStackTrace();
        return new ArrayList<>();
    }
}

// 新增响应类
@lombok.Data
public static class MedicationLogResponse {
    private String medicineName;
    private String takeTime;
    private Integer status;
    private String logDate;
}

    @GetMapping("/getTodayMedications")
    public List<Medication> getTodayMedications(@RequestParam Long userId) {
        // 获取用户今日有效的用药方案
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        List<Medication> allMeds = medicationRepository.findByUserId(userId);
        List<Medication> todayMeds = new ArrayList<>();
        
        for (Medication med : allMeds) {
            if (Boolean.TRUE.equals(med.getIsActive())) {
                // 检查开始日期和结束日期
                boolean isActive = true;
                
                if (med.getStartDate() != null) {
                    LocalDate startDate = LocalDate.parse(med.getStartDate());
                    if (today.isBefore(startDate)) {
                        isActive = false;
                    }
                }
                
                if (med.getEndDate() != null) {
                    LocalDate endDate = LocalDate.parse(med.getEndDate());
                    if (today.isAfter(endDate)) {
                        isActive = false;
                    }
                }
                
                if (isActive) {
                    todayMeds.add(med);
                }
            }
        }
        
        return todayMeds;
    }

    

    @PostMapping("/updateUserInfo")
    public SmsResponse updateUserInfo(@RequestBody UpdateUserInfoRequest request) {
        try {
            System.out.println("=== 接收到用户信息更新请求 (JSON方式) ===");
            System.out.println("请求体对象: " + request);
            System.out.println("userId: " + request.getUserId());
            System.out.println("nickname: " + request.getNickname());
            System.out.println("realName: " + request.getRealName());
            System.out.println("idCard: " + request.getIdCard());
            
            // 验证参数
            if (request.getUserId() == null) {
                return new SmsResponse(400, "用户ID不能为空", null);
            }
            
            // 查找用户
            Optional<User> userOpt = userRepository.findById(request.getUserId());
            if (!userOpt.isPresent()) {
                return new SmsResponse(404, "用户不存在", null);
            }
            
            User user = userOpt.get();
            
            // 更新昵称（如果提供了）
            if (request.getNickname() != null && !request.getNickname().trim().isEmpty()) {
                user.setNickname(request.getNickname());
            }
            
            // 更新真实姓名（如果提供了）
            if (request.getRealName() != null && !request.getRealName().trim().isEmpty()) {
                user.setRealName(request.getRealName());
            }
            
            // 更新身份证号（如果提供了）
            if (request.getIdCard() != null && !request.getIdCard().trim().isEmpty()) {
                // 验证身份证格式
                if (request.getIdCard().length() != 18) {
                    return new SmsResponse(400, "身份证号必须为18位", null);
                }
                user.setIdCard(request.getIdCard());
            }
            
            // 保存到数据库
            userRepository.save(user);
            
            System.out.println("用户信息更新成功: " + user);
            System.out.println("更新后 - 昵称: " + user.getNickname() + 
                            ", 真实姓名: " + user.getRealName() + 
                            ", 身份证: " + user.getIdCard());
            
            return new SmsResponse(200, "用户信息更新成功", null);
            
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "更新失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/changePassword")
    public SmsResponse changePassword(
        @RequestParam Long userId,
        @RequestParam String oldPassword,
        @RequestParam String newPassword
    ) {
        try {
            System.out.println("=== 修改密码请求 ===");
            System.out.println("用户ID: " + userId);
            
            // 验证新密码长度
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return new SmsResponse(400, "新密码不能为空", null);
            }
            
            // 查找用户
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                return new SmsResponse(404, "用户不存在", null);
            }
            
            User user = userOpt.get();
            
            // 验证原密码
            if (!user.getPassword().equals(oldPassword)) {
                return new SmsResponse(400, "原密码错误", null);
            }
            
            // 更新密码
            user.setPassword(newPassword);
            userRepository.save(user);
            
            System.out.println("密码修改成功，用户ID: " + userId);
            return new SmsResponse(200, "密码修改成功", null);
            
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "修改密码失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/changePhone")
    public SmsResponse changePhone(
        @RequestParam Long userId,
        @RequestParam String oldPhone,
        @RequestParam String newPhone,
        @RequestParam String code
    ) {
        try {
            System.out.println("=== 修改手机号请求 ===");
            System.out.println("用户ID: " + userId);
            System.out.println("旧手机号: " + oldPhone);
            System.out.println("新手机号: " + newPhone);
            
            // 验证新手机号格式
            if (!newPhone.matches("^1[3-9]\\d{9}$")) {
                return new SmsResponse(400, "新手机号格式不正确", null);
            }
            
            // 验证验证码
            if (!code.equals(codeMap.get(newPhone))) {
                return new SmsResponse(400, "验证码错误", null);
            }
            
            // 查找用户
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                return new SmsResponse(404, "用户不存在", null);
            }
            
            User user = userOpt.get();
            
            // 验证旧手机号
            if (!user.getPhone().equals(oldPhone)) {
                return new SmsResponse(400, "原手机号不匹配", null);
            }
            
            // 检查新手机号是否已被使用
            User existingUser = userRepository.findByPhone(newPhone);
            if (existingUser != null && !existingUser.getId().equals(userId)) {
                return new SmsResponse(400, "新手机号已被其他用户使用", null);
            }
            
            // 更新手机号
            user.setPhone(newPhone);
            userRepository.save(user);
            
            System.out.println("手机号修改成功，用户ID: " + userId + "，新手机号: " + newPhone);
            return new SmsResponse(200, "手机号修改成功", null);
            
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "修改手机号失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/sendChangePhoneCode")
    public SmsResponse sendChangePhoneCode(@RequestParam String phone) {
        try {
            // 验证手机号格式
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                return new SmsResponse(400, "手机号格式不正确", null);
            }
            
            // 生成验证码
            String code = String.valueOf((int)((Math.random() * 9 + 1) * 100000));
            codeMap.put(phone, code);
            
            System.out.println("发送修改手机号验证码: " + phone + " -> " + code);
            return new SmsResponse(200, "验证码已发送", code);
            
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "发送验证码失败", null);
        }
    }
    
    @GetMapping("/getUserInfo")
    public UserInfoResponse getUserInfo(@RequestParam Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                UserInfoResponse response = new UserInfoResponse();
                response.setId(user.getId());
                response.setPhone(user.getPhone());
                response.setNickname(user.getNickname());
                response.setRealName(user.getRealName());
                response.setIdCard(user.getIdCard());
                return response;
            } else {
                return new UserInfoResponse(); // 返回空对象
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new UserInfoResponse();
        }
    }
    
    @lombok.Data
    public static class UpdateUserInfoRequest {
        private Long userId;
        private String nickname;
        private String realName;
        private String idCard;
        
        // 添加构造方法以便日志输出
        public UpdateUserInfoRequest() {}
        
        @Override
        public String toString() {
            return String.format("UpdateUserInfoRequest{userId=%s, nickname='%s', realName='%s', idCard='%s'}", 
                    userId, nickname, realName, idCard);
        }
    }
    // 内部封装类，用于回传档案+用户姓名
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ArchivePlusResponse {
        private Archive archive;
        private String realName;
        private boolean hasData;  // 是否有档案数据
        
        // 兼容旧的构造函数
        public ArchivePlusResponse(Archive archive, String realName) {
            this.archive = archive;
            this.realName = realName;
            this.hasData = archive != null;
        }
    }

    @lombok.Data
    public static class UserInfoResponse {
        private Long id;
        private String phone;
        private String nickname;
        private String realName;
        private String idCard;
    }
}