package com.example.chronic_backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyRelationshipRepository extends JpaRepository<FamilyRelationship, Long> {
    
    // 查找用户的所有家属关系
    @Query("SELECT f FROM FamilyRelationship f WHERE f.userId = :userId OR f.familyUserId = :userId")
    List<FamilyRelationship> findAllByUserId(Long userId);
    
    // 查找用户发起的家属关系
    List<FamilyRelationship> findByUserId(Long userId);
    
    // 查找用户收到的家属请求
    List<FamilyRelationship> findByFamilyUserId(Long userId);
    
    // 查找特定的家属关系
    Optional<FamilyRelationship> findByUserIdAndFamilyUserId(Long userId, Long familyUserId);
    
    // 查找待确认的家属请求
    List<FamilyRelationship> findByFamilyUserIdAndStatus(Long familyUserId, Integer status);
    
    // 查找已同意的家属关系
    @Query("SELECT f FROM FamilyRelationship f WHERE (f.userId = :userId OR f.familyUserId = :userId) AND f.status = 1")
    List<FamilyRelationship> findApprovedRelationships(Long userId);
    
    // 新增：查找特定状态的关系
    @Query("SELECT f FROM FamilyRelationship f WHERE ((f.userId = :userId AND f.familyUserId = :familyUserId) OR (f.userId = :familyUserId AND f.familyUserId = :userId)) AND f.status = :status")
    Optional<FamilyRelationship> findRelationshipBetweenUsers(Long userId, Long familyUserId, Integer status);
}