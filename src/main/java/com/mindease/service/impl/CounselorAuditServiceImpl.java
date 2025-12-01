package com.mindease.service.impl;

import com.mindease.common.exception.BaseException;
import com.mindease.mapper.CounselorAuditRecordMapper;
import com.mindease.mapper.CounselorProfileMapper;
import com.mindease.mapper.SysNotificationMapper;
import com.mindease.mapper.UserMapper;
import com.mindease.pojo.dto.AuditProcessDTO;
import com.mindease.pojo.dto.AuditSubmitDTO;
import com.mindease.pojo.entity.CounselorAuditRecord;
import com.mindease.pojo.entity.CounselorProfile;
import com.mindease.pojo.entity.SysNotification;
import com.mindease.pojo.entity.User;
import com.mindease.pojo.vo.AuditListItemVO;
import com.mindease.pojo.vo.AuditListVO;
import com.mindease.pojo.vo.AuditStatusVO;
import com.mindease.pojo.vo.AuditSubmitVO;
import com.mindease.service.CounselorAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CounselorAuditServiceImpl implements CounselorAuditService {

    @Autowired
    private CounselorAuditRecordMapper auditRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CounselorProfileMapper counselorProfileMapper;

    @Autowired
    private SysNotificationMapper notificationMapper;

    /**
     * 提交资质审核
     */
    @Override
    @Transactional
    public AuditSubmitVO submitAudit(Long userId, AuditSubmitDTO submitDTO) {
        log.info("提交资质审核，用户ID:{}，数据:{}", userId, submitDTO);

        // 1. 验证用户是否存在
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }

        // 2. 验证角色
        if (!"COUNSELOR".equalsIgnoreCase(user.getRole())) {
            throw new BaseException("只有咨询师可以提交资质审核");
        }

        // 3. 创建审核记录
        CounselorAuditRecord record = CounselorAuditRecord.builder()
                .userId(userId)
                .realName(submitDTO.getRealName())
                .qualificationUrl(submitDTO.getQualificationUrl())
                .idCardUrl(submitDTO.getIdCardUrl())
                .status("PENDING")
                .createTime(LocalDateTime.now())
                .build();

        auditRecordMapper.insert(record);

        log.info("资质审核提交成功，审核ID:{}", record.getId());

        return AuditSubmitVO.builder()
                .auditId(record.getId())
                .build();
    }

    /**
     * 获取审核状态
     */
    @Override
    public AuditStatusVO getAuditStatus(Long userId) {
        log.info("获取审核状态，用户ID:{}", userId);

        CounselorAuditRecord latestRecord = auditRecordMapper.getLatestByUserId(userId);

        if (latestRecord == null) {
            throw new BaseException("暂无审核记录");
        }

        return AuditStatusVO.builder()
                .latestStatus(latestRecord.getStatus())
                .auditRemark(latestRecord.getAuditRemark())
                .submitTime(latestRecord.getCreateTime())
                .build();
    }

    /**
     * 获取待审核列表
     */
    @Override
    public AuditListVO getPendingAuditList(Integer page, Integer pageSize) {
        log.info("获取待审核列表，页码:{}，每页:{}", page, pageSize);

        int offset = (page - 1) * pageSize;
        List<CounselorAuditRecord> records = auditRecordMapper.getPendingList(pageSize, offset);
        int total = auditRecordMapper.countPending();

        List<AuditListItemVO> list = records.stream()
                .map(record -> {
                    User user = userMapper.getById(record.getUserId());
                    return AuditListItemVO.builder()
                            .auditId(record.getId())
                            .userId(record.getUserId())
                            .username(user != null ? user.getUsername() : "未知")
                            .realName(record.getRealName())
                            .qualificationUrl(record.getQualificationUrl())
                            .submitTime(record.getCreateTime())
                            .build();
                })
                .collect(Collectors.toList());

        return AuditListVO.builder()
                .total(total)
                .list(list)
                .build();
    }

    /**
     * 处理审核（通过/拒绝）
     */
    @Override
    @Transactional
    public void processAudit(Long adminId, AuditProcessDTO processDTO) {
        log.info("处理审核，管理员ID:{}，审核ID:{}，操作:{}", adminId, processDTO.getAuditId(), processDTO.getAction());

        // 1. 获取审核记录
        CounselorAuditRecord record = auditRecordMapper.getById(processDTO.getAuditId());
        if (record == null) {
            throw new BaseException("审核记录不存在");
        }

        // 2. 验证状态
        if (!"PENDING".equals(record.getStatus())) {
            throw new BaseException("该记录已处理，无法重复操作");
        }

        // 3. 验证操作类型
        String action = processDTO.getAction();
        if (!"PASS".equals(action) && !"REJECT".equals(action)) {
            throw new BaseException("操作类型只能是 PASS 或 REJECT");
        }

        // 4. 如果是拒绝，必须填写原因
        if ("REJECT".equals(action) && (processDTO.getRemark() == null || processDTO.getRemark().isEmpty())) {
            throw new BaseException("驳回时必须填写原因");
        }

        // 5. 更新审核记录
        String status = "PASS".equals(action) ? "APPROVED" : "REJECTED";
        auditRecordMapper.updateAuditStatus(
                processDTO.getAuditId(),
                status,
                adminId,
                processDTO.getRemark(),
                LocalDateTime.now()
        );

        // 6. 根据操作类型处理业务逻辑
        if ("PASS".equals(action)) {
            // 通过：更新用户状态为启用，创建/更新咨询师资料
            handleApproval(record);
            // 发送通知
            sendApprovalNotification(record.getUserId());
        } else {
            // 拒绝：发送通知
            sendRejectionNotification(record.getUserId(), processDTO.getRemark());
        }

        log.info("审核处理完成，审核ID:{}，结果:{}", processDTO.getAuditId(), status);
    }

    /**
     * 处理审核通过逻辑
     */
    private void handleApproval(CounselorAuditRecord record) {
        // 1. 更新用户状态为启用
        User user = userMapper.getById(record.getUserId());
        if (user != null) {
            user.setStatus(1);
            user.setUpdateTime(LocalDateTime.now());
            userMapper.update(user);
        }

        // 2. 创建或更新咨询师资料
        CounselorProfile existingProfile = counselorProfileMapper.getByUserId(record.getUserId());
        if (existingProfile == null) {
            // 创建新的咨询师资料
            CounselorProfile profile = CounselorProfile.builder()
                    .userId(record.getUserId())
                    .realName(record.getRealName())
                    .qualificationUrl(record.getQualificationUrl())
                    .rating(BigDecimal.valueOf(5.0))
                    .reviewCount(0)
                    .build();

            counselorProfileMapper.insert(profile);
            log.info("创建咨询师资料成功，用户ID:{}", record.getUserId());
        } else {
            // 更新现有资料
            existingProfile.setRealName(record.getRealName());
            existingProfile.setQualificationUrl(record.getQualificationUrl());
            counselorProfileMapper.update(existingProfile);
            log.info("更新咨询师资料成功，用户ID:{}", record.getUserId());
        }
    }

    /**
     * 发送审核通过通知
     */
    private void sendApprovalNotification(Long userId) {
        try {
            SysNotification notification = SysNotification.builder()
                    .userId(userId)
                    .type("audit")
                    .title("资质审核通过")
                    .content("恭喜您，您的咨询师资质审核已通过！您现在可以设置排班并开始接单了。")
                    .isRead(0)
                    .createTime(LocalDateTime.now())
                    .build();

            notificationMapper.insert(notification);
            log.info("已发送审核通过通知，用户ID:{}", userId);
        } catch (Exception e) {
            log.error("发送审核通过通知失败", e);
        }
    }

    /**
     * 发送审核拒绝通知
     */
    private void sendRejectionNotification(Long userId, String reason) {
        try {
            String content = "很遗憾，您的资质审核被驳回。原因：" + reason + "。请修改资料后重新提交。";

            SysNotification notification = SysNotification.builder()
                    .userId(userId)
                    .type("audit")
                    .title("资质审核未通过")
                    .content(content)
                    .isRead(0)
                    .createTime(LocalDateTime.now())
                    .build();

            notificationMapper.insert(notification);
            log.info("已发送审核拒绝通知，用户ID:{}", userId);
        } catch (Exception e) {
            log.error("发送审核拒绝通知失败", e);
        }
    }
}

