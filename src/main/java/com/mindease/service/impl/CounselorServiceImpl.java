package com.mindease.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindease.common.exception.BaseException;
import com.mindease.mapper.*;
import org.springframework.transaction.annotation.Transactional;
import com.mindease.pojo.dto.ReviewSubmitDTO;
import com.mindease.pojo.entity.*;
import com.mindease.pojo.vo.*;
import com.mindease.service.CounselorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CounselorServiceImpl implements CounselorService {

    @Autowired
    private CounselorProfileMapper counselorProfileMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MoodLogMapper moodLogMapper;

    @Autowired
    private AssessmentRecordMapper assessmentRecordMapper;

    @Autowired
    private CounselorReviewMapper counselorReviewMapper;

    @Autowired
    private AppointmentMapper appointmentMapper;

    @Autowired
    private com.mindease.service.AppointmentService appointmentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 智能推荐咨询师
     */
    @Override
    public RecommendResultVO recommendCounselors(Long userId, String keyword, String sort) {
        log.info("智能推荐咨询师，用户ID:{}，关键词:{}，排序:{}", userId, keyword, sort);

        // 1. 获取用户上下文
        List<String> keywords = new ArrayList<>();
        String strategy = "hot_list";
        String basedOn = "热门咨询师列表";
        List<String> userTags = new ArrayList<>();

        // 1.1 查询用户最近7天的情绪日志
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<MoodLog> recentMoodLogs = moodLogMapper.getRecentMoodLogs(userId, sevenDaysAgo);

        boolean isUrgent = false;
        if (recentMoodLogs != null && !recentMoodLogs.isEmpty()) {
            double avgScore = recentMoodLogs.stream()
                    .mapToInt(MoodLog::getMoodScore)
                    .average()
                    .orElse(10.0);

            if (avgScore < 4) {
                isUrgent = true;
                userTags.add("紧急");
            }
        }

        // 1.2 查询用户最近的测评记录
        AssessmentRecord latestAssessment = assessmentRecordMapper.getLatestByUserId(userId);
        if (latestAssessment != null) {
            strategy = "assessment_based";
            basedOn = latestAssessment.getScaleKey() + " " + latestAssessment.getResultLevel();

            // 提取关键词
            String resultLevel = latestAssessment.getResultLevel();
            if (resultLevel != null) {
                if (resultLevel.contains("焦虑")) {
                    keywords.add("焦虑");
                }
                if (resultLevel.contains("抑郁")) {
                    keywords.add("抑郁");
                }
                if (resultLevel.contains("失眠") || resultLevel.contains("睡眠")) {
                    keywords.add("失眠");
                }
                userTags.add(resultLevel);
            }
        } else if (recentMoodLogs != null && !recentMoodLogs.isEmpty()) {
            strategy = "mood_based";
            basedOn = "近期情绪状态";
        }

        // 1.3 手动关键词优先级最高
        if (keyword != null && !keyword.trim().isEmpty()) {
            String trimmedKeyword = keyword.trim();
            keywords.clear();
            keywords.add(trimmedKeyword);
            strategy = "keyword_search";
            basedOn = "搜索关键词：" + trimmedKeyword;
        }

        // 1.4 生成关键词变体，提升模糊匹配（如“焦虑症”→“焦虑”）
        keywords = expandKeywordVariants(keywords);

        // 2. 查询咨询师列表
        List<CounselorProfile> profiles;
        boolean hasKeyword = !keywords.isEmpty();
        if (!hasKeyword) {
            profiles = counselorProfileMapper.getAllActiveCounselors();
        } else {
            profiles = counselorProfileMapper.recommendCounselors(
                    keywords,
                    sort != null ? sort : "smart"
            );
        }

        // 3. 构建推荐列表
        // 为了在 lambda 中使用，创建 final 副本
        final List<String> finalKeywords = keywords;
        final boolean finalIsUrgent = isUrgent;
        
        List<CounselorRecommendVO> counselors = profiles.stream().map(profile -> {
            // 获取用户头像
            User user = userMapper.getById(profile.getUserId());

            // 解析 specialty JSON
            List<String> specialtyList = parseJsonArray(profile.getSpecialty());

            // 生成匹配理由
            String matchReason = generateMatchReason(profile, finalKeywords, finalIsUrgent);

            // 生成标签
            List<String> tags = generateTags(profile, finalIsUrgent);

            return CounselorRecommendVO.builder()
                    .id(profile.getUserId())
                    .realName(profile.getRealName())
                    .avatar(user != null ? user.getAvatar() : null)
                    .title(profile.getTitle())
                    .experienceYears(profile.getExperienceYears())
                    .specialty(specialtyList)
                    .rating(profile.getRating())
                    .pricePerHour(profile.getPricePerHour())
                    .location(profile.getLocation())
                    .nextAvailableTime(null) // 下一个可用时段（需前端调用可用时段接口获取）
                    .matchReason(matchReason)
                    .tags(tags)
                    .build();
        }).collect(Collectors.toList());

        // 4. 构建推荐上下文
        RecommendContextVO context = RecommendContextVO.builder()
                .strategy(strategy)
                .basedOn(basedOn)
                .userTags(userTags)
                .build();

        return RecommendResultVO.builder()
                .recommendContext(context)
                .counselors(counselors)
                .build();
    }

    /**
     * 为关键词生成变体，增强模糊匹配能力
     * 规则：
     * 1) 保留原词
     * 2) 去除常见中文后缀（症/障碍/问题/情况/状态/情绪/病/感）
     * 3) 去掉末尾一个字符（长度>2时），兜底提升部分匹配
     */
    private List<String> expandKeywordVariants(List<String> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> suffixes = Arrays.asList("症", "障碍", "问题", "情况", "状态", "情绪", "病", "感");
        List<String> result = new ArrayList<>();
        for (String kw : source) {
            if (kw == null) continue;
            String base = kw.trim();
            if (base.isEmpty()) continue;
            addIfAbsent(result, base);
            for (String suffix : suffixes) {
                if (base.endsWith(suffix) && base.length() > suffix.length() + 0) {
                    String stripped = base.substring(0, base.length() - suffix.length());
                    if (stripped.length() >= 2) {
                        addIfAbsent(result, stripped);
                    }
                }
            }
            if (base.length() > 2) {
                String shorter = base.substring(0, base.length() - 1);
                addIfAbsent(result, shorter);
            }
        }
        return result;
    }

    private void addIfAbsent(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    /**
     * 检查推荐前置状态
     */
    @Override
    public RecommendStatusVO getRecommendStatus(Long userId) {
        boolean hasAssessment = assessmentRecordMapper.countByUserId(userId) > 0;
        boolean hasMoodLog = moodLogMapper.countByUserId(userId) > 0;

        String lastAssessmentLevel = null;
        if (hasAssessment) {
            AssessmentRecord latestRecord = assessmentRecordMapper.getLatestByUserId(userId);
            lastAssessmentLevel = latestRecord != null ? latestRecord.getResultLevel() : null;
        }

        boolean recommendationReady = hasAssessment || hasMoodLog;

        return RecommendStatusVO.builder()
                .hasAssessment(hasAssessment)
                .hasMoodLog(hasMoodLog)
                .lastAssessmentLevel(lastAssessmentLevel)
                .recommendationReady(recommendationReady)
                .build();
    }

    /**
     * 获取咨询师详情
     */
    @Override
    public CounselorDetailVO getCounselorDetail(Long counselorId) {
        CounselorProfile profile = counselorProfileMapper.getByUserId(counselorId);
        if (profile == null) {
            throw new BaseException("咨询师不存在");
        }

        User user = userMapper.getById(counselorId);
        List<String> specialtyList = parseJsonArray(profile.getSpecialty());

        // 提取评价中的高频词作为标签
        List<String> tags = extractTagsFromReviews(counselorId);

        return CounselorDetailVO.builder()
                .id(counselorId)
                .realName(profile.getRealName())
                .avatar(user != null ? user.getAvatar() : null)
                .title(profile.getTitle())
                .experienceYears(profile.getExperienceYears())
                .specialty(specialtyList)
                .bio(profile.getBio())
                .qualificationUrl(profile.getQualificationUrl())
                .rating(profile.getRating())
                .reviewCount(profile.getReviewCount())
                .pricePerHour(profile.getPricePerHour())
                .location(profile.getLocation())
                .isOnline(profile.getLocation() != null && profile.getLocation().contains("线上"))
                .tags(tags)
                .build();
    }

    /**
     * 获取咨询师评价列表
     */
    @Override
    public ReviewListVO getCounselorReviews(Long counselorId, Integer limit, Integer offset) {
        List<CounselorReview> reviews = counselorReviewMapper.getByCounselorId(counselorId, limit, offset);
        int total = counselorReviewMapper.countByCounselorId(counselorId);
        Double avgRating = counselorReviewMapper.getAvgRatingByCounselorId(counselorId);

        List<CounselorReviewVO> reviewVOList = reviews.stream().map(review -> {
            User user = userMapper.getById(review.getUserId());
            return CounselorReviewVO.builder()
                    .id(review.getId())
                    .userId(review.getUserId())
                    .nickname(user != null ? user.getNickname() : "匿名用户")
                    .avatar(user != null ? user.getAvatar() : null)
                    .rating(review.getRating())
                    .content(review.getContent())
                    .createTime(review.getCreateTime())
                    .build();
        }).collect(Collectors.toList());

        return ReviewListVO.builder()
                .total(total)
                .avgRating(avgRating != null ? BigDecimal.valueOf(avgRating).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .reviews(reviewVOList)
                .build();
    }

    /**
     * 提交评价
     */
    @Override
    @Transactional
    public Long submitReview(Long userId, ReviewSubmitDTO reviewSubmitDTO) {
        // 在提交评价前，先自动更新该用户的已过期预约状态
        appointmentService.autoCompleteExpiredAppointments(userId);

        // 验证预约订单是否存在且已完成
        Appointment appointment = appointmentMapper.getById(reviewSubmitDTO.getAppointmentId());
        if (appointment == null) {
            throw new BaseException("预约订单不存在");
        }
        
        if (!appointment.getUserId().equals(userId)) {
            throw new BaseException("无权评价该预约");
        }
        
        if (!"COMPLETED".equals(appointment.getStatus())) {
            throw new BaseException("只能评价已完成的预约");
        }

        // 检查是否已经评价过
        int existingReviewCount = counselorReviewMapper.countByAppointmentId(reviewSubmitDTO.getAppointmentId());
        if (existingReviewCount > 0) {
            throw new BaseException("该预约已经评价过了");
        }

        // 从预约订单中获取counselorId
        Long counselorId = appointment.getCounselorId();

        CounselorReview review = CounselorReview.builder()
                .appointmentId(reviewSubmitDTO.getAppointmentId())
                .counselorId(counselorId)
                .userId(userId)
                .rating(reviewSubmitDTO.getRating())
                .content(reviewSubmitDTO.getContent())
                .createTime(LocalDateTime.now())
                .build();

        counselorReviewMapper.insert(review);

        // 更新咨询师的评分统计
        updateCounselorRating(counselorId);

        return review.getId();
    }

    /**
     * 更新咨询师评分统计
     */
    private void updateCounselorRating(Long counselorId) {
        // 查询该咨询师的所有评价
        List<CounselorReview> reviews = counselorReviewMapper.listByCounselorId(counselorId);
        
        if (reviews.isEmpty()) {
            return;
        }

        // 计算平均评分
        double avgRating = reviews.stream()
                .mapToInt(CounselorReview::getRating)
                .average()
                .orElse(5.0);

        // 保留一位小数
        BigDecimal rating = BigDecimal.valueOf(avgRating)
                .setScale(1, RoundingMode.HALF_UP);

        // 更新咨询师资料
        CounselorProfile profile = counselorProfileMapper.getByUserId(counselorId);
        if (profile != null) {
            profile.setRating(rating);
            profile.setReviewCount(reviews.size());
            counselorProfileMapper.update(profile);
        }
    }

    /**
     * 从评价中提取标签
     */
    private List<String> extractTagsFromReviews(Long counselorId) {
        List<CounselorReview> reviews = counselorReviewMapper.getByCounselorId(counselorId, 20, 0);
        
        if (reviews.isEmpty()) {
            return Arrays.asList("暂无评价");
        }

        // 统计高频词（简化版本，使用预定义标签匹配）
        List<String> predefinedTags = Arrays.asList(
            "专业", "耐心", "温和", "负责", "细心", "热情", "友善", 
            "经验丰富", "善于倾听", "有帮助", "靠谱", "值得信赖"
        );
        
        List<String> matchedTags = new ArrayList<>();
        String allContent = reviews.stream()
                .map(CounselorReview::getContent)
                .filter(content -> content != null && !content.isEmpty())
                .collect(Collectors.joining(" "));

        for (String tag : predefinedTags) {
            if (allContent.contains(tag)) {
                matchedTags.add(tag);
                if (matchedTags.size() >= 3) {
                    break;
                }
            }
        }

        // 如果没有匹配的标签，返回默认标签
        if (matchedTags.isEmpty()) {
            matchedTags.add("专业咨询师");
        }

        return matchedTags;
    }

    /**
     * 解析 JSON 数组
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("解析 JSON 失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    /**
     * 生成匹配理由
     */
    private String generateMatchReason(CounselorProfile profile, List<String> keywords, boolean isUrgent) {
        if (keywords.isEmpty()) {
            return "经验丰富，评价良好。";
        }

        List<String> specialtyList = parseJsonArray(profile.getSpecialty());
        long matchCount = keywords.stream()
                .filter(specialtyList::contains)
                .count();

        if (matchCount > 0) {
            return String.format("擅长处理%s问题，有%d年经验。", String.join("、", keywords), profile.getExperienceYears() != null ? profile.getExperienceYears() : 0);
        }

        return "综合评分高，服务专业。";
    }

    /**
     * 生成标签
     */
    private List<String> generateTags(CounselorProfile profile, boolean isUrgent) {
        List<String> tags = new ArrayList<>();

        if (profile.getPricePerHour() != null && profile.getPricePerHour().compareTo(BigDecimal.valueOf(300)) < 0) {
            tags.add("价格亲民");
        }

        if (profile.getRating() != null && profile.getRating().compareTo(BigDecimal.valueOf(4.8)) >= 0) {
            tags.add("高评分");
        }

        if (profile.getReviewCount() != null && profile.getReviewCount() > 50) {
            tags.add("经验丰富");
        }

        if (isUrgent) {
            tags.add("今日可约");
        }

        return tags;
    }
}

