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
    private AdminRepository adminRepository;  // 新增
    
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
    
    // 管理员登录
    @PostMapping("/login")
    public AdminResponse adminLogin(@RequestParam String username, @RequestParam String password) {
        try {
            // 从数据库验证管理员账户
            Admin admin = adminRepository.findByUsername(username);
            
            if (admin == null) {
                return new AdminResponse(400, "管理员账户不存在", null);
            }
            
            // 检查状态
            if (admin.getStatus() != null && admin.getStatus() == 0) {
                return new AdminResponse(400, "该账户已被禁用", null);
            }
            
            // 验证密码（注意：实际项目中应该使用加密密码）
            if (!admin.getPassword().equals(password)) {
                return new AdminResponse(400, "密码错误", null);
            }
            
            // 更新最后登录时间
            admin.setLastLogin(LocalDateTime.now());
            adminRepository.save(admin);
            
            // 登录成功，返回管理员信息
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
    
    // 获取当前管理员信息
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
    
    // 修改密码
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
            
            // 验证原密码
            if (!admin.getPassword().equals(oldPassword)) {
                return new AdminResponse(400, "原密码错误", null);
            }
            
            // 更新密码
            admin.setPassword(newPassword);
            adminRepository.save(admin);
            
            return new AdminResponse(200, "密码修改成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new AdminResponse(500, "修改失败: " + e.getMessage(), null);
        }
    }
    
    // 获取用户列表（保持不变）
    @GetMapping("/users")
    public UserListResponse getUserList(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        try {
            List<User> allUsers = userRepository.findAll();
            
            // 分页逻辑
            int total = allUsers.size();
            int totalPages = (int) Math.ceil((double) total / size);
            int start = (page - 1) * size;
            int end = Math.min(start + size, total);
            
            List<User> pageUsers = allUsers.subList(start, end);
            
            List<AdminUserInfo> userInfos = new ArrayList<>();
            for (User user : pageUsers) {
                AdminUserInfo info = new AdminUserInfo();
                info.setId(user.getId());
                info.setPhone(user.getPhone());
                info.setNickname(user.getNickname());
                info.setRealName(user.getRealName());
                info.setIdCard(user.getIdCard());
                
                // 统计用户数据
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
    
    // 获取统计数据（保持不变）
    @GetMapping("/statistics")
    public StatisticsResponse getStatistics() {
        try {
            StatisticsResponse response = new StatisticsResponse();
            
            // 总用户数
            long totalUsers = userRepository.count();
            
            // 有档案的用户数
            long usersWithArchive = 0;
            long usersWithDisease = 0;
            long totalFamilies = 0;
            
            List<User> allUsers = userRepository.findAll();
            for (User user : allUsers) {
                Optional<Archive> archive = archiveRepository.findByUserId(user.getId());
                if (archive.isPresent()) {
                    usersWithArchive++;
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
            response.setCode(200);
            response.setMsg("获取成功");
            
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return new StatisticsResponse(500, "获取失败: " + e.getMessage(), 0, 0, 0, 0);
        }
    }
    
    // 删除用户（保持不变）
    @DeleteMapping("/deleteUser")
    public AdminResponse deleteUser(@RequestParam Long userId) {
        try {
            // 检查用户是否存在
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                return new AdminResponse(404, "用户不存在", null);
            }
            
            // 删除用户（关联数据会自动删除，因为有外键约束）
            userRepository.deleteById(userId);
            
            return new AdminResponse(200, "用户删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new AdminResponse(500, "删除失败: " + e.getMessage(), null);
        }
    }
    
    // 查看用户详情（保持不变）
    @GetMapping("/userDetail")
    public UserDetailResponse getUserDetail(@RequestParam Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                return new UserDetailResponse(404, "用户不存在", null, null, null);
            }
            
            User user = userOpt.get();
            Optional<Archive> archive = archiveRepository.findByUserId(userId);
            List<Disease> diseases = diseaseRepository.findByUserId(userId);
            
            return new UserDetailResponse(200, "获取成功", user, archive.orElse(null), diseases);
        } catch (Exception e) {
            e.printStackTrace();
            return new UserDetailResponse(500, "获取失败: " + e.getMessage(), null, null, null);
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
        private String idCard;
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
        
        public StatisticsResponse() {}
        
        public StatisticsResponse(int code, String msg, long totalUsers, 
                                 long usersWithArchive, long usersWithDisease, long totalFamilies) {
            this.code = code;
            this.msg = msg;
            this.totalUsers = totalUsers;
            this.usersWithArchive = usersWithArchive;
            this.usersWithDisease = usersWithDisease;
            this.totalFamilies = totalFamilies;
        }
    }
    
    @lombok.Data
    public static class UserDetailResponse {
        private int code;
        private String msg;
        private User user;
        private Archive archive;
        private List<Disease> diseases;
        
        public UserDetailResponse(int code, String msg, User user, Archive archive, List<Disease> diseases) {
            this.code = code;
            this.msg = msg;
            this.user = user;
            this.archive = archive;
            this.diseases = diseases;
        }
    }
}
