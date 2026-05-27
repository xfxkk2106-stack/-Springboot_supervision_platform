-- 创建数据库
CREATE DATABASE IF NOT EXISTS supervision_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE supervision_platform;

-- 房间表
CREATE TABLE IF NOT EXISTS room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code VARCHAR(7) NOT NULL UNIQUE COMMENT '7位房间号(邀请码)',
    creator_id BIGINT COMMENT '创建者ID(管理员)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1 COMMENT '0=已关闭 1=活跃',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除 0=未删除 1=已删除',
    INDEX idx_room_code (room_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间表';

-- 房间成员表
CREATE TABLE IF NOT EXISTS room_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL COMMENT '房间ID',
    display_name VARCHAR(32) NOT NULL COMMENT '自定义名称',
    is_admin TINYINT DEFAULT 0 COMMENT '是否为管理员 0=否 1=是',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_online TINYINT DEFAULT 0 COMMENT '在线状态 0=离线 1=在线',
    INDEX idx_room_id (room_id),
    FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间成员表';

-- 学习计划表
CREATE TABLE IF NOT EXISTS learning_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL COMMENT '成员ID',
    plan_type ENUM('YEAR','QUARTER','MONTH','WEEK','DAY') NOT NULL COMMENT '计划类型',
    title VARCHAR(128) NOT NULL COMMENT '计划标题',
    target_date DATE COMMENT '目标日期',
    status TINYINT DEFAULT 0 COMMENT '0=进行中 1=已完成',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_member_id (member_id),
    FOREIGN KEY (member_id) REFERENCES room_member(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习计划表';

-- 当日学习小计划表
CREATE TABLE IF NOT EXISTS daily_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL COMMENT '成员ID',
    plan_id BIGINT COMMENT '关联的学习计划(可为空=当天临时计划)',
    subject VARCHAR(64) NOT NULL COMMENT '科目名称',
    task_content TEXT NOT NULL COMMENT '学习小计划内容',
    is_completed TINYINT DEFAULT 0 COMMENT '是否完成 0=未完成 1=已完成',
    completed_at DATETIME COMMENT '完成时间',
    task_date DATE NOT NULL COMMENT '计划日期',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    sort_order INT DEFAULT 0 COMMENT '排序',
    INDEX idx_member_date (member_id, task_date),
    INDEX idx_task_date (task_date),
    FOREIGN KEY (member_id) REFERENCES room_member(id) ON DELETE CASCADE,
    FOREIGN KEY (plan_id) REFERENCES learning_plan(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当日学习小计划表';

-- 学习证据表
CREATE TABLE IF NOT EXISTS task_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '任务ID',
    image_url VARCHAR(512) NOT NULL COMMENT 'MinIO中的图片URL',
    uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 0 COMMENT '0=待审核 1=通过 2=驳回',
    INDEX idx_task_id (task_id),
    FOREIGN KEY (task_id) REFERENCES daily_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习证据表';

-- 证据审核表
CREATE TABLE IF NOT EXISTS evidence_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT NOT NULL COMMENT '证据ID',
    reviewer_id BIGINT NOT NULL COMMENT '审核人(member_id)',
    result TINYINT NOT NULL COMMENT '1=通过 2=驳回',
    comment VARCHAR(256) COMMENT '审核备注',
    reviewed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evidence_id (evidence_id),
    FOREIGN KEY (evidence_id) REFERENCES task_evidence(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewer_id) REFERENCES room_member(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='证据审核表';

-- 明日计划表
CREATE TABLE IF NOT EXISTS tomorrow_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL COMMENT '成员ID',
    task_date DATE NOT NULL COMMENT '明日日期',
    subject VARCHAR(64) NOT NULL COMMENT '科目名称',
    task_content TEXT NOT NULL COMMENT '学习计划内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    sort_order INT DEFAULT 0 COMMENT '排序',
    INDEX idx_member_date (member_id, task_date),
    FOREIGN KEY (member_id) REFERENCES room_member(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='明日计划表';

-- 每日复盘表
CREATE TABLE IF NOT EXISTS daily_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL COMMENT '成员ID',
    review_date DATE NOT NULL COMMENT '复盘日期',
    summary TEXT COMMENT '复盘总结',
    mood_rating INT COMMENT '学习状态评分 1-5',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_member_date (member_id, review_date),
    FOREIGN KEY (member_id) REFERENCES room_member(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日复盘表';
