# 第三阶段：AI 核心能力开发 - 执行计划与验收标准

## 📋 阶段目标

实现四个核心 AI 服务，通过 Spring AI 调用 Deepseek 大模型，完成简历结构化提取、智能匹配打分、面试题生成和追问生成功能。

---

## 🎯 任务分解

### 任务 1：简历结构化提取服务（ResumeExtractService）
**预计时间：** 0.5-1 天

**开发内容：**

1. 注入 `ChatModel` 和相关依赖（ResumeRepo）
2. 读取 `extract-resume.st` prompt 模板
3. 构建 AI 请求：
   - 使用 StringTemplate 或 String.format 填充 `{resumeText}` 占位符
   - 调用 `chatModel.call()` 获取 AI 响应
4. 解析 JSON 响应：
   - 使用 Jackson 或 Gson 解析返回的 JSON 字符串
   - 提取姓名、工作年限、技能栈、项目经历、教育背景
5. 更新数据库：
   - 将结构化数据（JSON 字符串）写入 `resumes.structured_data` 字段
   - 调用 `resumeRepo.updateById()`
6. 异常处理：
   - AI 调用超时（设置 30 秒超时）
   - JSON 解析失败（返回错误信息）
   - 数据库更新失败

**验收标准：**

- [ ] 能成功调用 Deepseek API 并获取响应
- [ ] 返回的 JSON 包含完整的结构化字段（name, yearsOfExperience, skills, projects, education）
- [ ] 结构化数据正确存入 H2 数据库的 `resumes.structured_data` 字段
- [ ] 缓存生效：同一 `resumeId` 第二次调用直接从缓存返回，无需再次调用 AI
- [ ] 使用 `docs/resume` 中至少 3 份不同格式的简历测试，提取准确率 > 90%
- [ ] 异常场景有友好提示（AI 超时、解析失败等）

---

### 任务 2：智能匹配打分服务（MatchingService）
**预计时间：** 1 天

**开发内容：**

1. 注入 `ChatModel`、`JobDescriptionRepo`、`ResumeRepo`、`AnalysisResultRepo`
2. 读取 JD 内容和简历结构化数据：
   - 从数据库查询 JD 的 `content` 字段
   - 从数据库查询 Resume 的 `structured_data` 字段
3. 读取 `match-score.st` prompt 模板
4. 构建 AI 请求：
   - 填充 `{jdContent}` 和 `{resumeData}` 占位符
   - 调用 `chatModel.call()`
5. 解析多维度评分结果：
   - 提取总分 `score`（0-100）
   - 提取评分理由 `reason`
   - 提取各维度分数 `dimensions`（技能匹配、工作年限、项目相关度、行业背景）
   - 提取推荐结果 `recommendation`（PROCEED/REJECT/MAYBE）
   - 提取优势 `strengths` 和不足 `gaps`
6. 保存分析结果：
   - 将匹配结果存入 `analysis_results` 表
   - 字段：`jd_id`, `resume_id`, `match_score`, `match_reason`, `match_dimensions`, `recommendation`
7. 返回结构化 DTO

**验收标准：**

- [ ] 能正确读取 JD 和简历数据
- [ ] AI 返回的 JSON 包含所有必需字段（score, reason, dimensions, recommendation, strengths, gaps）
- [ ] 总分计算合理（85-100=优秀，70-84=良好，50-69=中等，0-49=较差）
- [ ] 各维度分数总和与总分匹配（加权平均逻辑正确）
- [ ] 推荐结果与分数一致（高分→PROCEED，低分→REJECT）
- [ ] 评分理由具体明确，能指出匹配的优势和不足
- [ ] 使用 `docs/jd/` 和 `docs/resume/` 中的样本数据测试至少 5 组匹配，评分合理性人工验证通过
- [ ] 缓存生效：相同 `jdId` 和 `resumeId` 组合第二次调用直接返回缓存

---

### 任务 3：面试题生成服务（QuestionGenService）
**预计时间：** 0.5-1 天

**开发内容：**

1. 注入 `ChatModel`、`ResumeRepo`、`JobDescriptionRepo`、`AnalysisResultRepo`
2. 读取 JD 内容和简历结构化数据
3. 读取 `gen-questions.st` prompt 模板
4. 构建 AI 请求：
   - 填充 `{jdContent}` 和 `{resumeData}` 占位符
   - 调用 `chatModel.call()`
5. 解析生成的面试题列表：
   - 每道题包含：id, question, category, difficulty, keyPoints, scoringCriteria, type
   - 验证至少生成 10 道题
   - 验证题目分布：技术题 60%，行为题 30%，场景题 10%
   - 验证难度分布：简单 30%，中等 50%，困难 20%
6. 保存到数据库：
   - 将题目列表（JSON 数组）存入 `analysis_results.questions` 字段
7. 返回结构化 DTO

**验收标准：**

- [ ] 生成至少 10 道面试题
- [ ] 题目类型分布符合要求（TECHNICAL 60%, BEHAVIORAL 30%, SCENARIO 10%）
- [ ] 难度分布合理（1-5 级，符合 30%-50%-20% 分布）
- [ ] 每道题包含完整字段（question, category, difficulty, keyPoints, scoringCriteria, type）
- [ ] 题目针对性强，与简历中的技能和项目经验高度相关
- [ ] 评分标准具体可操作，便于面试官使用
- [ ] 使用 3 份不同背景的简历测试，生成的题目有明显差异化
- [ ] 缓存生效：相同 `resumeId` 第二次调用直接返回缓存

---

### 任务 4：追问生成服务（FollowUpService）
**预计时间：** 0.5 天

**开发内容：**

1. 注入 `ChatModel`、`ResumeRepo`、`AnalysisResultRepo`
2. 读取简历结构化数据
3. 读取 `gen-followup.st` prompt 模板
4. 构建 AI 请求：
   - 填充 `{resumeData}` 占位符
   - 调用 `chatModel.call()`
5. 解析追问列表：
   - 每个追问包含：id, question, context, reason
   - 验证生成 3-5 个追问
   - 追问应针对简历中的模糊点、量化缺失、技术深度不足等问题
6. 保存到数据库：
   - 将追问列表（JSON 数组）存入 `analysis_results.followup_questions` 字段
7. 返回结构化 DTO

**验收标准：**

- [ ] 生成 3-5 个针对性追问
- [ ] 每个追问包含完整字段（id, question, context, reason）
- [ ] 追问能有效识别简历中的模糊表述（如"改善性能"未量化、"参与项目"角色不明确）
- [ ] 追问能深挖技术深度（如"熟悉 Redis"→具体使用了哪些特性？数据规模？）
- [ ] 追问原因合理，能帮助面试官理解为什么要问这个问题
- [ ] 使用包含模糊描述的简历测试，追问准确指出模糊点
- [ ] 使用技术栈丰富的简历测试，追问能覆盖不同技术领域
- [ ] 缓存生效：相同 `resumeId` 第二次调用直接返回缓存

---

### 任务 5：Caffeine 缓存优化
**预计时间：** 0.5 天

**开发内容：**

1. 验证 `CacheConfig` 配置：
   - 确认缓存名称：`resumeExtract`, `matchScore`, `questions`, `followup`
   - 设置合理的过期时间（建议 1 小时）
   - 设置最大缓存数量（建议 1000）
2. 验证 `@Cacheable` 注解：
   - 确认 key 生成策略正确（避免缓存污染）
   - 测试缓存命中率
3. 添加缓存监控日志：
   - 记录缓存命中/未命中情况
   - 记录 AI 调用耗时

**验收标准：**

- [ ] 相同输入第二次调用不会再次调用 AI API
- [ ] 缓存 key 设计合理，不会产生冲突
- [ ] 缓存过期时间合理（1 小时），避免数据过期问题
- [ ] 日志能清晰显示是否命中缓存
- [ ] 测试缓存命中后响应时间 < 100ms（无 AI 调用）

---

## 🔧 技术实现要点

### 1. Spring AI 调用模式

```java
String prompt = loadPromptTemplate("extract-resume.st")
    .replace("{resumeText}", rawText);

ChatResponse response = chatModel.call(
    new Prompt(prompt, 
        OpenAiChatOptions.builder()
            .temperature(0.7)
            .maxTokens(4096)
            .build()
    )
);

String jsonResult = response.getResult().getOutput().getContent();
```

### 2. JSON 解析

```java
ObjectMapper mapper = new ObjectMapper();
JsonNode rootNode = mapper.readTree(jsonResult);

Gson gson = new Gson();
MatchResultDTO result = gson.fromJson(jsonResult, MatchResultDTO.class);
```

### 3. Prompt 模板加载

```java
@Autowired
private ResourceLoader resourceLoader;

String loadPrompt(String filename) throws IOException {
    Resource resource = resourceLoader.getResource("classpath:prompts/" + filename);
    return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
}
```

### 4. 异常处理模式

```java
try {
    String result = chatModel.call(prompt);
    return parseResult(result);
} catch (HttpTimeoutException e) {
    log.error("AI API timeout for resume {}", resumeId, e);
    throw new ServiceException("AI 服务超时，请稍后重试");
} catch (JsonProcessingException e) {
    log.error("Failed to parse AI response for resume {}", resumeId, e);
    throw new ServiceException("AI 响应解析失败");
}
```

---

## ✅ 总体验收标准

### 功能完整性
- [ ] 所有 4 个服务实现完整，方法不再返回空 JSON `{}`
- [ ] Controller 层能正常调用 Service 层，端到端流程可走通
- [ ] 数据库表 `resumes.structured_data` 和 `analysis_results.*` 字段能正确写入

### 质量标准
- [ ] 代码无编译错误和 linter 警告
- [ ] 日志记录完整（INFO 级别记录关键操作，ERROR 级别记录异常）
- [ ] 异常处理完善，用户能看到友好错误提示
- [ ] 代码符合项目已有风格（使用 Lombok、构造函数注入等）

### AI 效果标准
- [ ] 简历提取准确率 > 90%（人工评估 10 份简历）
- [ ] 匹配打分合理性 > 85%（人工评估 10 组匹配）
- [ ] 面试题针对性强，至少 80% 的题目与简历/JD 相关
- [ ] 追问能识别至少 70% 的简历模糊点

### 性能标准
- [ ] 单次 AI调用响应时间 < 10 秒（正常网络环境）
- [ ] 缓存命中后响应时间 < 100ms
- [ ] 批量处理 10 份简历不超过 2 分钟（无缓存情况）

### 集成测试
- [ ] 使用 `docs/jd/AI 业务探索.pdf` 作为 JD
- [ ] 使用 `docs/resume/` 中至少 5 份简历测试完整流程
- [ ] 验证上传→解析→匹配→生成题目→生成追问 全链路可用

---

## 📝 开发建议

1. **按依赖顺序开发**：ResumeExtract → Matching → QuestionGen → FollowUp
2. **先测试 AI 调用**：使用 Postman 或单元测试验证 AI 响应格式
3. **渐进式开发**：先实现基本功能，再优化错误处理和缓存
4. **及时验证**：每完成一个服务立即测试，避免积累问题
5. **保留日志**：记录所有 AI 请求和响应（可脱敏），便于调试 Prompt

---

## 🚀 完成标志

当以下场景能完整走通时，第三阶段验收通过：

1. 上传 JD（`AI 业务探索.pdf`）
2. 上传简历（`小王_深度实战版_v5_v6.pdf`）
3. 调用 `/api/analysis/extract/{resumeId}`，返回结构化简历数据
4. 调用 `/api/analysis/match`，返回 85 分匹配结果 + 详细理由
5. 调用 `/api/interview/questions/{resumeId}`，返回 10+ 道针对性面试题
6. 调用 `/api/interview/followup/{resumeId}`，返回 3-5 个深度追问
7. H2 数据库中能查询到完整的分析结果记录

**预计总时间：2-3 天**

---

**文档版本：** v1.0  
**创建日期：** 2026-06-11  
**对应开发引导：** DEVELOPMENT_GUIDE.md 第三阶段
