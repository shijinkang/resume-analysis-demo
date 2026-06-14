package com.demo.resume.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.resume.model.entity.Resume;
import com.demo.resume.repository.ResumeRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeExtractService {

    private final ChatModel chatModel;
    private final ResumeRepo resumeRepo;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    /**
     * 调用 AI 将简历原始文本提取为结构化 JSON，结果缓存（key = resumeId）
     */
    @Cacheable(value = "resumeExtract", key = "#resumeId")
    public String extractStructuredInfo(Long resumeId, String rawText) {
        log.info("开始 AI 结构化提取，简历 ID: {}", resumeId);
        long startTime = System.currentTimeMillis();

        String promptText = buildPrompt(rawText);
        String aiResponse = callAi(resumeId, promptText);
        String structuredJson = stripMarkdownFences(resumeId, aiResponse);
        validateJson(resumeId, structuredJson);
        persistStructuredData(resumeId, structuredJson);

        log.info("简历 ID: {} 结构化提取完成，耗时 {}ms", resumeId, System.currentTimeMillis() - startTime);
        return structuredJson;
    }

    /**
     * 加载 Prompt 模板并注入简历原文
     */
    private String buildPrompt(String rawText) {
        try {
            var resource = resourceLoader.getResource("classpath:prompts/extract-resume.st");
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            return template.replace("{resumeText}", rawText);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 Prompt 模板 extract-resume.st", e);
        }
    }

    /**
     * 调用 AI 模型进行结构化提取，temperature=0.3 保证输出格式稳定
     */
    private String callAi(Long resumeId, String promptText) {
        try {
            log.debug("调用 AI API，简历 ID: {}", resumeId);
            var options = OpenAiChatOptions.builder()
                    .temperature(0.3)
                    .maxTokens(4096)
                    .build();
            var response = chatModel.call(new Prompt(promptText, options));
            String content = response.getResult().getOutput().getContent();
            log.debug("AI 响应长度: {} 字符，简历 ID: {}", content.length(), resumeId);
            return content;
        } catch (Exception e) {
            log.error("AI API 调用失败，简历 ID: {}", resumeId, e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 部分大模型会将 JSON 包裹在 markdown 代码块（```json ... ```）中，此处统一剥除。
     */
    private String stripMarkdownFences(Long resumeId, String aiResponse) {
        String trimmed = aiResponse.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        log.debug("解析后 JSON 前 100 字符，简历 ID: {}: {}",
                resumeId, trimmed.substring(0, Math.min(100, trimmed.length())));
        return trimmed;
    }

    /**
     * 校验 AI 返回的 JSON 是否包含所有必要字段，缺字段时仅告警不中断流程
     */
    private void validateJson(Long resumeId, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String[] requiredFields = {"name", "yearsOfExperience", "skills", "projects", "education"};
            for (String field : requiredFields) {
                if (!root.has(field)) {
                    log.warn("AI 响应缺少字段 [{}]，简历 ID: {}", field, resumeId);
                }
            }
        } catch (Exception e) {
            log.error("AI 响应 JSON 解析失败，简历 ID: {}, 内容: {}", resumeId, json, e);
            throw new RuntimeException("AI 响应格式解析失败，请稍后重试", e);
        }
    }

    private void persistStructuredData(Long resumeId, String structuredJson) {
        // 使用 UpdateWrapper 是因为 Resume.structuredData 字段配置了 FieldStrategy.IGNORED，insert/update 时不会自动写入
        LambdaUpdateWrapper<Resume> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Resume::getId, resumeId)
               .set(Resume::getStructuredData, structuredJson);
        int rows = resumeRepo.update(null, wrapper);
        if (rows == 0) {
            log.warn("简历 ID: {} 不存在，无法写入结构化数据", resumeId);
            throw new RuntimeException("简历记录不存在，ID: " + resumeId);
        }
        log.info("结构化数据已写入数据库，简历 ID: {}", resumeId);
    }
}
