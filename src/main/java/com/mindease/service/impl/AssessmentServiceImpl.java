package com.mindease.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindease.common.exception.BaseException;
import com.mindease.mapper.*;
import com.mindease.pojo.dto.AssessmentSubmitDTO;
import com.mindease.pojo.dto.QuestionManageDTO;
import com.mindease.pojo.dto.ScaleSaveDTO;
import com.mindease.pojo.entity.*;
import com.mindease.pojo.vo.*;
import com.mindease.service.AssessmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 心理测评服务实现类
 */
@Service
@Slf4j
public class AssessmentServiceImpl implements AssessmentService {

    @Autowired
    private AssessmentScaleMapper assessmentScaleMapper;

    @Autowired
    private AssessmentQuestionMapper assessmentQuestionMapper;

    @Autowired
    private AssessmentRecordMapper assessmentRecordMapper;

    @Autowired
    private AssessmentAnswerMapper assessmentAnswerMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取量表列表
     *
     * @return
     */
    @Override
    public ScaleListVO getScaleList() {
        List<AssessmentScale> scales = assessmentScaleMapper.listActive();

        List<ScaleListVO.ScaleItem> scaleItems = scales.stream()
                .map(scale -> ScaleListVO.ScaleItem.builder()
                        .id(scale.getId())
                        .scaleKey(scale.getScaleKey())
                        .title(scale.getTitle())
                        .coverUrl(scale.getCoverUrl())
                        .description(scale.getDescription())
                        .status(scale.getStatus())
                        .build())
                .collect(Collectors.toList());

        return ScaleListVO.builder()
                .scales(scaleItems)
                .build();
    }

    /**
     * 获取量表详情
     *
     * @param scaleKey
     * @return
     */
    @Override
    public ScaleDetailVO getScaleDetail(String scaleKey) {
        // 查询量表基本信息
        AssessmentScale scale = assessmentScaleMapper.getByScaleKey(scaleKey);
        if (scale == null) {
            throw new BaseException("量表不存在");
        }

        // 查询题目列表
        List<AssessmentQuestion> questions = assessmentQuestionMapper.listByScaleKey(scaleKey);

        // 构建题目列表
        List<ScaleDetailVO.QuestionItem> questionItems = questions.stream()
                .map(question -> {
                    // 解析选项JSON
                    List<ScaleDetailVO.OptionItem> options = parseOptions(question.getOptions());

                    return ScaleDetailVO.QuestionItem.builder()
                            .id(question.getId())
                            .text(question.getQuestionText())
                            .options(options)
                            .build();
                })
                .collect(Collectors.toList());

        return ScaleDetailVO.builder()
                .id(scale.getId())
                .scaleKey(scale.getScaleKey())
                .title(scale.getTitle())
                .description(scale.getDescription())
                .questions(questionItems)
                .build();
    }

    /**
     * 提交测评
     *
     * @param userId
     * @param submitDTO
     * @return
     */
    @Override
    @Transactional
    public AssessmentSubmitVO submitAssessment(Long userId, AssessmentSubmitDTO submitDTO) {
        String scaleKey = submitDTO.getScaleKey();

        // 查询量表
        AssessmentScale scale = assessmentScaleMapper.getByScaleKey(scaleKey);
        if (scale == null) {
            throw new BaseException("量表不存在");
        }

        // 计算总分
        int totalScore = submitDTO.getAnswers().stream()
                .mapToInt(AssessmentSubmitDTO.AnswerItem::getScore)
                .sum();

        // 根据评分规则判断结果等级
        List<ScaleSaveDTO.ScoringRule> scoringRules = parseScoringRules(scale.getScoringRules());
        String resultLevel = "";
        String resultDesc = "";

        for (ScaleSaveDTO.ScoringRule rule : scoringRules) {
            if (totalScore >= rule.getMin() && totalScore <= rule.getMax()) {
                resultLevel = rule.getLevel();
                resultDesc = rule.getDesc();
                break;
            }
        }

        // 保存测评记录
        AssessmentRecord record = AssessmentRecord.builder()
                .userId(userId)
                .scaleKey(scaleKey)
                .totalScore(totalScore)
                .resultLevel(resultLevel)
                .resultDesc(resultDesc)
                .build();

        assessmentRecordMapper.insert(record);

        // 查询题目信息，获取答案文本
        List<Long> questionIds = submitDTO.getAnswers().stream()
                .map(AssessmentSubmitDTO.AnswerItem::getQuestionId)
                .collect(Collectors.toList());

        List<AssessmentQuestion> questions = assessmentQuestionMapper.listByIds(questionIds);
        Map<Long, AssessmentQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(AssessmentQuestion::getId, q -> q));

        // 保存答案详情
        List<AssessmentAnswer> answers = submitDTO.getAnswers().stream()
                .map(answerItem -> {
                    AssessmentQuestion question = questionMap.get(answerItem.getQuestionId());
                    String answerText = getAnswerText(question.getOptions(), answerItem.getScore());

                    return AssessmentAnswer.builder()
                            .recordId(record.getId())
                            .questionId(answerItem.getQuestionId())
                            .score(answerItem.getScore())
                            .answerText(answerText)
                            .build();
                })
                .collect(Collectors.toList());

        if (!answers.isEmpty()) {
            assessmentAnswerMapper.batchInsert(answers);
        }

        return AssessmentSubmitVO.builder()
                .recordId(record.getId())
                .totalScore(totalScore)
                .resultLevel(resultLevel)
                .resultDesc(resultDesc)
                .build();
    }

    /**
     * 获取测评历史列表
     *
     * @param userId
     * @param limit
     * @return
     */
    @Override
    public AssessmentRecordListVO getRecordList(Long userId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }

        List<AssessmentRecord> records = assessmentRecordMapper.listByUserId(userId, limit);

        // 获取所有涉及到的scaleKey
        List<String> scaleKeys = records.stream()
                .map(AssessmentRecord::getScaleKey)
                .distinct()
                .collect(Collectors.toList());

        // 查询量表信息
        Map<String, String> scaleTitleMap = new HashMap<>();
        for (String scaleKey : scaleKeys) {
            AssessmentScale scale = assessmentScaleMapper.getByScaleKey(scaleKey);
            if (scale != null) {
                scaleTitleMap.put(scaleKey, scale.getTitle());
            }
        }

        // 构建返回结果
        List<AssessmentRecordListVO.RecordItem> recordItems = records.stream()
                .map(record -> AssessmentRecordListVO.RecordItem.builder()
                        .id(record.getId())
                        .scaleKey(record.getScaleKey())
                        .scaleTitle(scaleTitleMap.getOrDefault(record.getScaleKey(), ""))
                        .totalScore(record.getTotalScore())
                        .resultLevel(record.getResultLevel())
                        .createTime(record.getCreateTime())
                        .build())
                .collect(Collectors.toList());

        return AssessmentRecordListVO.builder()
                .records(recordItems)
                .build();
    }

    /**
     * 获取单次测评详情
     *
     * @param id
     * @return
     */
    @Override
    public AssessmentRecordDetailVO getRecordDetail(Long id) {
        // 查询测评记录
        AssessmentRecord record = assessmentRecordMapper.getById(id);
        if (record == null) {
            throw new BaseException("测评记录不存在");
        }

        // 查询量表信息
        AssessmentScale scale = assessmentScaleMapper.getByScaleKey(record.getScaleKey());
        String scaleTitle = scale != null ? scale.getTitle() : "";

        // 查询答案详情
        List<AssessmentAnswer> answers = assessmentAnswerMapper.listByRecordId(id);

        // 查询题目信息
        List<Long> questionIds = answers.stream()
                .map(AssessmentAnswer::getQuestionId)
                .collect(Collectors.toList());

        Map<Long, AssessmentQuestion> questionMap = new HashMap<>();
        if (!questionIds.isEmpty()) {
            List<AssessmentQuestion> questions = assessmentQuestionMapper.listByIds(questionIds);
            questionMap = questions.stream()
                    .collect(Collectors.toMap(AssessmentQuestion::getId, q -> q));
        }

        // 构建答案详情列表
        List<AssessmentRecordDetailVO.AnswerDetail> answerDetails = new ArrayList<>();
        for (AssessmentAnswer answer : answers) {
            AssessmentQuestion question = questionMap.get(answer.getQuestionId());
            String questionText = question != null ? question.getQuestionText() : "";

            answerDetails.add(AssessmentRecordDetailVO.AnswerDetail.builder()
                    .questionId(answer.getQuestionId())
                    .questionText(questionText)
                    .score(answer.getScore())
                    .answerText(answer.getAnswerText())
                    .build());
        }

        return AssessmentRecordDetailVO.builder()
                .id(record.getId())
                .scaleKey(record.getScaleKey())
                .scaleTitle(scaleTitle)
                .totalScore(record.getTotalScore())
                .resultLevel(record.getResultLevel())
                .resultDesc(record.getResultDesc())
                .answersDetail(answerDetails)
                .createTime(record.getCreateTime())
                .build();
    }

    /**
     * 创建或更新量表（管理员）
     *
     * @param scaleSaveDTO
     * @return
     */
    @Override
    @Transactional
    public ScaleSaveVO saveScale(ScaleSaveDTO scaleSaveDTO) {
        // 将评分规则转换为JSON字符串
        String scoringRulesJson = toJson(scaleSaveDTO.getScoringRules());

        boolean isUpdate = scaleSaveDTO.getId() != null;

        if (isUpdate) {
            // 更新操作
            AssessmentScale scale = assessmentScaleMapper.getById(scaleSaveDTO.getId());
            if (scale == null) {
                throw new BaseException("量表不存在");
            }

            scale.setTitle(scaleSaveDTO.getTitle());
            scale.setCoverUrl(scaleSaveDTO.getCoverUrl());
            scale.setDescription(scaleSaveDTO.getDescription());
            scale.setStatus(scaleSaveDTO.getStatus());
            scale.setScoringRules(scoringRulesJson);

            assessmentScaleMapper.update(scale);

            log.info("更新量表成功，ID: {}, scaleKey: {}", scale.getId(), scale.getScaleKey());

            return ScaleSaveVO.builder()
                    .scaleId(scale.getId())
                    .isUpdate(true)
                    .build();
        } else {
            // 创建操作
            // 检查scaleKey是否已存在
            AssessmentScale existScale = assessmentScaleMapper.getByScaleKey(scaleSaveDTO.getScaleKey());
            if (existScale != null) {
                throw new BaseException("量表Key已存在");
            }

            AssessmentScale scale = AssessmentScale.builder()
                    .scaleKey(scaleSaveDTO.getScaleKey())
                    .title(scaleSaveDTO.getTitle())
                    .coverUrl(scaleSaveDTO.getCoverUrl())
                    .description(scaleSaveDTO.getDescription())
                    .status(scaleSaveDTO.getStatus() != null ? scaleSaveDTO.getStatus() : "active")
                    .scoringRules(scoringRulesJson)
                    .build();

            assessmentScaleMapper.insert(scale);

            log.info("创建量表成功，ID: {}, scaleKey: {}", scale.getId(), scale.getScaleKey());

            return ScaleSaveVO.builder()
                    .scaleId(scale.getId())
                    .isUpdate(false)
                    .build();
        }
    }

    /**
     * 管理量表题目（管理员）
     *
     * @param questionManageDTO
     * @return
     */
    @Override
    @Transactional
    public QuestionManageVO manageQuestions(QuestionManageDTO questionManageDTO) {
        String scaleKey = questionManageDTO.getScaleKey();

        // 检查量表是否存在
        AssessmentScale scale = assessmentScaleMapper.getByScaleKey(scaleKey);
        if (scale == null) {
            throw new BaseException("量表不存在");
        }

        int count = 0;

        for (QuestionManageDTO.QuestionItem questionItem : questionManageDTO.getQuestions()) {
            // 判断是否为删除操作
            if (Boolean.TRUE.equals(questionItem.getDeleted())) {
                // 删除题目
                if (questionItem.getId() != null) {
                    // 先删除该题目的所有答案记录
                    // 注意：这里需要谨慎处理，如果有历史测评记录引用了这个题目，可能需要保留
                    // 目前只删除题目本身
                    assessmentQuestionMapper.delete(questionItem.getId());
                    count++;
                    log.info("删除题目，ID: {}", questionItem.getId());
                }
                continue;
            }

            // 新增或更新操作
            String optionsJson = toJson(questionItem.getOptions());

            if (questionItem.getId() != null) {
                // 更新题目
                AssessmentQuestion question = assessmentQuestionMapper.getById(questionItem.getId());
                if (question != null) {
                    question.setQuestionText(questionItem.getQuestionText());
                    question.setOptions(optionsJson);
                    question.setSortOrder(questionItem.getSortOrder());

                    assessmentQuestionMapper.update(question);
                    count++;
                    log.info("更新题目，ID: {}", questionItem.getId());
                }
            } else {
                // 新增题目（需要检查是否重复）
                // 通过题目内容判断是否重复
                List<AssessmentQuestion> existingQuestions = assessmentQuestionMapper.listByScaleKey(scaleKey);
                boolean isDuplicate = existingQuestions.stream()
                        .anyMatch(q -> q.getQuestionText().equals(questionItem.getQuestionText()));

                if (isDuplicate) {
                    log.warn("题目内容重复，跳过插入: {}", questionItem.getQuestionText());
                    continue;
                }

                AssessmentQuestion question = AssessmentQuestion.builder()
                        .scaleKey(scaleKey)
                        .questionText(questionItem.getQuestionText())
                        .options(optionsJson)
                        .sortOrder(questionItem.getSortOrder())
                        .build();

                assessmentQuestionMapper.insert(question);
                count++;
                log.info("新增题目，内容: {}", questionItem.getQuestionText());
            }
        }

        return QuestionManageVO.builder()
                .success(true)
                .count(count)
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析选项JSON
     */
    private List<ScaleDetailVO.OptionItem> parseOptions(String optionsJson) {
        try {
            return objectMapper.readValue(optionsJson,
                    new TypeReference<List<ScaleDetailVO.OptionItem>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析选项JSON失败: {}", optionsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析评分规则JSON
     */
    private List<ScaleSaveDTO.ScoringRule> parseScoringRules(String scoringRulesJson) {
        try {
            return objectMapper.readValue(scoringRulesJson,
                    new TypeReference<List<ScaleSaveDTO.ScoringRule>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析评分规则JSON失败: {}", scoringRulesJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将对象转换为JSON字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("转换JSON失败", e);
            throw new BaseException("数据格式错误");
        }
    }

    /**
     * 根据分数获取答案文本
     */
    private String getAnswerText(String optionsJson, Integer score) {
        try {
            List<Map<String, Object>> options = objectMapper.readValue(optionsJson,
                    new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> option : options) {
                Integer optionScore = (Integer) option.get("score");
                if (optionScore != null && optionScore.equals(score)) {
                    return (String) option.get("label");
                }
            }
        } catch (Exception e) {
            log.error("解析选项失败: {}", optionsJson, e);
        }
        return "";
    }
}

