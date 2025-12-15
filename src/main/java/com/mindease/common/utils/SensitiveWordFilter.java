package com.mindease.common.utils;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SensitiveWordFilter {

    // 敏感词库，可以从数据库或配置文件加载
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "自杀", "自残", "自尽", "轻生", "寻死", "了结生命", "结束生命",
            "割腕", "上吊", "跳楼", "投河", "服毒", "割脉", "开煤气"
            // 可以根据需要添加更多敏感词
    ));

    /**
     * 检测文本中是否包含敏感词
     * @param text 待检测的文本
     * @return 如果包含敏感词返回true，否则返回false
     */
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // 转换为小写进行检测，提高匹配率
        String lowerText = text.toLowerCase();

        for (String word : SENSITIVE_WORDS) {
            if (lowerText.contains(word.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取检测到的第一个敏感词
     * @param text 待检测的文本
     * @return 返回检测到的第一个敏感词，如果没有则返回null
     */
    public String getFirstSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String lowerText = text.toLowerCase();

        for (String word : SENSITIVE_WORDS) {
            if (lowerText.contains(word.toLowerCase())) {
                return word;
            }
        }

        return null;
    }
    
    /**
     * 获取文本中所有的敏感词
     * @param text 待检测的文本
     * @return 返回检测到的所有敏感词列表，如果没有则返回空列表
     */
    public List<String> getAllSensitiveWords(String text) {
        List<String> foundWords = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return foundWords;
        }

        String lowerText = text.toLowerCase();

        for (String word : SENSITIVE_WORDS) {
            if (lowerText.contains(word.toLowerCase())) {
                foundWords.add(word);
            }
        }

        return foundWords;
    }
}