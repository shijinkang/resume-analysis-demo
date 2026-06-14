package com.demo.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.resume.model.dto.InterviewPackDTO;
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
public class QuestionGenService {

    private final ChatModel chatModel;
    private final ResumeRepo resumeRepo;
    private final JobDescriptionRepo jobDescriptionRepo;
    private final AnalysisResultRepo analysisResultRepo;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    /**
     * 根据简历和 JD 生成面试题列表，结果会缓存到 Caffeine（key = resumeId）。
     * 若该简历已有关联的 AnalysisResult 则读取其中的 JD；否则使用最新一条 JD。
     */
    @Cacheable(value = "questions", key = "#resumeId")
    public InterviewPackDTO generateQuestions(Long resumeId) {
        log.info("开始生成面试题，简历 ID: {}", resumeId);
        long startTime = System.currentTimeMillis();

        Resume resume = loadResume(resumeId);
        String jdContent = resolveJdContent(resumeId);

        String promptText = buildPrompt(jdContent, resume.getStructuredData());
        String aiResponse = callAi(resumeId, promptText);
        String cleanJson = stripMarkdownFences(aiResponse);

        InterviewPackDTO dto = parseQuestions(resumeId, cleanJson);
        dto.setResumeId(resumeId);
        extractCandidateName(resume.getStructuredData(), dto);

        persistQuestions(resumeId, cleanJson);

        log.info("面试题生成完成，简历 ID: {}，共 {} 道题，耗时: {}ms",
                resumeId, dto.getQuestions() == null ? 0 : dto.getQuestions().size(),
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
     * 优先从与该简历关联的 AnalysisResult 中取 JD；若无则查最新一条 JD 记录。
     * 若实在找不到 JD，则使用通用面试题生成（jdContent 为空字符串）。
     */
    private String resolveJdContent(Long resumeId) {
        LambdaQueryWrapper<AnalysisResult> query = new LambdaQueryWrapper<>();
        query.eq(AnalysisResult::getResumeId, resumeId)
             .orderByDesc(AnalysisResult::getCreatedAt)
             .last("LIMIT 1");
        AnalysisResult latestResult = analysisResultRepo.selectOne(query);

        if (latestResult != null && latestResult.getJdId() != null) {
            JobDescription jd = jobDescriptionRepo.selectById(latestResult.getJdId());
            if (jd != null && jd.getContent() != null && !jd.getContent().isBlank()) {
                log.debug("使用关联 JD ID: {} 生成面试题，简历 ID: {}", latestResult.getJdId(), resumeId);
                return jd.getContent();
            }
        }

        log.warn("未找到与简历 ID: {} 关联的有效 JD，将使用空 JD 内容生成通用面试题", resumeId);
        return "";
    }

    /**
     * 加载 Prompt 模板并注入 JD 和简历数据
     */
    private String buildPrompt(String jdContent, String resumeData) {
        try {
            var resource = resourceLoader.getResource("classpath:prompts/gen-questions.st");
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            return template
                    .replace("{jdContent}", jdContent)
                    .replace("{resumeData}", resumeData);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 Prompt 模板 gen-questions.st", e);
        }
    }

    /**
     * 调用 AI 生成面试题，temperature=0.7 增加创意性
     */
    private String callAi(Long resumeId, String promptText) {
        try {
            log.debug("调用 AI API，简历 ID: {}", resumeId);
            var options = OpenAiChatOptions.builder()
                    .temperature(0.7)
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
     * 解析 AI 返回的面试题 JSON，包含题目、类别、难度、评分标准等字段
     */
    private InterviewPackDTO parseQuestions(Long resumeId, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode questionsNode = root.path("questions");

            if (!questionsNode.isArray()) {
                log.warn("AI 响应中 questions 字段不是数组，简历 ID: {}", resumeId);
                throw new RuntimeException("AI 响应格式错误：questions 字段应为数组");
            }

            List<InterviewPackDTO.QuestionDTO> questions = new ArrayList<>();
            for (JsonNode node : questionsNode) {
                InterviewPackDTO.QuestionDTO q = new InterviewPackDTO.QuestionDTO();
                q.setId(node.path("id").asInt());
                q.setQuestion(node.path("question").asText());
                q.setCategory(node.path("category").asText());
                q.setDifficulty(node.path("difficulty").asInt());
                q.setScoringCriteria(node.path("scoringCriteria").asText());
                q.setType(node.path("type").asText());

                List<String> keyPoints = new ArrayList<>();
                JsonNode keyPointsNode = node.path("keyPoints");
                if (keyPointsNode.isArray()) {
                    keyPointsNode.forEach(kp -> keyPoints.add(kp.asText()));
                }
                q.setKeyPoints(keyPoints);

                questions.add(q);
            }

            log.info("成功解析 {} 道面试题，简历 ID: {}", questions.size(), resumeId);
            if (questions.size() < 10) {
                log.warn("生成题目数量 {} 少于要求的 10 道，简历 ID: {}", questions.size(), resumeId);
            }

            InterviewPackDTO dto = new InterviewPackDTO();
            dto.setQuestions(questions);
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
     * 持久化面试题到数据库，已存在则更新，不存在则新增
     */
    private void persistQuestions(Long resumeId, String questionsJson) {
        try {
            LambdaQueryWrapper<AnalysisResult> query = new LambdaQueryWrapper<>();
            query.eq(AnalysisResult::getResumeId, resumeId)
                 .orderByDesc(AnalysisResult::getCreatedAt)
                 .last("LIMIT 1");
            AnalysisResult existing = analysisResultRepo.selectOne(query);

            if (existing != null) {
                LambdaUpdateWrapper<AnalysisResult> update = new LambdaUpdateWrapper<>();
                update.eq(AnalysisResult::getId, existing.getId())
                      .set(AnalysisResult::getQuestions, questionsJson);
                analysisResultRepo.update(null, update);
                log.info("更新面试题到已有分析结果，AnalysisResult ID: {}", existing.getId());
            } else {
                AnalysisResult result = new AnalysisResult();
                result.setResumeId(resumeId);
                result.setQuestions(questionsJson);
                analysisResultRepo.insert(result);
                log.info("新增分析结果（含面试题），简历 ID: {}", resumeId);
            }
        } catch (Exception e) {
            log.error("保存面试题失败，简历 ID: {}", resumeId, e);
            throw new RuntimeException("保存面试题失败", e);
        }
    }
}
