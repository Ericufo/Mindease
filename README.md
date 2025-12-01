# MindEase - 心理健康管理系统

## 开发阶段快速配置

### 1. 数据库建表语句

```sql
CREATE DATABASE IF NOT EXISTS `mindease`;
USE `mindease`;

DROP TABLE IF EXISTS `sys_user`;

CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(64) COLLATE utf8_bin NOT NULL COMMENT '用户名/账号',
    `password` VARCHAR(128) COLLATE utf8_bin NOT NULL COMMENT '密码(加密存储)',
    `nickname` VARCHAR(64) DEFAULT NULL COLLATE utf8_bin COMMENT '昵称',
    `phone` VARCHAR(20) DEFAULT NULL COLLATE utf8_bin COMMENT '手机号',
    `avatar` VARCHAR(255) DEFAULT NULL COLLATE utf8_bin COMMENT '头像',
    `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色: USER, COUNSELOR, ADMIN, ROOT',
    `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用，2:待审核',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='系统用户表';

-- 初始化超级管理员账号（用户名：admin，密码：123456）
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `role`,  `status`, `create_time`)
VALUES ('1','admin', 'e10adc3949ba59abbe56e057f20f883e', '超级管理员', 'ADMIN', 1, NOW ());

-- 咨询师资质审核记录表 (新增)
DROP TABLE IF EXISTS `counselor_audit_record`;
CREATE TABLE `counselor_audit_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '申请人ID (关联sys_user)',
    `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名',
    `qualification_url` VARCHAR(512) NOT NULL COMMENT '资质证书图片URL',
    `id_card_url` VARCHAR(512) DEFAULT NULL COMMENT '身份证/执照URL(可选)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING(待审), APPROVED(通过), REJECTED(驳回)',
    `auditor_id` BIGINT DEFAULT NULL COMMENT '审核管理员ID (关联sys_user)',
    `audit_time` DATETIME DEFAULT NULL COMMENT '审核处理时间',
    `audit_remark` VARCHAR(255) DEFAULT NULL COMMENT '审核备注/驳回原因',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交申请时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询师资质审核记录表';

-- 咨询师公开资料表 (审核通过后才写入)
DROP TABLE IF EXISTS `counselor_profile`;
CREATE TABLE `counselor_profile` (
    `user_id` BIGINT NOT NULL COMMENT '关联sys_user.id',
    `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名(同步自审核表)',
    `title` VARCHAR(64) DEFAULT NULL COMMENT '头衔(如:资深咨询师)',
    `experience_years` INT DEFAULT NULL COMMENT '从业年限',
    `specialty` JSON DEFAULT NULL COMMENT '擅长领域JSON数组',
    `bio` TEXT DEFAULT NULL COMMENT '个人简介',
    `qualification_url` VARCHAR(512) DEFAULT NULL COMMENT '当前展示的证书URL',
    `location` VARCHAR(128) DEFAULT NULL COMMENT '所在地区',
    `price_per_hour` DECIMAL(10,2) DEFAULT 0.00 COMMENT '咨询价格/小时',
    `rating` DECIMAL(3,1) DEFAULT 5.0 COMMENT '综合评分',
    `review_count` INT DEFAULT 0 COMMENT '评价数量',
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询师公开资料表';
```

### 2. application-dev.yml 配置

在 `src/main/resources/application-dev.yml` 文件中配置数据库连接：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    host: localhost
    port: 3306
    database: mindease
    username: root
    password: 你的数据库密码
```