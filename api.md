9. 咨询师资质审核模块 (Counselor Audit - NEW)
9.1 提交/更新资质审核申请 (User Side) - [新增]
- 名称: 提交资质审核
- 权限: 登录用户 (Role=counselor)
- 方法: POST
- 路径: /counselor/audit/submit
- 逻辑说明:
  1. 插入数据到 counselor_audit_record 表，状态为 PENDING。
  2. 如果是被驳回后重新提交，生成一条新的记录（保留历史驳回记录）。
- 请求 Body (JSON):
{
  "realName": "张伟",
  "qualificationUrl": "https://oss.com/license_2024.jpg",
  "idCardUrl": "https://oss.com/idcard_back.jpg" // 可选
}
- 响应:
- codeJSON
{
  "code": 200,
  "message": "资质提交成功，请等待管理员审核",
  "data": {
    "auditId": 501 // 返回审核记录ID
  }
}
9.2 获取审核状态 (User Side) - [新增]
- 名称: 获取我的审核状态
- 方法: GET
- 路径: /counselor/audit/status
- 响应:
{
  "code": 200,
  "message": "success",
  "data": {
    "latestStatus": "REJECTED", // PENDING, APPROVED, REJECTED
    "auditRemark": "图片模糊，请重新上传", // 驳回原因
    "submitTime": "2023-11-01 10:00:00"
  }
}

---
📦 10. 管理员审核接口 (Admin Audit - Modified)
10.1 获取待审核列表
- 方法: GET
- 路径: /admin/audit/list
- Query 参数: page=1, pageSize=20 (管理员后台通常数据量大，必须分页)
- 响应 (JSON):
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 5,
    "list": [
      {
        "auditId": 501,
        "userId": 2001,
        "username": "doctor_zhang",
        "realName": "张伟",
        "qualificationUrl": "https://oss.com/license.jpg",
        "submitTime": "2023-11-01 10:00:00"
      }
    ]
  }
}
10.2 审核操作 (通过/拒绝) 
- 方法: POST
- 路径: /admin/audit/process
- 请求 Body:
{
  "auditId": 501,
  "action": "PASS",   // 或 "REJECT""remark": "您的资质已确认无误" // 驳回时必填，通过时可选
}
- 后端详细逻辑流程:
  1. 校验权限: 确认当前操作者是管理员。
  2. 获取数据: 根据 auditId 查询审核记录，获取申请人的 user_id。
  3. 更新审核表 (counselor_audit_record):
    - 更新 status, audit_time, auditor_id。
    - 记录 audit_remark。
  4. 处理业务状态 (事务内):
    - 如果是 PASS (通过):
      - 修改 sys_user 表：status = 1 (正常)。
      - 同步/更新 counselor_profile 表数据。
      - 【新增】写入通知: 向 sys_notification 插入一条记录。
        - type: "audit"
        - title: "资质审核通过"
        - content: "恭喜您，您的咨询师资质审核已通过！您现在可以设置排班并开始接单了。"
    - 如果是 REJECT (拒绝):
      - 修改 sys_user 表：status 保持 2 (或视业务需求而定)。
      - 【新增】写入通知: 向 sys_notification 插入一条记录。
        - type: "audit"
        - title: "资质审核未通过"
        - content: "很遗憾，您的资质审核被驳回。原因：" + remark + "。请修改资料后重新提交。"
  5. 返回响应: 成功。