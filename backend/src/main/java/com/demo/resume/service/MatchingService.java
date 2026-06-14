package com.demo.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.resume.model.dto.MatchResultDTO;
import com.demo.resume.model.entity.AnalysisResult;
import com.demo.resume.model.entity.JobDescription;
import com.demo.resume.model.entity.Resume;
import com.demo.resume.repository.AnalysisResultRepo;
import com.demo.resume.repository.JobDescriptionRepo;
import com.demo.resume.repository.ResumeRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchingService {

    private final ChatModel chatModel;
    private final JobDescriptionRepo jobDescriptionRepo;
    private final ResumeRepo resumeRepo;
    private final AnalysisResultRepo analysisResultRepo;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Value("${resume.extract.match-wait-timeout-ms:60000}")
    private long matchWaitTimeoutMs;

    @Value("${resume.extract.match-poll-interval-ms:2000}")
    private long matchPollIntervalMs;

    /**
     * 计算简历与 JD 的匹配度，调用 AI 进行多维度评分
     */
    @Cacheable(value = "matchScore", key = "#jdId + '-' + #resumeId")
    public MatchResultDTO calculateMatch(Long jdId, Long resumeId) {
        log.info("开始 AI 匹配评分，JD ID: {}, 简历 ID: {}", jdId, resumeId);
        long startTime = System.currentTimeMillis();

        JobDescription jd = loadJd(jdId);
        Resume resume = loadResume(resumeId);

        String promptText = buildPrompt(jd.getContent(), resume.getStructuredData());
        String aiResponse = callAi(jdId, resumeId, promptText);
        String cleanJson = stripMarkdownFences(aiResponse);
        MatchResultDTO dto = parseMatchResult(jdId, resumeId, cleanJson);
        dto.setResumeId(resumeId);

        extractCandidateName(resume.getStructuredData(), dto);
        persistAnalysisResult(jdId, resumeId, dto, cleanJson);

        log.info("匹配评分完成，JD ID: {}, 简历 ID: {}, 得分: {}, 耗时: {}ms",
                jdId, resumeId, dto.getScore(), System.currentTimeMillis() - startTime);
        return dto;
    }

    private JobDescription loadJd(Long jdId) {
        JobDescription jd = jobDescriptionRepo.selectById(jdId);
        if (jd == null) {
            throw new IllegalArgumentException("JD 不存在，ID: " + jdId);
        }
        if (jd.getContent() == null || jd.getContent().isBlank()) {
            throw new IllegalArgumentException("JD 内容为空，无法进行匹配，ID: " + jdId);
        }
        return jd;
    }

    private Resume loadResume(Long resumeId) {
        Resume resume = resumeRepo.selectById(resumeId);
        if (resume == null) {
            throw new IllegalArgumentException("简历不存在，ID: " + resumeId);
        }
        if (resume.getStructuredData() == null || resume.getStructuredData().isBlank()) {
            resume = waitForStructuredData(resumeId);
        }
        return resume;
    }

    private Resume waitForStructuredData(Long resumeId) {
        long deadline = System.currentTimeMillis() + matchWaitTimeoutMs;
        log.info("简历 ID: {} 结构化数据尚未就绪，等待异步提取完成（最多 {}ms）", resumeId, matchWaitTimeoutMs);

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(matchPollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待简历结构化提取时被中断，ID: " + resumeId, e);
            }

            Resume resume = resumeRepo.selectById(resumeId);
            if (resume == null) {
                throw new IllegalArgumentException("简历不存在，ID: " + resumeId);
            }
            if (resume.getStructuredData() != null && !resume.getStructuredData().isBlank()) {
                log.info("简历 ID: {} 结构化数据已就绪", resumeId);
                return resume;
            }
        }

        throw new IllegalArgumentException(
                "简历结构化提取超时（已等待 " + (matchWaitTimeoutMs / 1000) + "s），请稍后重试，ID: " + resumeId);
    }

    /**
     * 加载 Prompt 模板并注入 JD 和简历数据
     */
    private String buildPrompt(String jdContent, String resumeData) {
        try {
            var resource = resourceLoader.getResource("classpath:prompts/match-score.st");
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            return template
                    .replace("{jdContent}", jdContent)
                    .replace("{resumeData}", resumeData);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 Prompt 模板 match-score.st", e);
        }
    }

    /**
     * 调用 AI 模型进行匹配评分，temperature=0.3 保证输出稳定性
     */
    private String callAi(Long jdId, Long resumeId, String promptText) {
        try {
            log.debug("调用 AI API，JD ID: {}, 简历 ID: {}", jdId, resumeId);
            var options = OpenAiChatOptions.builder()
                    .temperature(0.3)
                    .maxTokens(4096)
                    .build();
            var response = chatModel.call(new Prompt(promptText, options));
            String content = response.getResult().getOutput().getContent();
            log.debug("AI 响应长度: {} 字符，JD ID: {}, 简历 ID: {}", content.length(), jdId, resumeId);
            return content;
        } catch (Exception e) {
            log.error("AI API 调用失败，JD ID: {}, 简历 ID: {}", jdId, resumeId, e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 移除 AI 响应中可能存在的 markdown 代码块标记
     */
    private String stripMarkdownFences(String aiResponse) {
        String trimmed = aiResponse.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    /**
     * 解析 AI 返回的 JSON，提取匹配评分结果
     */
    private MatchResultDTO parseMatchResult(Long jdId, Long resumeId, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            validateRequiredFields(root, jdId, resumeId);

            MatchResultDTO dto = new MatchResultDTO();
            dto.setScore(root.path("score").asInt());
            dto.setReason(root.path("reason").asText());
            dto.setRecommendation(root.path("recommendation").asText());

            dto.setDimensions(parseDimensions(root.path("dimensions")));
            dto.setStrengths(parseStringList(root.path("strengths")));
            dto.setGaps(parseStringList(root.path("gaps")));

            return dto;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 响应 JSON 解析失败，JD ID: {}, 简历 ID: {}, 内容: {}", jdId, resumeId, json, e);
            throw new RuntimeException("AI 响应格式解析失败，请稍后重试", e);
        }
    }

    private void validateRequiredFields(JsonNode root, Long jdId, Long resumeId) {
        String[] required = {"score", "reason", "dimensions", "recommendation", "strengths", "gaps"};
        for (String field : required) {
            if (!root.has(field)) {
                log.warn("AI 响应缺少字段 [{}]，JD ID: {}, 简历 ID: {}", field, jdId, resumeId);
            }
        }
    }

    /**
     * 解析多维度评分（技能匹配、工作年限、项目相关度、行业背景）
     */
    private Map<String, Integer> parseDimensions(JsonNode dimensionsNode) {
        Map<String, Integer> dimensions = new HashMap<>();
        if (dimensionsNode != null && dimensionsNode.isObject()) {
            dimensionsNode.fields().forEachRemaining(entry ->
                    dimensions.put(entry.getKey(), entry.getValue().asInt()));
        }
        return dimensions;
    }

    private List<String> parseStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(item -> list.add(item.asText()));
        }
        return list;
    }

    private void extractCandidateName(String structuredData, MatchResultDTO dto) {
        try {
            JsonNode root = objectMapper.readTree(structuredData);
            if (root.has("name")) {
                dto.setCandidateName(root.path("name").asText());
            }
        } catch (Exception e) {
            log.warn("无法从结构化数据中提取候选人姓名", e);
        }
    }

    /**
     * 持久化分析结果，已存在则更新，不存在则新增
     */
    private void persistAnalysisResult(Long jdId, Long resumeId, MatchResultDTO dto, String rawJson) {
        try {
            String dimensionsJson = objectMapper.writeValueAsString(dto.getDimensions());

            LambdaQueryWrapper<AnalysisResult> query = new LambdaQueryWrapper<>();
            query.eq(AnalysisResult::getJdId, jdId).eq(AnalysisResult::getResumeId, resumeId);
            AnalysisResult existing = analysisResultRepo.selectOne(query);

            if (existing != null) {
                LambdaUpdateWrapper<AnalysisResult> update = new LambdaUpdateWrapper<>();
                update.eq(AnalysisResult::getId, existing.getId())
                        .set(AnalysisResult::getMatchScore, dto.getScore())
                        .set(AnalysisResult::getMatchReason, dto.getReason())
                        .set(AnalysisResult::getMatchDimensions, dimensionsJson)
                        .set(AnalysisResult::getRecommendation, dto.getRecommendation());
                analysisResultRepo.update(null, update);
                log.info("更新已有分析结果，ID: {}", existing.getId());
            } else {
                AnalysisResult result = new AnalysisResult();
                result.setJdId(jdId);
                result.setResumeId(resumeId);
                result.setMatchScore(dto.getScore());
                result.setMatchReason(dto.getReason());
                result.setMatchDimensions(dimensionsJson);
                result.setRecommendation(dto.getRecommendation());
                analysisResultRepo.insert(result);
                log.info("新增分析结果，JD ID: {}, 简历 ID: {}", jdId, resumeId);
            }
        } catch (Exception e) {
            log.error("保存分析结果失败，JD ID: {}, 简历 ID: {}", jdId, resumeId, e);
            throw new RuntimeException("保存分析结果失败", e);
        }
    }
}
