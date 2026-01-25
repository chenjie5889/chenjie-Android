package com.example.chronic_backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DataPermissionRepository extends JpaRepository<DataPermission, Long> {
    
    List<DataPermission> findByRelationshipId(Long relationshipId);
    
    DataPermission findByRelationshipIdAndPermissionType(Long relationshipId, String permissionType);
}