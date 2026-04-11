-- 为已有表补充注释、调整字段顺序，并移除 session_uuid
-- 使用方式：mysql -u root -p seetalk < backend/src/main/resources/db/fix-schema-comments-and-order.sql

ALTER TABLE chat_session COMMENT '聊天会话表';

-- 临时固定用户 ID：后续接入登录后由真实用户 ID 替代
SET @default_user_id := 329767336584859648;

-- 若存在 session_uuid 列则删除（旧版 schema）
SET @drop_uuid := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_session'
      AND COLUMN_NAME = 'session_uuid'
);
SET @sql_drop_uuid := IF(
    @drop_uuid > 0,
    'ALTER TABLE chat_session DROP COLUMN session_uuid',
    'SELECT 1'
);
PREPARE stmt FROM @sql_drop_uuid;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 去掉自增，主键由应用侧 Snowflake 生成
SET @has_auto_increment := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_session'
      AND COLUMN_NAME = 'id'
      AND EXTRA LIKE '%auto_increment%'
);
SET @sql_drop_ai := IF(
    @has_auto_increment > 0,
    'ALTER TABLE chat_session MODIFY COLUMN id BIGINT NOT NULL COMMENT ''主键 ID（Snowflake 分布式 ID）''',
    'SELECT 1'
);
PREPARE stmt FROM @sql_drop_ai;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 增加 user_id，并将历史会话归到当前临时固定用户下
SET @has_user_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_session'
      AND COLUMN_NAME = 'user_id'
);
SET @sql_add_user_id := IF(
    @has_user_id = 0,
    'ALTER TABLE chat_session ADD COLUMN user_id BIGINT NULL COMMENT ''所属用户 ID（临时固定用户，后续接入登录后替换）'' AFTER id',
    'SELECT 1'
);
PREPARE stmt FROM @sql_add_user_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE chat_session
SET user_id = @default_user_id
WHERE user_id IS NULL OR user_id = 0;

ALTER TABLE chat_session
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键 ID（Snowflake 分布式 ID）' FIRST,
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户 ID（临时固定用户，后续接入登录后替换）' AFTER id,
    MODIFY COLUMN title VARCHAR(128) COMMENT '会话标题（取自首条用户消息）' AFTER user_id,
    MODIFY COLUMN last_active_time DATETIME(6) NOT NULL COMMENT '最后活跃时间' AFTER title,
    MODIFY COLUMN message_count INT NOT NULL COMMENT '消息条数' AFTER last_active_time,
    MODIFY COLUMN create_time DATETIME(6) NOT NULL COMMENT '创建时间' AFTER message_count,
    MODIFY COLUMN update_time DATETIME(6) NOT NULL COMMENT '更新时间' AFTER create_time,
    MODIFY COLUMN is_deleted BIT(1) NOT NULL COMMENT '是否已删除（0=否，1=是）' AFTER update_time;

SET @has_user_id_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_session'
      AND INDEX_NAME = 'idx_chat_session_user_active'
);
SET @sql_add_user_id_index := IF(
    @has_user_id_index = 0,
    'CREATE INDEX idx_chat_session_user_active ON chat_session (user_id, last_active_time)',
    'SELECT 1'
);
PREPARE stmt FROM @sql_add_user_id_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE chat_message COMMENT '聊天消息表';

SET @msg_has_auto_increment := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_message'
      AND COLUMN_NAME = 'id'
      AND EXTRA LIKE '%auto_increment%'
);
SET @sql_msg_drop_ai := IF(
    @msg_has_auto_increment > 0,
    'ALTER TABLE chat_message MODIFY COLUMN id BIGINT NOT NULL COMMENT ''主键 ID（Snowflake 分布式 ID）''',
    'SELECT 1'
);
PREPARE stmt FROM @sql_msg_drop_ai;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE chat_message
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键 ID（Snowflake 分布式 ID）' FIRST,
    MODIFY COLUMN session_id BIGINT NOT NULL COMMENT '所属会话 ID' AFTER id,
    MODIFY COLUMN role VARCHAR(16) NOT NULL COMMENT '消息角色（user / assistant）' AFTER session_id,
    MODIFY COLUMN content TEXT NOT NULL COMMENT '消息内容' AFTER role,
    MODIFY COLUMN used_vision BIT(1) NOT NULL COMMENT '是否使用视觉理解' AFTER content,
    MODIFY COLUMN create_time DATETIME(6) NOT NULL COMMENT '创建时间' AFTER used_vision,
    MODIFY COLUMN update_time DATETIME(6) NOT NULL COMMENT '更新时间' AFTER create_time,
    MODIFY COLUMN is_deleted BIT(1) NOT NULL COMMENT '是否已删除（0=否，1=是）' AFTER update_time;
