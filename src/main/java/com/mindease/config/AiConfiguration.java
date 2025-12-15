package com.mindease.config;

import com.mindease.aiservice.ConsultantService;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiConfiguration {

    @Autowired
    private ChatMemoryStore redisChatMemoryStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private RedisEmbeddingStore redisEmbeddingStore;

//    @Autowired
//    private ChatModel qwenChatModel;
//
//    @Bean
//    public ConsultantService consultantService() {
//        ConsultantService consultantService = AiServices.builder(ConsultantService.class)
//                .chatModel(qwenChatModel)
//                .build();
//        return consultantService;
//    }

//    // 会话记忆
//    @Bean
//    public ChatMemory chatMemory() {
//        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
//                .maxMessages(20)
//                .build();
//        return memory;
//    }

    // chatMemoryProvider
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
       ChatMemoryProvider chatMemoryProvider = new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(redisChatMemoryStore)
                        .build();
            }
        };
       return chatMemoryProvider;
    }

    // 构建向量数据库操作对象
    @Bean
    public EmbeddingStore store() {
        // 加载文档进内存
        List<Document> documents = ClassPathDocumentLoader.loadDocuments("content");
        //List<Document> documents = ClassPathDocumentLoader.loadDocuments("content", new ApachePdfBoxDocumentParser()); pdf版本好像有点不兼容？
        // 构建向量数据库操作对象
        //InMemoryEmbeddingStore store = new InMemoryEmbeddingStore();

        // 构建文档分割器对象
        DocumentSplitter ds = DocumentSplitters.recursive(500, 100);

        // 构建一个EmbeddingStoreIngestor对象，完成对文本数据切割，向量化，存储
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(redisEmbeddingStore)
                .documentSplitter(ds)
                .embeddingModel(embeddingModel)
                .build();
        // 执行向量化存储
        ingestor.ingest(documents);
        return redisEmbeddingStore;
    }

    // 构建向量数据库检索对象
    @Bean
    public ContentRetriever contentRetriever() {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(redisEmbeddingStore)
                .minScore(0.5)
                .maxResults(3)
                .embeddingModel(embeddingModel)
                .build();
    }

}