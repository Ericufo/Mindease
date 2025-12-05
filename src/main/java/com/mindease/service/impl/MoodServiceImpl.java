package com.mindease.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindease.mapper.MoodLogMapper;
import com.mindease.pojo.dto.MoodLogDTO;
import com.mindease.pojo.entity.MoodLog;
import com.mindease.pojo.vo.*;
import com.mindease.service.MoodService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MoodServiceImpl implements MoodService {

    @Autowired
    private MoodLogMapper moodLogMapper;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MoodLogVO submitMoodLog(MoodLogDTO moodLogDTO, Long userId) {
        // 创建MoodLog实体
        MoodLog moodLog = new MoodLog();
        BeanUtils.copyProperties(moodLogDTO, moodLog);

        moodLog.setUserId(userId);
        
        // 处理标签，将List转换为JSON字符串
        if (moodLogDTO.getTags() != null && !moodLogDTO.getTags().isEmpty()) {
            try {
                String tagsJson = objectMapper.writeValueAsString(moodLogDTO.getTags());
                moodLog.setTags(tagsJson);
            } catch (JsonProcessingException e) {
                log.error("标签JSON转换失败", e);
                moodLog.setTags("[]");
            }
        } else {
            moodLog.setTags("[]");
        }
        
        // TODO: 设置AI分析
        moodLog.setAiAnalysis("今天也很棒，无论如何请继续加油");
        
        // 设置创建时间
        moodLog.setCreateTime(LocalDateTime.now());
        
        // 插入数据库
        moodLogMapper.insert(moodLog);
        
        // 构建返回结果
        MoodLogVO moodLogVO = new MoodLogVO();
        moodLogVO.setLogId(moodLog.getId());
        moodLogVO.setAiAnalysis(moodLog.getAiAnalysis());
        
        return moodLogVO;
    }

    @Override
    public MoodLogListVO getMoodLogList(Long userId, Integer limit, Integer offset) {
        log.info("获取情绪日记列表，用户ID: {}, limit: {}, offset: {}", userId, limit, offset);

        // 查询总数
        Long total = moodLogMapper.countByUserIdWithTotal(userId);
        
        // 分页查询日志列表
        List<MoodLog> moodLogs = moodLogMapper.getByUserIdWithPagination(userId, limit, offset);
        
        // 转换为VO列表
        List<MoodLogItemVO> logItems = moodLogs.stream()
                .map(this::convertToMoodLogItemVO)
                .collect(Collectors.toList());
        
        return MoodLogListVO.builder()
                .total(total)
                .logs(logItems)
                .build();
    }

    @Override
    public MoodLogDetailVO getMoodLogDetail(Long id) {
        log.info("获取情绪日记详情，ID: {}", id);

        MoodLog moodLog = moodLogMapper.getById(id);
        if (moodLog == null) {
            throw new RuntimeException("情绪日记不存在");
        }
        
        return convertToMoodLogDetailVO(moodLog);
    }

    @Override
    public Boolean deleteMoodLog(Long id) {
        log.info("删除情绪日记，ID: {}", id);

        int result = moodLogMapper.deleteById(id);
        return result > 0;
    }

    @Override
    public MoodTrendVO getMoodTrend(Long userId, Integer days) {
        log.info("获取情绪趋势，用户ID: {}, 天数: {}", userId, days);

        // 计算日期范围
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);
        
        // 查询指定时间范围内的情绪日志
        List<MoodLog> moodLogs = moodLogMapper.getByUserIdAndDateRange(userId, startDate, endDate);
        
        // 按日期分组，计算每日平均分
        Map<LocalDate, List<MoodLog>> logsByDate = moodLogs.stream()
                .collect(Collectors.groupingBy(log -> log.getLogDate().toLocalDate()));
        
        // 生成连续日期列表
        List<String> dates = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dates.add(date.format(formatter));
            
            List<MoodLog> dailyLogs = logsByDate.get(date);
            if (dailyLogs != null && !dailyLogs.isEmpty()) {
                int avgScore = (int) Math.round(dailyLogs.stream()
                        .mapToInt(MoodLog::getMoodScore)
                        .average()
                        .orElse(0.0));
                scores.add(avgScore);
            } else {
                scores.add(0);
            }
        }
        
        // 计算整体平均分
        double avgScore = moodLogs.stream()
                .mapToInt(MoodLog::getMoodScore)
                .average()
                .orElse(0.0);
        
        // 计算积极情绪比例（评分>=6为积极）
        long positiveCount = moodLogs.stream()
                .filter(log -> log.getMoodScore() >= 6)
                .count();
        double positiveRate = moodLogs.isEmpty() ? 0.0 : (double) positiveCount / moodLogs.size();
        
        // 计算连续记录天数
        int continuousDays = calculateContinuousDays(moodLogs);
        
        return MoodTrendVO.builder()
                .dates(dates)
                .scores(scores)
                .avgScore(avgScore)
                .positiveRate(Math.round(positiveRate * 100.0) / 100.0)
                .continuousDays(continuousDays)
                .build();
    }

    @Override
    public MoodStatisticsVO getMoodStatistics(Long userId) {
        log.info("获取情绪统计，用户ID: {}", userId);

        // 查询情绪类型分布
        List<MoodLogMapper.MoodTypeCount> typeCounts = moodLogMapper.getMoodTypeDistribution(userId);
        
        // 计算总数
        int totalLogs = typeCounts.stream()
                .mapToInt(count -> count.getCount().intValue())
                .sum();
        
        // 转换为百分比分布
        Map<String, String> distribution = new HashMap<>();
        for (MoodLogMapper.MoodTypeCount count : typeCounts) {
            double percentage = totalLogs == 0 ? 0.0 : (count.getCount().doubleValue() / totalLogs) * 100;
            distribution.put(count.getMoodType().toLowerCase(), String.format("%.0f%%", percentage));
        }
        
        // 计算平均分
        Double avgScore = moodLogMapper.getAverageScoreByUserId(userId);
        if (avgScore == null) {
            avgScore = 0.0;
        }
        
        return MoodStatisticsVO.builder()
                .distribution(distribution)
                .totalLogs(totalLogs)
                .avgScore(Math.round(avgScore * 10.0) / 10.0)
                .build();
    }

    /**
     * 将MoodLog转换为MoodLogItemVO
     */
    private MoodLogItemVO convertToMoodLogItemVO(MoodLog moodLog) {
        return MoodLogItemVO.builder()
                .id(moodLog.getId())
                .logDate(moodLog.getLogDate())
                .moodType(moodLog.getMoodType())
                .moodScore(moodLog.getMoodScore())
                .content(moodLog.getContent())
                .emoji(getEmojiByMoodType(moodLog.getMoodType()))
                .build();
    }

    /**
     * 将MoodLog转换为MoodLogDetailVO
     */
    private MoodLogDetailVO convertToMoodLogDetailVO(MoodLog moodLog) {
        // 解析标签JSON
        List<String> tags = new ArrayList<>();
        if (moodLog.getTags() != null && !moodLog.getTags().isEmpty()) {
            try {
                tags = objectMapper.readValue(moodLog.getTags(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                log.error("标签JSON解析失败", e);
            }
        }
        
        return MoodLogDetailVO.builder()
                .id(moodLog.getId())
                .userId(moodLog.getUserId())
                .moodType(moodLog.getMoodType())
                .moodScore(moodLog.getMoodScore())
                .content(moodLog.getContent())
                .tags(tags)
                .aiAnalysis(moodLog.getAiAnalysis())
                .logDate(moodLog.getLogDate())
                .createTime(moodLog.getCreateTime())
                .build();
    }

    /**
     * 根据情绪类型获取对应的emoji
     */
    private String getEmojiByMoodType(String moodType) {
        switch (moodType.toLowerCase()) {
            case "happy": return "😄";
            case "sad": return "😢";
            case "anxious": return "😰";
            case "calm": return "😌";
            case "angry": return "😠";
            case "tired": return "😴";
            case "excited": return "🤩";
            default: return "😐";
        }
    }

    /**
     * 计算连续记录天数
     */
    private int calculateContinuousDays(List<MoodLog> logs) {
        if (logs.isEmpty()) {
            return 0;
        }

        // 按日期降序排序（最新的在前）
        List<LocalDate> dates = logs.stream()
                .map(log -> log.getLogDate().toLocalDate())
                .distinct()
                .sorted((d1, d2) -> d2.compareTo(d1))
                .collect(Collectors.toList());

        // 从今天开始计算连续天数
        LocalDate today = LocalDate.now();
        int continuousDays = 0;
        
        for (LocalDate date : dates) {
            if (!date.isEqual(today.minusDays(continuousDays))) {
                break;
            }
            continuousDays++;
        }
        
        return continuousDays;
    }
}