package com.example.chronic_backend;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "data_permissions")
public class DataPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "relationship_id", nullable = false)
    private Long relationshipId;
    
    @Column(name = "permission_type")
    private String permissionType;
    
    @Column(name = "is_granted")
    private Boolean isGranted = false;
}