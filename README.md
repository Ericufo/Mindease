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
    `phone` VARCHAR(20) DEFAULT NULL COLLATE utf8_bin COMMENT '手机号',
    `avatar` VARCHAR(255) DEFAULT NULL COLLATE utf8_bin COMMENT '头像',
    `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色: USER, COUNSELOR, ADMIN, ROOT',
    `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `update_user` bigint DEFAULT NULL COMMENT '修改人',
    
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='系统用户表';

INSERT INTO `sys_user` (`id`, `username`, `password`, `role`,  `status`, `create_time`)
VALUES ('1','admin', '123456', 'ROOT', 1, NOW ());
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