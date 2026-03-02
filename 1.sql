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
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  -- 修改唯一约束：同一用户同一天同一药品同一时间只能记录一次
  UNIQUE KEY `uk_user_date_medicine_time` (`user_id`, `log_date`, `medicine_name`, `take_time`),
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

-- 6. 创建管理员表
DROP TABLE IF EXISTS `admins`;
CREATE TABLE `admins` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL COMMENT '管理员用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '管理员密码',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `role` VARCHAR(20) DEFAULT 'admin' COMMENT '角色',
  `status` INT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login` TIMESTAMP NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 自定义指标表
DROP TABLE IF EXISTS `custom_metrics`;
CREATE TABLE `custom_metrics` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `metric_name` VARCHAR(50) NOT NULL COMMENT '指标名称，如：血压、血糖',
  `unit` VARCHAR(20) COMMENT '单位，如：mmHg、mmol/L',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 自定义指标记录表
DROP TABLE IF EXISTS `custom_metric_records`;
CREATE TABLE `custom_metric_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `metric_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `record_value` VARCHAR(50) NOT NULL COMMENT '记录值，如：120/80',
  `record_date` DATE NOT NULL COMMENT '记录日期',
  `note` VARCHAR(200) COMMENT '备注，如：早餐前测量',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_metric_id (metric_id),
  INDEX idx_user_date (user_id, record_date),
  FOREIGN KEY (metric_id) REFERENCES custom_metrics(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `users` (phone, password, nickname, real_name, id_card) VALUES
-- 原有2个用户
('13584715889', '123456', '测试用户', '张三', '110101199001011234'),
('13900139000', '123456', '用户二', '李四', '110101199002021235'),
-- 新增8个用户
('13800138001', '123456', '王五', '王五', '110101199003031236'),
('13800138002', '123456', '赵六', '赵六', '110101199004041237'),
('13800138003', '123456', '孙七', '孙七', '110101199005051238'),
('13800138004', '123456', '周八', '周八', '110101199006061239'),
('13800138005', '123456', '吴九', '吴九', '110101199007071240'),
('13800138006', '123456', '郑十', '郑十', '110101199008081241'),
('13800138007', '123456', '钱一', '钱一', '110101199009091242'),
('13800138008', '123456', '冯二', '冯二', '110101199010101243'),
('13800138009', '123456', '陈三', '陈三', '110101199011111244'),
('13800138010', '123456', '卫四', '卫四', '110101199012121245');

-- 插入更多健康档案数据
INSERT INTO `health_archives` (user_id, gender, birthday, height, weight, medical_history) VALUES
-- 原有档案
(1, '男', '1960-01-01', 175.5, 70.0, '高血压病史5年，需每日服药'),
(2, '女', '1990-02-02', 165.0, 55.0, '糖尿病病史3年，需控制饮食'),
-- 新增档案
(3, '男', '1992-03-03', 180.0, 75.0, '哮喘病史2年，季节性发作'),
(4, '女', '1988-04-04', 160.0, 52.0, '甲状腺功能减退，需长期服药'),
(5, '男', '1995-05-05', 178.0, 80.0, '高血脂病史1年，控制饮食中'),
(6, '女', '1993-06-06', 162.0, 58.0, '关节炎病史4年，阴雨天加重'),
(7, '男', '1991-07-07', 176.0, 72.0, '胃溃疡病史3年，需定期复查'),
(8, '女', '1989-08-08', 158.0, 50.0, '偏头痛病史6年，压力大时发作'),
(9, '男', '2004-09-09', 182.0, 85.0, '痛风病史2年，需控制嘌呤摄入'),
(10, '男', '1992-10-10', 168.0, 60.0, '冠心病病史1年，术后恢复中'),
(11, '男', '1970-11-11', 175.0, 68.0, '慢性支气管炎病史3年'),
(12, '女', '1987-12-12', 155.0, 48.0, '骨质疏松病史5年，需补钙');

-- 插入更多疾病数据
INSERT INTO `diseases` (user_id, disease_name, disease_type, diagnosis_info, symptoms, diagnosis_date, hospital, doctor) VALUES
-- 原有疾病数据
(1, '高血压', '原发性', '收缩压持续高于140mmHg，舒张压持续高于90mmHg', '头痛、头晕、心悸', '2023-01-15', '人民医院', '张医生'),
(1, '2型糖尿病', NULL, '空腹血糖7.8mmol/L，餐后血糖11.2mmol/L', '多饮、多尿、体重下降', '2023-03-20', '中心医院', '李医生'),
-- 新增疾病数据
(3, '支气管哮喘', '过敏性', '支气管收缩，呼吸困难', '喘息、胸闷、咳嗽', '2023-02-10', '呼吸科医院', '王医生'),
(4, '甲状腺功能减退', '原发性', 'TSH升高，T3、T4降低', '乏力、怕冷、体重增加', '2023-04-05', '内分泌医院', '刘医生'),
(5, '高脂血症', '饮食性', '总胆固醇6.8mmol/L，甘油三酯2.5mmol/L', '无明显症状，体检发现', '2023-05-20', '体检中心', '陈医生'),
(6, '类风湿关节炎', '免疫性', '关节肿胀，晨僵超过1小时', '关节疼痛、肿胀、活动受限', '2023-06-15', '风湿科医院', '杨医生'),
(7, '胃溃疡', '慢性', '胃镜检查发现直径0.8cm溃疡', '上腹痛、反酸、烧心', '2023-07-10', '消化科医院', '赵医生'),
(8, '偏头痛', '神经性', '反复发作性头痛', '单侧头痛、恶心、畏光', '2023-08-25', '神经内科医院', '孙医生'),
(9, '痛风', '代谢性', '血尿酸560μmol/L，关节红肿', '关节剧痛、红肿、发热', '2023-09-30', '风湿免疫科', '周医生'),
(10, '冠心病', '缺血性', '冠状动脉狭窄70%', '胸痛、胸闷、气短', '2023-10-12', '心内科医院', '吴医生'),
(11, '慢性支气管炎', '感染性', '每年咳嗽咳痰超过3个月', '咳嗽、咳痰、喘息', '2023-11-08', '呼吸科医院', '郑医生'),
(12, '骨质疏松', '退行性', '骨密度T值-2.8', '腰背痛、身高变矮', '2023-12-05', '骨科医院', '冯医生'),
(2, '高血压', '继发性', '肾性高血压', '头晕、耳鸣', '2023-04-18', '肾病医院', '钱医生'),
(3, '过敏性鼻炎', '季节性', '花粉过敏', '打喷嚏、流清涕、鼻塞', '2023-08-12', '耳鼻喉科医院', '宋医生');

-- 插入更多用药数据
INSERT INTO `medications` (user_id, disease_id, medicine_name, generic_name, dosage, frequency, take_time_morning, take_time_noon, take_time_evening, take_time_night, instructions, precautions, side_effects, contraindications, mechanism, storage, start_date, end_date, is_active) VALUES
-- 原有用药数据
(1, 1, '硝苯地平', '硝苯地平控释片', '10mg', '每日一次', '08:00', NULL, NULL, NULL, '空腹服用', '监测血压，避免突然停药', '头痛、面部潮红', '孕妇禁用', '钙通道阻滞剂', '阴凉干燥处保存', '2023-01-20', NULL, TRUE),
(1, 1, '厄贝沙坦', NULL, '150mg', '每日一次', NULL, NULL, '20:00', NULL, '饭后服用', '监测肾功能', '头晕、高血钾', '双侧肾动脉狭窄禁用', '血管紧张素II受体拮抗剂', '室温保存', '2023-02-01', NULL, TRUE),
(1, 2, '二甲双胍', '盐酸二甲双胍片', '500mg', '每日三次', '08:00', '12:00', '18:00', NULL, '饭后服用', '注意乳酸酸中毒风险', '胃肠道不适', '肾功能不全禁用', '增加胰岛素敏感性', '阴凉处保存', '2023-03-25', NULL, TRUE),
-- 新增用药数据
(3, 3, '沙丁胺醇', '硫酸沙丁胺醇气雾剂', '100μg', '按需使用', NULL, NULL, NULL, NULL, '发作时使用', '避免过量使用', '心悸、手抖', '心动过速禁用', 'β2受体激动剂', '阴凉处保存', '2023-02-15', NULL, TRUE),
(4, 4, '左甲状腺素', '左甲状腺素钠片', '50μg', '每日一次', '07:00', NULL, NULL, NULL, '空腹服用', '定期复查甲状腺功能', '心悸、多汗', '甲亢禁用', '补充甲状腺激素', '阴凉干燥处保存', '2023-04-10', NULL, TRUE),
(5, 5, '阿托伐他汀', '阿托伐他汀钙片', '20mg', '每日一次', NULL, NULL, '20:00', NULL, '睡前服用', '监测肝功能', '肌肉酸痛、肝功能异常', '活动性肝病禁用', '抑制胆固醇合成', '室温保存', '2023-05-25', NULL, TRUE),
(6, 6, '甲氨蝶呤', '甲氨蝶呤片', '7.5mg', '每周一次', NULL, NULL, NULL, '21:00', '每周固定时间服用', '定期复查血常规', '恶心、脱发、骨髓抑制', '孕妇禁用', '免疫抑制剂', '阴凉处保存', '2023-06-20', NULL, TRUE),
(7, 7, '奥美拉唑', '奥美拉唑肠溶胶囊', '20mg', '每日一次', '07:30', NULL, NULL, NULL, '早餐前服用', '至少服用4周', '头痛、腹泻', '严重肝肾功能不全禁用', '质子泵抑制剂', '阴凉干燥处保存', '2023-07-15', '2023-09-15', TRUE),
(8, 8, '佐米曲普坦', '佐米曲普坦片', '2.5mg', '按需使用', NULL, NULL, NULL, NULL, '头痛发作时服用', '24小时内不超过10mg', '乏力、头晕', '缺血性心脏病禁用', '5-HT1受体激动剂', '室温保存', '2023-08-30', NULL, TRUE),
(9, 9, '别嘌醇', '别嘌醇片', '100mg', '每日一次', NULL, NULL, NULL, '22:00', '饭后服用', '多饮水', '皮疹、肝功能损害', '孕妇禁用', '抑制尿酸合成', '阴凉干燥处保存', '2023-10-05', NULL, TRUE),
(10, 10, '阿司匹林', '阿司匹林肠溶片', '100mg', '每日一次', '08:00', NULL, NULL, NULL, '饭后服用', '注意出血倾向', '胃肠道不适、出血', '消化性溃疡禁用', '抗血小板聚集', '阴凉干燥处保存', '2023-10-18', NULL, TRUE),
(11, 11, '氨溴索', '盐酸氨溴索口服液', '30mg', '每日三次', '08:00', '12:00', '20:00', NULL, '饭后服用', '多饮水', '恶心、皮疹', '妊娠早期禁用', '祛痰药', '室温保存', '2023-11-15', '2023-12-15', TRUE),
(12, 12, '阿仑膦酸钠', '阿仑膦酸钠片', '70mg', '每周一次', '07:00', NULL, NULL, NULL, '空腹站立服用，半小时内不躺卧', '多饮水', '食管刺激、肌肉痛', '食管狭窄禁用', '抑制骨吸收', '室温保存', '2023-12-10', NULL, TRUE);


INSERT INTO `medication_logs` (user_id, medicine_name, take_time, log_date, status, created_at) VALUES
-- 用户1 - 硝苯地平（每日早上8:00）
(1, '硝苯地平', '08:00', '2026-02-01', 1, '2026-02-01 08:05:00'),
(1, '硝苯地平', '08:00', '2026-02-02', 1, '2026-02-02 08:10:00'),
(1, '硝苯平地', '08:00', '2026-02-03', 1, '2026-02-03 08:15:00'),
(1, '硝苯地平', '08:00', '2026-02-04', 0, '2026-02-04 23:59:00'), 

-- 用户1 - 厄贝沙坦（每日晚上8:00）
(1, '厄贝沙坦', '20:00', '2026-02-01', 1, '2026-02-01 20:05:00'),
(1, '厄贝沙坦', '20:00', '2026-02-02', 1, '2026-02-02 20:10:00'),
(1, '厄贝沙坦', '20:00', '2026-02-03', 0, '2026-02-03 23:30:00'), -- 漏服
(1, '厄贝沙坦', '20:00', '2026-02-04', 1, '2026-02-04 20:15:00'),

-- 用户1 - 二甲双胍（每日三次）
-- 2月1日
(1, '二甲双胍', '08:00', '2026-02-01', 1, '2026-02-01 08:10:00'),
(1, '二甲双胍', '12:00', '2026-02-01', 1, '2026-02-01 12:15:00'),
(1, '二甲双胍', '18:00', '2026-02-01', 0, '2026-02-01 23:45:00'), -- 漏服
-- 2月2日
(1, '二甲双胍', '08:00', '2026-02-02', 1, '2026-02-02 08:05:00'),
(1, '二甲双胍', '12:00', '2026-02-02', 1, '2026-02-02 12:10:00'),
(1, '二甲双胍', '18:00', '2026-02-02', 1, '2026-02-02 18:20:00'),
-- 2月3日
(1, '二甲双胍', '08:00', '2026-02-03', 1, '2026-02-03 08:15:00'),
(1, '二甲双胍', '12:00', '2026-02-03', 0, '2026-02-03 15:30:00'), -- 漏服
(1, '二甲双胍', '18:00', '2026-02-03', 1, '2026-02-03 18:10:00'),
-- 2月4日
(1, '二甲双胍', '08:00', '2026-02-04', 1, '2026-02-04 08:20:00'),
(1, '二甲双胍', '12:00', '2026-02-04', 1, '2026-02-04 12:05:00'),
(1, '二甲双胍', '18:00', '2026-02-04', 1, '2026-02-04 18:15:00');



-- 插入家属关系数据
INSERT INTO `family_relationships` (user_id, family_user_id, relationship, status, request_time, confirm_time) VALUES
-- 父子关系
(1, 3, '父子', 1, '2023-11-01 10:00:00', '2023-11-01 15:30:00'),
-- 夫妻关系
(2, 4, '夫妻', 1, '2023-11-02 09:00:00', '2023-11-02 14:20:00'),
-- 母子关系
(5, 6, '母子', 1, '2023-11-03 11:00:00', '2023-11-03 16:45:00'),
-- 父女关系
(7, 8, '父女', 1, '2023-11-04 08:30:00', '2023-11-04 12:15:00'),
-- 兄弟姐妹
(9, 10, '兄弟', 1, '2023-11-05 13:00:00', '2023-11-05 18:00:00'),
-- 等待确认的请求
(11, 12, '夫妻', 0, '2023-11-06 10:30:00', NULL);

-- 插入数据共享权限
INSERT INTO `data_permissions` (relationship_id, permission_type, is_granted) VALUES
-- 父子关系权限
(1, 'ARCHIVE', TRUE),
(1, 'DISEASE', TRUE),
(1, 'MEDICATION', TRUE),
-- 夫妻关系权限
(2, 'ARCHIVE', TRUE),
(2, 'DISEASE', TRUE),
(2, 'MEDICATION', FALSE), -- 不共享用药记录
-- 母子关系权限
(3, 'ARCHIVE', TRUE),
(3, 'DISEASE', TRUE),
(3, 'MEDICATION', TRUE),
-- 父女关系权限
(4, 'ARCHIVE', TRUE),
(4, 'DISEASE', FALSE), -- 不共享疾病记录
(4, 'MEDICATION', TRUE),
-- 兄弟关系权限
(5, 'ARCHIVE', TRUE),
(5, 'DISEASE', TRUE),
(5, 'MEDICATION', TRUE);

-- 插入管理员数据（补充）
INSERT INTO `admins` (username, password, real_name, phone, role, status, last_login) VALUES
-- 原有管理员
('admin', 'admin123', '王昱珩', '13800138000', 'admin', 1, '2023-11-20 09:30:00'),
('manager', 'manager123', '王红艳', '13900139000', 'admin', 1, '2023-11-19 14:20:00'),
-- 新增管理员
('supervisor', 'super123', '徐春林', '13700137000', 'admin', 1, '2023-11-18 10:15:00'),
('auditor', 'audit123', '陈凤霞', '13600136000', 'admin', 1, '2023-11-17 16:45:00');