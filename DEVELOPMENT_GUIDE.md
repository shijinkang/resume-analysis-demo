# 智能简历解析与试题生成引擎 - 开发引导文档

## 项目概述

这是一个基于 AI 的简历解析与面试题生成系统，用于快速筛选候选人并生成个性化面试题库。

**核心功能：**
- 📄 结构化提取：AI 解析简历并提取关键信息
- 🎯 智能匹配打分：JD 与简历匹配度评分（0-100）+ 精准理由
- 📝 试题生成：生成 10+ 道面试题，包含考察点、难度、评分标准
- 🔍 追问模拟：针对简历模糊点生成 3-5 个深度追问

---

## 技术栈

### 后端
- **Java 17** + Spring Boot 3.2.x
- **Spring AI 1.0.0-M5** - LLM 集成框架（兼容 OpenAI 协议）
- **Deepseek Chat** - 大模型（通过 `DEEPSEEK_API_KEY` 环境变量配置）
- **MyBatis-Plus 3.5.7** - 持久化框架
- **H2 Database** - 内嵌数据库，MySQL 兼容模式（无需安装外部数据库）
- **Apache PDFBox 3.x** - PDF 文档解析
- **Apache POI** - Word 文档解析
- **Caffeine** - 本地缓存

### 前端
- **HTML5 + TailwindCSS** - 现代化 UI
- **Alpine.js** - 轻量级交互框架

---

## 系统架构

```
┌─────────────┐
│  前端页面    │ (HTML + Alpine.js)
└──────┬──────┘
       │ HTTP
       ▼
┌─────────────────────────────────┐
│     Spring Boot 后端             │
│  ┌─────────────────────────┐   │
│  │  Controller 层          │   │
│  │  - UploadController     │   │
│  │  - AnalysisController   │   │
│  │  - InterviewController  │   │
│  └──────────┬──────────────┘   │
│             │                   │
│  ┌──────────▼──────────────┐   │
│  │  Service 层             │   │
│  │  - DocumentParse        │   │
│  │  - ResumeExtract        │   │
│  │  - Matching             │   │
│  │  - QuestionGen          │   │
│  │  - FollowUp             │   │
│  └──────────┬──────────────┘   │
│             │                   │
│  ┌──────────▼──────────────┐   │
│  │  Mapper 层 (MyBatis-Plus)│   │
│  └──────────┬──────────────┘   │
└─────────────┼──────────────────┘
              │
       ┌──────┴───────┐
       ▼              ▼
┌─────────────┐  ┌──────────┐
│ H2 Database │  │ LLM API  │
│ (内嵌/文件) │  │ Deepseek │
└─────────────┘  └──────────┘
```

---

## 数据流

```
1. 用户上传 JD + 简历（PDF/Word）
         │
         ▼
2. DocumentParseService 提取文档文本
         │
         ▼
3. ResumeExtractService 调用 AI 结构化提取 → 存入 H2
         │
         ▼
4. MatchingService 计算匹配度评分（AI 评分 0-100 + 理由）
         │
         ▼
5. QuestionGenService 生成面试题（10+）→ 存入 H2
         │
         ▼
6. FollowUpService 生成追问（3-5 个）→ 存入 H2
         │
         ▼
7. 返回完整分析结果给前端
```

---

## API 设计

### 1. 上传模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/upload/jd` | 上传 JD 文件（PDF/Word） |
| POST | `/api/upload/resumes` | 批量上传简历文件 |

### 2. 解析与匹配模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/analysis/extract/{resumeId}` | 触发简历结构化提取 |
| POST | `/api/analysis/match` | JD 与简历匹配评分 |
| GET  | `/api/analysis/result/{resumeId}` | 获取完整分析结果 |

**匹配响应示例：**
```json
{
  "resumeId": "456",
  "score": 85,
  "reason": "候选人具备 5 年 Java 开发经验，技术栈与岗位需求高度匹配。",
  "dimensions": {
    "技能匹配": 90,
    "工作年限": 85,
    "项目相关度": 80,
    "行业背景": 80
  },
  "recommendation": "PROCEED"
}
```

### 3. 面试题生成模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/interview/questions/{resumeId}` | 生成面试题（10+ 道） |
| POST | `/api/interview/followup/{resumeId}` | 生成追问问题（3-5 个） |

---

## 数据库设计

使用 H2 内嵌数据库（MySQL 兼容模式），`schema.sql` 在启动时自动执行。

### job_descriptions
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| title | VARCHAR(255) | 职位名称 |
| content | TEXT | JD 完整内容 |
| requirements | TEXT | 技能要求 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### resumes
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| file_name | VARCHAR(255) | 文件名 |
| file_path | VARCHAR(500) | 存储路径 |
| raw_text | TEXT | 提取的原始文本 |
| structured_data | TEXT | AI 结构化结果（JSON 字符串） |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### analysis_results
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| jd_id | BIGINT | 关联 JD |
| resume_id | BIGINT | 关联简历 |
| match_score | INT | 匹配分数 0-100 |
| match_reason | TEXT | 匹配理由 |
| match_dimensions | TEXT | 各维度分数（JSON 字符串） |
| questions | TEXT | 面试题列表（JSON 字符串） |
| followup_questions | TEXT | 追问列表（JSON 字符串） |
| recommendation | VARCHAR(50) | PROCEED / REJECT / MAYBE |

---

## MyBatis-Plus 使用说明

### Mapper 接口
所有 Repository 继承 `BaseMapper<T>`，内置 CRUD 方法，无需手写 SQL：

```java
// 直接注入使用
@Autowired
private ResumeRepo resumeRepo;

// 基础操作
resumeRepo.insert(resume);
resumeRepo.selectById(id);
resumeRepo.updateById(resume);

// 条件查询
LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Resume::getId, resumeId);
resumeRepo.selectOne(wrapper);
```

### 自动填充
`createdAt` 和 `updatedAt` 字段通过 `MyMetaObjectHandler` 自动填充，无需手动设置。

### 迁移到 MySQL
只需修改 `application.yml` 的 datasource 配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/resume_db?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

并在 `pom.xml` 中替换 H2 依赖为：

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## Prompt 设计策略

### 1. 结构化提取 (`extract-resume.st`)
将简历文本转换为标准 JSON，包含：姓名、工作年限、技能栈、项目经历、教育背景。

### 2. 匹配评分 (`match-score.st`)
多维度加权评分：技能匹配（30%）、工作年限（20%）、项目相关度（30%）、行业背景（20%），输出分数 + 理由 + 推进建议。

### 3. 试题生成 (`gen-questions.st`)
每道题包含：`question`、`category`（TECHNICAL/BEHAVIORAL/SCENARIO）、`difficulty`（1-5）、`keyPoints`、`scoringCriteria`，至少生成 10 道。

### 4. 追问生成 (`gen-followup.st`)
识别简历中的模糊描述并生成针对性追问，包含追问背景和追问原因。

---

## 开发步骤

### 第一阶段：环境搭建（预计 0.5 天）

- [ ] 配置 `DEEPSEEK_API_KEY` 环境变量
- [ ] `mvn spring-boot:run` 验证项目启动
- [ ] 访问 `http://localhost:8080/h2-console` 确认数据库初始化正常
- [ ] 打开 `frontend/index.html` 确认前端页面可访问

**验收：** 项目能启动，H2 控制台能看到三张表

---

### 第二阶段：文档解析（预计 1 天）

- [ ] 完善 `UploadController`：接收文件 → 调用 `DocumentParseService` → 存入数据库
- [ ] 补充文件类型校验（只允许 .pdf / .docx）
- [ ] 补充文件大小限制
- [ ] 本地测试：上传一份 PDF 和一份 Word，确认文本提取正确

**验收：** 能成功上传并解析 PDF/Word，数据存入 H2

---

### 第三阶段：AI 核心能力（预计 2-3 天）

- [ ] 完善 `ResumeExtractService`：调用 LLM + Prompt 模板，解析返回的 JSON
- [ ] 完善 `MatchingService`：多维度评分，返回结构化结果
- [ ] 完善 `QuestionGenService`：生成 10+ 道面试题
- [ ] 完善 `FollowUpService`：生成 3-5 个追问
- [ ] Caffeine 缓存配置，避免重复调用 API

**验收：** 对同一份简历，结构化提取准确，匹配评分有理有据，题目有针对性

---

### 第四阶段：前端开发与联调（预计 1 天）

- [ ] 完善 `frontend/index.html`：完整的上传 + 结果展示 UI
- [ ] 前后端联调（CORS 已配置）
- [ ] 错误处理与加载状态

**验收：** 完整流程可在浏览器中走通

---

### 第五阶段：收尾（0.5 天）

- [ ] 端到端测试
- [ ] 异常场景处理（文件解析失败、AI 调用超时等）
- [ ] 补充使用说明

---

## 配置说明

### 环境变量

| 变量名 | 说明 | 必填 |
|--------|------|------|
| `DEEPSEEK_API_KEY` | Deepseek API Key | 是 |

### application.yml 关键配置

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/resumedb;MODE=MySQL  # H2 文件数据库
  h2:
    console:
      enabled: true    # 开启 H2 Web 控制台
  ai:
    openai:
      base-url: https://api.deepseek.com           # Deepseek 兼容 OpenAI 协议
      model: deepseek-chat

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true             # 自动驼峰映射
```

---

## 注意事项

1. **API Key 安全**：不要将 Key 硬编码在代码中，使用环境变量
2. **缓存策略**：相同简历提取结果缓存 1 小时，避免重复调用 LLM
3. **文件管理**：上传文件存储在 `./uploads/` 目录，该目录已加入 `.gitignore`
4. **H2 数据持久化**：数据文件在 `./data/resumedb.mv.db`，重启后数据不丢失
5. **LLM 超时**：Deepseek API 调用建议设置 30 秒超时，避免长时间阻塞

---

## 常见问题

**Q: H2 控制台登录 JDBC URL 填什么？**
`jdbc:h2:file:./data/resumedb`，用户名 `sa`，密码留空。

**Q: 如何切换到 MySQL？**
参考上方"迁移到 MySQL"章节，只需改 `application.yml` 的 datasource 配置和 `pom.xml` 的驱动依赖，`schema.sql` 建表语句兼容 MySQL，无需修改。

**Q: Prompt 效果不好怎么优化？**
直接修改 `src/main/resources/prompts/` 目录下对应的 `.st` 文件，无需重新编译。

---

**最后更新：** 2026-06-11
**版本：** v1.1（移除 pgvector，改用 H2 + MyBatis-Plus）
