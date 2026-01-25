package com.example.chronic_backend;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "family_relationships")
public class FamilyRelationship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "family_user_id", nullable = false)
    private Long familyUserId;
    
    private String relationship;
    
    private Integer status = 0; // 0-待确认，1-已同意，2-已拒绝，3-已解除
    
    @Column(name = "request_time")
    private LocalDateTime requestTime;
    
    @Column(name = "confirm_time")
    private LocalDateTime confirmTime;
    
    // 关联的用户信息（不映射到数据库）
    @Transient
    private String familyUserName;
    
    @Transient
    private String familyUserPhone;
}