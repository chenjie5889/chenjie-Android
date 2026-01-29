-- 1.sql
-- 1. 如果存在旧数据库则删除，然后重新创建
DROP DATABASE IF EXISTS chronic_db;
CREATE DATABASE chronic_db DEFAULT CHARACTER SET utf8mb4;

USE chronic_db;

-- 2. 创建用户表（包含身份绑定核心字段）
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `phone` VARCHAR(11) NOT NULL COMMENT '手机号',
  `password` VARCHAR(100) NOT NULL COMMENT '密码',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `id_card` VARCHAR(18) DEFAULT NULL COMMENT '身份证号',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 创建档案表（移除real_name字段，关联users表）
DROP TABLE IF EXISTS `health_archives`;
CREATE TABLE `health_archives` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL UNIQUE,
  `gender` VARCHAR(10),
  `birthday` DATE,
  `height` DOUBLE,
  `weight` DOUBLE,
  `medical_history` TEXT,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES users(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 删除旧表并重新创建
DROP TABLE IF EXISTS `diseases`;
CREATE TABLE `diseases` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `disease_name` VARCHAR(100) NOT NULL,
  `disease_type` VARCHAR(50),
  `diagnosis_info` TEXT,
  `symptoms` TEXT,
  `diagnosis_date` VARCHAR(20),  -- 改为VARCHAR类型
  `hospital` VARCHAR(200),
  `doctor` VARCHAR(50),
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `medications`;
CREATE TABLE `medications` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `disease_id` BIGINT,
  `medicine_name` VARCHAR(100) NOT NULL,
  `generic_name` VARCHAR(100),
  `dosage` VARCHAR(50),
  `frequency` VARCHAR(50),
  `take_time_morning` VARCHAR(10),  -- 改为VARCHAR类型
  `take_time_noon` VARCHAR(10),     -- 改为VARCHAR类型
  `take_time_evening` VARCHAR(10),  -- 改为VARCHAR类型
  `take_time_night` VARCHAR(10),    -- 改为VARCHAR类型
  `instructions` VARCHAR(200),
  `precautions` TEXT,
  `side_effects` TEXT,
  `contraindications` TEXT,
  `mechanism` TEXT,
  `storage` VARCHAR(200),
  `start_date` VARCHAR(20),         -- 改为VARCHAR类型
  `end_date` VARCHAR(20),           -- 改为VARCHAR类型
  `is_active` BOOLEAN DEFAULT TRUE,
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_disease_id (disease_id),
  FOREIGN KEY (disease_id) REFERENCES diseases(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `medication_logs`;
CREATE TABLE `medication_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `medicine_name` VARCHAR(100) COMMENT '药品名称',
  `take_time` VARCHAR(10) COMMENT '服药时间（HH:mm）',
  `log_date` DATE NOT NULL COMMENT '打卡日期',
  `status` INT COMMENT '0:漏服(红), 1:按时(蓝)',
  `created_at` DATE DEFAULT (CURRENT_DATE) COMMENT '记录创建日期',
  UNIQUE KEY `uk_user_date_medicine` (`user_id`, `log_date`, `medicine_name`),
  FOREIGN KEY (`user_id`) REFERENCES users(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 家属关系表
CREATE TABLE IF NOT EXISTS family_relationships (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '发起请求的用户ID',
    family_user_id BIGINT NOT NULL COMMENT '家属用户ID',
    relationship VARCHAR(50) COMMENT '关系类型（父子、夫妻等）',
    status INT DEFAULT 0 COMMENT '状态：0-待确认，1-已同意，2-已拒绝，3-已解除',
    request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',
    confirm_time TIMESTAMP NULL COMMENT '确认时间',
    UNIQUE KEY uk_user_family (user_id, family_user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (family_user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_status (user_id, status),
    INDEX idx_family_status (family_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 数据共享权限表
CREATE TABLE IF NOT EXISTS data_permissions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    relationship_id BIGINT NOT NULL COMMENT '家属关系ID',
    permission_type VARCHAR(20) COMMENT '权限类型：ARCHIVE-档案，DISEASE-疾病，MEDICATION-用药',
    is_granted BOOLEAN DEFAULT FALSE COMMENT '是否授予权限',
    FOREIGN KEY (relationship_id) REFERENCES family_relationships(id) ON DELETE CASCADE,
    UNIQUE KEY uk_relationship_type (relationship_id, permission_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 插入测试数据
INSERT INTO diseases (user_id, disease_name, disease_type, diagnosis_info, symptoms, diagnosis_date, hospital, doctor) VALUES
(1, '高血压', '原发性', '收缩压持续高于140mmHg，舒张压持续高于90mmHg', '头痛、头晕、心悸', '2023-01-15', '人民医院', '张医生'),
(1, '2型糖尿病', NULL, '空腹血糖7.8mmol/L，餐后血糖11.2mmol/L', '多饮、多尿、体重下降', '2023-03-20', '中心医院', '李医生');

INSERT INTO medications (user_id, disease_id, medicine_name, generic_name, dosage, frequency, take_time_morning, take_time_noon, take_time_evening, take_time_night, instructions, precautions, side_effects, contraindications, mechanism, storage, start_date, end_date, is_active) VALUES
(1, 1, '硝苯地平', '硝苯地平控释片', '10mg', '每日一次', '08:00', NULL, NULL, NULL, '空腹服用', '监测血压，避免突然停药', '头痛、面部潮红', '孕妇禁用', '钙通道阻滞剂', '阴凉干燥处保存', '2023-01-20', NULL, TRUE),
(1, 1, '厄贝沙坦', NULL, '150mg', '每日一次', NULL, NULL, '20:00', NULL, '饭后服用', '监测肾功能', '头晕、高血钾', '双侧肾动脉狭窄禁用', '血管紧张素II受体拮抗剂', '室温保存', '2023-02-01', NULL, TRUE),
(1, 2, '二甲双胍', '盐酸二甲双胍片', '500mg', '每日三次', '08:00', '12:00', '18:00', NULL, '饭后服用', '注意乳酸酸中毒风险', '胃肠道不适', '肾功能不全禁用', '增加胰岛素敏感性', '阴凉处保存', '2023-03-25', NULL, TRUE);-- 5. 插入测试数据
INSERT INTO `users` (phone, password, nickname, real_name, id_card) VALUES
('13584715889', '123456', '测试用户', '张三', '110101199001011234'),
('13900139000', '123456', '用户二', '李四', '110101199002021235');

-- 插入测试档案数据
INSERT INTO `health_archives` (user_id, gender, birthday, height, weight, medical_history) VALUES
(1, '男', '1990-01-01', 175.5, 70.0, '高血压病史5年，需每日服药'),
(2, '女', '1990-02-02', 165.0, 55.0, '糖尿病病史3年，需控制饮食');

