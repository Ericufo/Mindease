package com.mindease.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;


@AiService(
        //chatMemory = "chatMemory", // 配置会话记忆对象
        chatMemoryProvider = "chatMemoryProvider", // 配置会话记忆对象提供器
        contentRetriever = "contentRetriever" // 配置向量数据库检索对象
)
public interface ConsultantService {

    @SystemMessage(fromResource = "system.txt")
    Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);
}
