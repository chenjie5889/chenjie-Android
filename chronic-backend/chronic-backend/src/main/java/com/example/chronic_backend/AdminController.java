package com.example.chronic_backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@CrossOrigin
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    @Autowired
    private AdminRepository adminRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ArchiveRepository archiveRepository;
    
    @Autowired
    private DiseaseRepository diseaseRepository;
    
    @Autowired
    private MedicationRepository medicationRepository;
    
    @Autowired
    private FamilyRelationshipRepository familyRepository;
    
    // 管理员登录（保持不变）
    @PostMapping("/login")
    public AdminResponse adminLogin(@RequestParam String username, @RequestParam String password) {
        try {
            Admin admin = adminRepository.findByUsername(username);
            
            if (admin == null) {
                return new AdminResponse(400, "管理员账户不存在", null);
            }
            
            if (admin.getStatus() != null && admin.getStatus() == 0) {
                return new AdminResponse(400, "该账户已被禁用", null);
            }
            
            if (!admin.getPassword().equals(password)) {
                return new AdminResponse(400, "密码错误", null);
            }
            
            admin.setLastLogin(LocalDateTime.now());
            adminRepository.save(admin);
            
            Map<String, Object> adminInfo = new HashMap<>();
            adminInfo.put("id", admin.getId());
            adminInfo.put("username", admin.getUsername());
            adminInfo.put("realName", admin.getRealName());
            adminInfo.put("role", admin.getRole());
            
            return new AdminResponse(200, "登录成功", adminInfo);
        } catch (Exception e) {
            e.printStackTrace();
            return new AdminResponse(500, "登录失败: " + e.getMessage(), null);
        }
    }
    
    // 获取当前管理员信息（保持不变）
    @GetMapping("/profile")
    public AdminResponse getAdminProfile(@RequestParam Long adminId) {
        try {
            Optional<Admin> adminOpt = adminRepository.findById(adminId);
            if (!adminOpt.isPresent()) {
                return new AdminResponse(404, "管理员不存在", null);
            }
            
            Admin admin = adminOpt.get();
            Map<String, Object> adminInfo = new HashMap<>();
            adminInfo.put("id", admin.getId());
            adminInfo.put("username", admin.getUsername());
            adminInfo.put("realName", admin.getRealName());
            adminInfo.put("phone", admin.getPhone());
            adminInfo.put("role", admin.getRole());
            adminInfo.put("lastLogin", admin.getLastLogin());
            
            return new AdminResponse(200, "获取成功", adminInfo);
        } catch (Exception e) {
            e.printStackTrace();
            return new AdminResponse(500, "获取失败: " + e.getMessage(), null);
        }
    }
    
    // 修改密码（保持不变）
    @PostMapping("/changePassword")
    public AdminResponse changePassword(
        @RequestParam Long adminId,
        @RequestParam String oldPassword,
        @RequestParam String newPassword
    ) {
        try {
            Optional<Admin> adminOpt = adminRepository.findById(adminId);
            if (!adminOpt.isPresent()) {
                return new AdminResponse(404, "管理员不存在", null);
            }
            
            Admin admin = adminOpt.get();
            
            if (!admin.getPassword().equals(oldPassword)) {
                return new AdminResponse(400, "原密码错误", null);
            }
            
            admin.setPassword(newPassword);
            adminRepository.save(admin);
            
            return new AdminResponse(200, "密码修改成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new AdminResponse(500, "修改失败: " + e.getMessage(), null);
        }
    }
    
    // 获取用户列表 - 添加查询功能
    @GetMapping("/users")
    public UserListResponse getUserList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        try {
            List<User> allUsers = userRepository.findAll();
            
            // 如果有关键词，进行筛选
            List<User> filteredUsers = new ArrayList<>();
            if (keyword != null && !keyword.trim().isEmpty()) {
                String searchKey = keyword.toLowerCase();
                for (User user : allUsers) {
                    boolean match = false;
                    // 搜索ID
                    if (String.valueOf(user.getId()).contains(searchKey)) {
                        match = true;
                    }
                    // 搜索手机号
                    if (user.getPhone() != null && user.getPhone().toLowerCase().contains(searchKey)) {
                        match = true;
                    }
                    // 搜索昵称
                    if (user.getNickname() != null && user.getNickname().toLowerCase().contains(searchKey)) {
                        match = true;
                    }
                    // 搜索真实姓名
                    if (user.getRealName() != null && user.getRealName().toLowerCase().contains(searchKey)) {
                        match = true;
                    }
                    
                    if (match) {
                        filteredUsers.add(user);
                    }
                }
            } else {
                filteredUsers = allUsers;
            }
            
            int total = filteredUsers.size();
            int totalPages = (int) Math.ceil((double) total / size);
            int start = (page - 1) * size;
            int end = Math.min(start + size, total);
            
            List<User> pageUsers = filteredUsers.subList(start, end);
            
            List<AdminUserInfo> userInfos = new ArrayList<>();
            for (User user : pageUsers) {
                AdminUserInfo info = new AdminUserInfo();
                info.setId(user.getId());
                info.setPhone(user.getPhone());
                info.setNickname(user.getNickname());
                info.setRealName(user.getRealName());
                
                // 统计用户数据，但不显示具体内容
                long diseaseCount = diseaseRepository.countByUserId(user.getId());
                Optional<Archive> archive = archiveRepository.findByUserId(user.getId());
                
                info.setHasArchive(archive.isPresent());
                info.setDiseaseCount(diseaseCount);
                
                userInfos.add(info);
            }
            
            return new UserListResponse(200, "获取成功", userInfos, total, totalPages, page);
        } catch (Exception e) {
            e.printStackTrace();
            return new UserListResponse(500, "获取失败: " + e.getMessage(), 
                                       new ArrayList<>(), 0, 0, page);
        }
    }
    
    // 获取统计数据 - 添加图表数据
    @GetMapping("/statistics")
    public StatisticsResponse getStatistics() {
        try {
            StatisticsResponse response = new StatisticsResponse();
            
            List<User> allUsers = userRepository.findAll();
            long totalUsers = allUsers.size();
            
            long usersWithArchive = 0;
            long usersWithDisease = 0;
            long totalFamilies = 0;
            
            // 性别统计
            Map<String, Long> genderStats = new HashMap<>();
            genderStats.put("男", 0L);
            genderStats.put("女", 0L);
            genderStats.put("未知", 0L);
            
            // 年龄段统计
            Map<String, Long> ageStats = new HashMap<>();
            ageStats.put("18岁以下", 0L);
            ageStats.put("18-30岁", 0L);
            ageStats.put("31-45岁", 0L);
            ageStats.put("46-60岁", 0L);
            ageStats.put("60岁以上", 0L);
            
            for (User user : allUsers) {
                Optional<Archive> archive = archiveRepository.findByUserId(user.getId());
                if (archive.isPresent()) {
                    usersWithArchive++;
                    
                    // 统计性别
                    String gender = archive.get().getGender();
                    if (gender != null) {
                        if (gender.equals("男")) {
                            genderStats.put("男", genderStats.get("男") + 1);
                        } else if (gender.equals("女")) {
                            genderStats.put("女", genderStats.get("女") + 1);
                        } else {
                            genderStats.put("未知", genderStats.get("未知") + 1);
                        }
                    } else {
                        genderStats.put("未知", genderStats.get("未知") + 1);
                    }
                    
                    // 统计年龄
                    if (archive.get().getBirthday() != null) {
                        int birthYear = archive.get().getBirthday().getYear();
                        int currentYear = LocalDateTime.now().getYear();
                        int age = currentYear - birthYear;
                        
                        if (age < 18) {
                            ageStats.put("18岁以下", ageStats.get("18岁以下") + 1);
                        } else if (age <= 30) {
                            ageStats.put("18-30岁", ageStats.get("18-30岁") + 1);
                        } else if (age <= 45) {
                            ageStats.put("31-45岁", ageStats.get("31-45岁") + 1);
                        } else if (age <= 60) {
                            ageStats.put("46-60岁", ageStats.get("46-60岁") + 1);
                        } else {
                            ageStats.put("60岁以上", ageStats.get("60岁以上") + 1);
                        }
                    }
                }
                
                long diseaseCount = diseaseRepository.countByUserId(user.getId());
                if (diseaseCount > 0) {
                    usersWithDisease++;
                }
                
                // 家属关系统计
                List<FamilyRelationship> relationships = familyRepository.findAllByUserId(user.getId());
                totalFamilies += relationships.stream()
                    .filter(r -> r.getStatus() == 1)
                    .count();
            }
            
            response.setTotalUsers(totalUsers);
            response.setUsersWithArchive(usersWithArchive);
            response.setUsersWithDisease(usersWithDisease);
            response.setTotalFamilies(totalFamilies);
            response.setGenderStats(genderStats);
            response.setAgeStats(ageStats);
            response.setCode(200);
            response.setMsg("获取成功");
            
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return new StatisticsResponse(500, "获取失败: " + e.getMessage(), 
                    0, 0, 0, 0, new HashMap<>(), new HashMap<>());
        }
    }
    
    // 删除用户（保持不变）
    @DeleteMapping("/deleteUser")
    public AdminResponse deleteUser(@RequestParam Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                return new AdminResponse(404, "用户不存在", null);
            }
            
            userRepository.deleteById(userId);
            
            return new AdminResponse(200, "用户删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new AdminResponse(500, "删除失败: " + e.getMessage(), null);
        }
    }
    
    // 查看用户详情 - 修改为只显示基本信息，不显示敏感数据
    @GetMapping("/userDetail")
    public AdminResponse getUserDetail(@RequestParam Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                return new AdminResponse(404, "用户不存在", null);
            }
            
            User user = userOpt.get();
            
            // 只返回基本信息，不返回档案、疾病、家属等敏感数据
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("phone", user.getPhone());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("realName", user.getRealName());
            
            // 只返回统计数量，不返回具体内容
            long diseaseCount = diseaseRepository.countByUserId(userId);
            Optional<Archive> archive = archiveRepository.findByUserId(userId);
            
            userInfo.put("hasArchive", archive.isPresent());
            userInfo.put("diseaseCount", diseaseCount);
            
            // 家属关系数量
            List<FamilyRelationship> relationships = familyRepository.findAllByUserId(userId);
            long familyCount = relationships.stream()
                .filter(r -> r.getStatus() == 1)
                .count();
            userInfo.put("familyCount", familyCount);
            
            return new AdminResponse(200, "获取成功", userInfo);
        } catch (Exception e) {
            e.printStackTrace();
            return new AdminResponse(500, "获取失败: " + e.getMessage(), null);
        }
    }
    
    // 响应类
    @lombok.Data
    public static class AdminResponse {
        private int code;
        private String msg;
        private Object data;
        
        public AdminResponse(int code, String msg, Object data) {
            this.code = code;
            this.msg = msg;
            this.data = data;
        }
    }
    
    @lombok.Data
    public static class UserListResponse {
        private int code;
        private String msg;
        private List<AdminUserInfo> users;
        private int total;
        private int totalPages;
        private int currentPage;
        
        public UserListResponse(int code, String msg, List<AdminUserInfo> users, 
                               int total, int totalPages, int currentPage) {
            this.code = code;
            this.msg = msg;
            this.users = users;
            this.total = total;
            this.totalPages = totalPages;
            this.currentPage = currentPage;
        }
    }
    
    @lombok.Data
    public static class AdminUserInfo {
        private Long id;
        private String phone;
        private String nickname;
        private String realName;
        private boolean hasArchive;
        private long diseaseCount;
    }
    
    @lombok.Data
    public static class StatisticsResponse {
        private int code;
        private String msg;
        private long totalUsers;
        private long usersWithArchive;
        private long usersWithDisease;
        private long totalFamilies;
        private Map<String, Long> genderStats;  // 性别统计
        private Map<String, Long> ageStats;     // 年龄段统计
        
        public StatisticsResponse() {}
        
        public StatisticsResponse(int code, String msg, long totalUsers, 
                                 long usersWithArchive, long usersWithDisease, 
                                 long totalFamilies, Map<String, Long> genderStats,
                                 Map<String, Long> ageStats) {
            this.code = code;
            this.msg = msg;
            this.totalUsers = totalUsers;
            this.usersWithArchive = usersWithArchive;
            this.usersWithDisease = usersWithDisease;
            this.totalFamilies = totalFamilies;
            this.genderStats = genderStats;
            this.ageStats = ageStats;
        }
    }
}