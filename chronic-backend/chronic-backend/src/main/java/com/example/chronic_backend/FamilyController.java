// FamilyController.java - 修复移除家属功能
package com.example.chronic_backend;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@CrossOrigin
@RestController
@RequestMapping("/api/family")
public class FamilyController {
    
    @Autowired
    private FamilyRelationshipRepository familyRepository;
    
    @Autowired
    private DataPermissionRepository permissionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ArchiveRepository archiveRepository;
    
    @Autowired
    private DiseaseRepository diseaseRepository;
    
    @Autowired
    private MedicationRepository medicationRepository;
    
    // 1. 搜索用户（通过手机号）
    @GetMapping("/searchUser")
    public SmsResponse searchUser(@RequestParam String phone) {
        try {
            User user = userRepository.findByPhone(phone);
            if (user == null) {
                return new SmsResponse(404, "用户不存在", null);
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("phone", user.getPhone());
            data.put("nickname", user.getNickname());
            data.put("realName", user.getRealName());
            
            return new SmsResponse(200, "找到用户", new Gson().toJson(data));
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "搜索失败: " + e.getMessage(), null);
        }
    }
    
    // 2. 发送家属请求
    @PostMapping("/request")
    public SmsResponse sendFamilyRequest(@RequestBody FamilyRequest request) {
        try {
            // 验证参数
            if (request.getUserId() == null || request.getFamilyPhone() == null || 
                request.getRelationship() == null || request.getPermissions() == null) {
                return new SmsResponse(400, "参数不完整", null);
            }
            
            // 查找家属用户
            User familyUser = userRepository.findByPhone(request.getFamilyPhone());
            if (familyUser == null) {
                return new SmsResponse(404, "家属用户不存在", null);
            }
            
            // 检查是否是自己
            if (request.getUserId().equals(familyUser.getId())) {
                return new SmsResponse(400, "不能添加自己为家属", null);
            }
            
            // 检查是否已有关系
            Optional<FamilyRelationship> existing = familyRepository
                .findByUserIdAndFamilyUserId(request.getUserId(), familyUser.getId());
            if (existing.isPresent()) {
                FamilyRelationship rel = existing.get();
                String statusMsg = "";
                switch (rel.getStatus()) {
                    case 0: statusMsg = "等待对方确认"; break;
                    case 1: statusMsg = "已是家属关系"; break;
                    case 2: statusMsg = "对方已拒绝"; break;
                    case 3: statusMsg = "关系已解除"; break;
                }
                return new SmsResponse(400, statusMsg, null);
            }
            
            // 检查对方是否已向自己发送过请求
            Optional<FamilyRelationship> reverseExisting = familyRepository
                .findByUserIdAndFamilyUserId(familyUser.getId(), request.getUserId());
            if (reverseExisting.isPresent()) {
                FamilyRelationship rel = reverseExisting.get();
                if (rel.getStatus() == 0) {
                    return new SmsResponse(400, "对方已向您发送了家属请求，请先处理", null);
                }
            }
            
            // 创建家属关系
            FamilyRelationship relationship = new FamilyRelationship();
            relationship.setUserId(request.getUserId());
            relationship.setFamilyUserId(familyUser.getId());
            relationship.setRelationship(request.getRelationship());
            relationship.setStatus(0); // 待确认
            relationship.setRequestTime(LocalDateTime.now());
            relationship = familyRepository.save(relationship);
            
            // 创建权限记录
            for (String permissionType : request.getPermissions()) {
                DataPermission permission = new DataPermission();
                permission.setRelationshipId(relationship.getId());
                permission.setPermissionType(permissionType);
                permission.setIsGranted(false);
                permissionRepository.save(permission);
            }
            
            return new SmsResponse(200, "家属请求已发送，等待对方确认", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "发送请求失败: " + e.getMessage(), null);
        }
    }
    
    // 3. 获取待确认的家属请求
    @GetMapping("/pendingRequests")
    public List<FamilyRequestResponse> getPendingRequests(@RequestParam Long userId) {
        List<FamilyRelationship> relationships = familyRepository.findByFamilyUserIdAndStatus(userId, 0);
        List<FamilyRequestResponse> responses = new ArrayList<>();
        
        for (FamilyRelationship rel : relationships) {
            User requester = userRepository.findById(rel.getUserId()).orElse(null);
            if (requester != null) {
                FamilyRequestResponse response = new FamilyRequestResponse();
                response.setId(rel.getId());
                response.setRequesterId(requester.getId());
                response.setRequesterName(requester.getRealName() != null ? requester.getRealName() : requester.getNickname());
                response.setRequesterPhone(requester.getPhone());
                response.setRelationship(rel.getRelationship());
                response.setRequestTime(rel.getRequestTime());
                
                // 获取请求的权限类型
                List<DataPermission> permissions = permissionRepository.findByRelationshipId(rel.getId());
                List<String> permissionTypes = new ArrayList<>();
                for (DataPermission perm : permissions) {
                    permissionTypes.add(perm.getPermissionType());
                }
                response.setRequestedPermissions(permissionTypes);
                
                responses.add(response);
            }
        }
        
        return responses;
    }
    
    // 4. 处理家属请求（同意/拒绝）
    @PostMapping("/handleRequest")
    public SmsResponse handleFamilyRequest(@RequestBody HandleRequest request) {
        try {
            Optional<FamilyRelationship> relationshipOpt = familyRepository.findById(request.getRelationshipId());
            if (!relationshipOpt.isPresent()) {
                return new SmsResponse(404, "请求不存在", null);
            }
            
            FamilyRelationship relationship = relationshipOpt.get();
            
            // 验证用户是否有权限处理
            if (!relationship.getFamilyUserId().equals(request.getUserId())) {
                return new SmsResponse(403, "无权处理此请求", null);
            }
            
            if (request.isAgree()) {
                // 同意请求
                relationship.setStatus(1);
                relationship.setConfirmTime(LocalDateTime.now());
                
                // 更新权限
                List<DataPermission> permissions = permissionRepository.findByRelationshipId(relationship.getId());
                for (DataPermission perm : permissions) {
                    perm.setIsGranted(true);
                    permissionRepository.save(perm);
                }
                
                familyRepository.save(relationship);
                
                return new SmsResponse(200, "已同意家属请求", null);
            } else {
                // 拒绝请求
                relationship.setStatus(2);
                relationship.setConfirmTime(LocalDateTime.now());
                familyRepository.save(relationship);
                
                return new SmsResponse(200, "已拒绝家属请求", null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "处理失败: " + e.getMessage(), null);
        }
    }
    
    // 5. 获取已同意的家属列表
    @GetMapping("/approvedFamily")
    public List<FamilyMemberResponse> getApprovedFamily(@RequestParam Long userId) {
        List<FamilyRelationship> relationships = familyRepository.findApprovedRelationships(userId);
        List<FamilyMemberResponse> responses = new ArrayList<>();
        
        for (FamilyRelationship rel : relationships) {
            FamilyMemberResponse response = new FamilyMemberResponse();
            
            // 判断用户是发起方还是接收方
            if (rel.getUserId().equals(userId)) {
                // 用户是发起方，家属是接收方
                User familyUser = userRepository.findById(rel.getFamilyUserId()).orElse(null);
                if (familyUser != null) {
                    response.setRelationshipId(rel.getId());
                    response.setFamilyId(familyUser.getId());
                    response.setFamilyName(familyUser.getRealName() != null ? familyUser.getRealName() : familyUser.getNickname());
                    response.setFamilyPhone(familyUser.getPhone());
                    response.setRelationship(rel.getRelationship());
                    response.setIsRequester(true);
                }
            } else {
                // 用户是接收方，家属是发起方
                User familyUser = userRepository.findById(rel.getUserId()).orElse(null);
                if (familyUser != null) {
                    response.setRelationshipId(rel.getId());
                    response.setFamilyId(familyUser.getId());
                    response.setFamilyName(familyUser.getRealName() != null ? familyUser.getRealName() : familyUser.getNickname());
                    response.setFamilyPhone(familyUser.getPhone());
                    response.setRelationship(rel.getRelationship());
                    response.setIsRequester(false);
                }
            }
            
            // 获取权限信息
            if (response.getFamilyId() != null) {
                List<DataPermission> permissions = permissionRepository.findByRelationshipId(rel.getId());
                Map<String, Boolean> permissionMap = new HashMap<>();
                for (DataPermission perm : permissions) {
                    permissionMap.put(perm.getPermissionType(), perm.getIsGranted());
                }
                response.setPermissions(permissionMap);
            }
            
            responses.add(response);
        }
        
        return responses;
    }
    
    // 6. 获取家属的健康数据（根据权限）
    @GetMapping("/familyData")
    public FamilyDataResponse getFamilyData(@RequestParam Long userId, @RequestParam Long familyId) {
        try {
            // 查找家属关系（双向查找）
            Optional<FamilyRelationship> relationship = familyRepository.findByUserIdAndFamilyUserId(userId, familyId);
            if (!relationship.isPresent()) {
                // 尝试反向查找（用户可能是接收方）
                relationship = familyRepository.findByUserIdAndFamilyUserId(familyId, userId);
            }
            
            if (!relationship.isPresent() || relationship.get().getStatus() != 1) {
                return new FamilyDataResponse(403, "无权限访问", null, null, null);
            }
            
            FamilyDataResponse response = new FamilyDataResponse();
            Long relationshipId = relationship.get().getId();
            
            // 获取档案数据
            DataPermission archivePerm = permissionRepository.findByRelationshipIdAndPermissionType(relationshipId, "ARCHIVE");
            if (archivePerm != null && archivePerm.getIsGranted()) {
                Optional<Archive> archive = archiveRepository.findByUserId(familyId);
                archive.ifPresent(response::setArchive);
            }
            
            // 获取疾病数据
            DataPermission diseasePerm = permissionRepository.findByRelationshipIdAndPermissionType(relationshipId, "DISEASE");
            if (diseasePerm != null && diseasePerm.getIsGranted()) {
                List<Disease> diseases = diseaseRepository.findByUserId(familyId);
                response.setDiseases(diseases);
            }
            
            // 获取用药数据
            DataPermission medicationPerm = permissionRepository.findByRelationshipIdAndPermissionType(relationshipId, "MEDICATION");
            if (medicationPerm != null && medicationPerm.getIsGranted()) {
                List<Medication> medications = medicationRepository.findByUserId(familyId);
                response.setMedications(medications);
            }
            
            response.setCode(200);
            response.setMsg("获取成功");
            return response;
            
        } catch (Exception e) {
            e.printStackTrace();
            return new FamilyDataResponse(500, "获取失败: " + e.getMessage(), null, null, null);
        }
    }
    
    // 7. 解除家属关系（关键修复）
    @DeleteMapping("/removeFamily")
    public SmsResponse removeFamily(@RequestParam Long userId, @RequestParam Long familyId) {
        try {
            System.out.println("移除家属请求: userId=" + userId + ", familyId=" + familyId);
            
            // 查找家属关系（双向查找）
            Optional<FamilyRelationship> relationship = familyRepository.findByUserIdAndFamilyUserId(userId, familyId);
            if (!relationship.isPresent()) {
                // 尝试反向查找（用户可能是接收方）
                relationship = familyRepository.findByUserIdAndFamilyUserId(familyId, userId);
                if (!relationship.isPresent()) {
                    return new SmsResponse(404, "家属关系不存在", null);
                }
            }
            
            FamilyRelationship rel = relationship.get();
            System.out.println("找到家属关系: id=" + rel.getId() + 
                             ", userId=" + rel.getUserId() + 
                             ", familyUserId=" + rel.getFamilyUserId() + 
                             ", status=" + rel.getStatus());
            
            // 验证用户是否有权限移除
            boolean canRemove = rel.getUserId().equals(userId) || rel.getFamilyUserId().equals(userId);
            if (!canRemove) {
                return new SmsResponse(403, "无权解除此家属关系", null);
            }
            
            // 更新关系状态为已解除
            rel.setStatus(3);
            rel.setConfirmTime(LocalDateTime.now());
            familyRepository.save(rel);
            
            // 记录日志
            System.out.println("家属关系已解除: 关系ID=" + rel.getId());
            
            return new SmsResponse(200, "已成功解除家属关系", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new SmsResponse(500, "解除失败: " + e.getMessage(), null);
        }
    }
    
    // 请求参数类
    @lombok.Data
    public static class FamilyRequest {
        private Long userId;
        private String familyPhone;
        private String relationship;
        private List<String> permissions; // ARCHIVE, DISEASE, MEDICATION
    }
    
    @lombok.Data
    public static class HandleRequest {
        private Long userId;
        private Long relationshipId;
        private boolean agree;
    }
    
    // 响应类
    @lombok.Data
    public static class FamilyRequestResponse {
        private Long id;
        private Long requesterId;
        private String requesterName;
        private String requesterPhone;
        private String relationship;
        private LocalDateTime requestTime;
        private List<String> requestedPermissions;
    }
    
    @lombok.Data
    public static class FamilyMemberResponse {
        private Long relationshipId;
        private Long familyId;
        private String familyName;
        private String familyPhone;
        private String relationship;
        private Boolean isRequester;
        private Map<String, Boolean> permissions;
    }
    
    @lombok.Data
    public static class FamilyDataResponse {
        private int code;
        private String msg;
        private Archive archive;
        private List<Disease> diseases;
        private List<Medication> medications;
        
        public FamilyDataResponse() {}
        
        public FamilyDataResponse(int code, String msg, Archive archive, List<Disease> diseases, List<Medication> medications) {
            this.code = code;
            this.msg = msg;
            this.archive = archive;
            this.diseases = diseases;
            this.medications = medications;
        }
    }
}