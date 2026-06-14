package com.demo.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.resume.model.dto.InterviewPackDTO;
import com.demo.resume.model.entity.AnalysisResult;
import com.demo.resume.model.entity.Resume;
import com.demo.resume.repository.AnalysisResultRepo;
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
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FollowUpService {

    private final ChatModel chatModel;
    private final ResumeRepo resumeRepo;
    private final AnalysisResultRepo analysisResultRepo;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    /**
     * 根据简历结构化数据生成追问题目，结果缓存（key = resumeId）
     */
    @Cacheable(value = "followup", key = "#resumeId")
    public InterviewPackDTO generateFollowUp(Long resumeId) {
        log.info("开始生成追问，简历 ID: {}", resumeId);
        long startTime = System.currentTimeMillis();

        Resume resume = loadResume(resumeId);

        String promptText = buildPrompt(resume.getStructuredData());
        String aiResponse = callAi(resumeId, promptText);
        String cleanJson = stripMarkdownFences(aiResponse);

        InterviewPackDTO dto = parseFollowUpQuestions(resumeId, cleanJson);
        dto.setResumeId(resumeId);
        extractCandidateName(resume.getStructuredData(), dto);

        persistFollowUpQuestions(resumeId, cleanJson);

        log.info("追问生成完成，简历 ID: {}，共 {} 个追问，耗时: {}ms",
                resumeId, dto.getFollowUpQuestions() == null ? 0 : dto.getFollowUpQuestions().size(),
                System.currentTimeMillis() - startTime);
        return dto;
    }

    private Resume loadResume(Long resumeId) {
        Resume resume = resumeRepo.selectById(resumeId);
        if (resume == null) {
            throw new IllegalArgumentException("简历不存在，ID: " + resumeId);
        }
        if (resume.getStructuredData() == null || resume.getStructuredData().isBlank()) {
            throw new IllegalArgumentException("简历尚未完成结构化提取，请先调用 /api/analysis/extract，ID: " + resumeId);
        }
        return resume;
    }

    /**
     * 加载 Prompt 模板并注入简历数据
     */
    private String buildPrompt(String resumeData) {
        try {
            var resource = resourceLoader.getResource("classpath:prompts/gen-followup.st");
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            return template.replace("{resumeData}", resumeData);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 Prompt 模板 gen-followup.st", e);
        }
    }

    /**
     * 调用 AI 生成追问，temperature=0.7 保持一定多样性
     */
    private String callAi(Long resumeId, String promptText) {
        try {
            log.debug("调用 AI API，简历 ID: {}", resumeId);
            var options = OpenAiChatOptions.builder()
                    .temperature(0.7)
                    .maxTokens(2048)
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
     * 解析 AI 返回的追问 JSON，校验数量应在 3-5 个之间
     */
    private InterviewPackDTO parseFollowUpQuestions(Long resumeId, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode questionsNode = root.path("questions");

            if (!questionsNode.isArray()) {
                log.warn("AI 响应中 questions 字段不是数组，简历 ID: {}", resumeId);
                throw new RuntimeException("AI 响应格式错误：questions 字段应为数组");
            }

            List<InterviewPackDTO.FollowUpQuestionDTO> followUpQuestions = new ArrayList<>();
            for (JsonNode node : questionsNode) {
                InterviewPackDTO.FollowUpQuestionDTO q = new InterviewPackDTO.FollowUpQuestionDTO();
                q.setId(node.path("id").asInt());
                q.setQuestion(node.path("question").asText());
                q.setContext(node.path("context").asText());
                q.setReason(node.path("reason").asText());
                followUpQuestions.add(q);
            }

            log.info("成功解析 {} 个追问，简历 ID: {}", followUpQuestions.size(), resumeId);
            if (followUpQuestions.size() < 3) {
                log.warn("生成追问数量 {} 少于要求的 3 个，简历 ID: {}", followUpQuestions.size(), resumeId);
            }
            if (followUpQuestions.size() > 5) {
                log.warn("生成追问数量 {} 超过要求的 5 个，简历 ID: {}", followUpQuestions.size(), resumeId);
            }

            InterviewPackDTO dto = new InterviewPackDTO();
            dto.setFollowUpQuestions(followUpQuestions);
            return dto;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 响应 JSON 解析失败，简历 ID: {}, 内容片段: {}",
                    resumeId, json.substring(0, Math.min(200, json.length())), e);
            throw new RuntimeException("AI 响应格式解析失败，请稍后重试", e);
        }
    }

    private void extractCandidateName(String structuredData, InterviewPackDTO dto) {
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
     * 持久化追问到数据库，优先写入最近一条关联的 AnalysisResult
     */
    private void persistFollowUpQuestions(Long resumeId, String followUpJson) {
        try {
            LambdaQueryWrapper<AnalysisResult> query = new LambdaQueryWrapper<>();
            query.eq(AnalysisResult::getResumeId, resumeId)
                 .orderByDesc(AnalysisResult::getCreatedAt)
                 .last("LIMIT 1");
            AnalysisResult existing = analysisResultRepo.selectOne(query);

            if (existing != null) {
                LambdaUpdateWrapper<AnalysisResult> update = new LambdaUpdateWrapper<>();
                update.eq(AnalysisResult::getId, existing.getId())
                      .set(AnalysisResult::getFollowupQuestions, followUpJson);
                analysisResultRepo.update(null, update);
                log.info("更新追问到已有分析结果，AnalysisResult ID: {}", existing.getId());
            } else {
                AnalysisResult result = new AnalysisResult();
                result.setResumeId(resumeId);
                result.setFollowupQuestions(followUpJson);
                analysisResultRepo.insert(result);
                log.info("新增分析结果（含追问），简历 ID: {}", resumeId);
            }
        } catch (Exception e) {
            log.error("保存追问失败，简历 ID: {}", resumeId, e);
            throw new RuntimeException("保存追问失败", e);
        }
    }
}
