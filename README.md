# 智能简历解析与试题生成引擎

基于 AI 的简历解析与面试题生成系统，帮助 HR 和技术面试官快速筛选候选人并生成个性化面试题库。

## 功能特性

- **文档上传与解析** — 支持 PDF / DOCX 上传，自动提取纯文本
- **结构化提取** — AI 解析简历，输出姓名、技能、项目、教育等 JSON 结构
- **智能匹配打分** — JD 与简历多维度匹配（0–100 分）+ 推荐理由与优劣势分析
- **试题生成** — 根据 JD 和简历生成 10+ 道面试题，含考察点、难度、评分标准
- **追问模拟** — 针对简历模糊表述生成 3–5 个深度追问
- **数据管理** — 工作台、JD 管理、简历管理、分析结果四个视图，支持搜索、编辑、删除

## 技术栈

**后端**
- Java 17 + Spring Boot 3.2
- Spring AI 1.0.0-M5（LLM 集成，兼容 OpenAI 协议）
- Deepseek Chat（大模型）
- MyBatis-Plus 3.5.7（持久化框架）
- H2 Database（内嵌数据库，MySQL 兼容模式）
- Apache PDFBox 3.x + Apache POI（文档解析）
- Caffeine（本地缓存）
- dotenv-java（自动加载 `.env` 环境变量）

**前端**
- HTML5 + TailwindCSS + Alpine.js（无构建步骤，CDN 引入）
- 静态资源内嵌于 Spring Boot，也可独立打开 `frontend/` 目录

## 快速开始

### 1. 配置 API Key

复制环境变量模板并填入 Deepseek API Key：

```bash
cp .env.example .env
# 编辑 .env，填入 DEEPSEEK_API_KEY
```

也可通过系统环境变量设置：

```bash
# Windows
set DEEPSEEK_API_KEY=your_deepseek_api_key_here

# Linux / macOS
export DEEPSEEK_API_KEY=your_deepseek_api_key_here
```

应用启动时会自动加载项目根目录的 `.env` 文件（`ResumeAnalysisApp` 使用 dotenv-java）。

### 2. 运行后端

无需安装外部数据库，H2 会自动创建本地文件数据库。

```bash
cd backend
mvn spring-boot:run
```

> **注意**：数据库路径 `./data/resumedb` 相对于**进程工作目录**。建议在 `backend/` 目录下启动，数据文件将保存在 `backend/data/`；若从项目根目录启动，则保存在根目录 `data/`。

方式二：通过命令行参数启动jar包，来到根目录下进入cmd，执行命令，注意将java.exe路径换成你的java17路径

```bach
"D:\Develop\JDK\JDK17\bin\java.exe" -jar resume-analysis-1.0.0.jar --spring.ai.openai.api-key=你的deepseek api_key
```

### 3. 访问页面

**推荐方式**（同源访问，无跨域问题）：

```
http://localhost:8080/
```

也可直接打开 `frontend/index.html`（需确保后端已启动，且 `app.js` 中 `API_BASE` 指向正确地址）。

**其他入口：**
- H2 控制台：`http://localhost:8080/h2-console`（JDBC URL: `jdbc:h2:file:./data/resumedb`）

### 4. 测试样例

`docs/test-samples/` 目录提供了 8 份 JD 和 12 份简历文本样例，可用于手动测试上传与分析流程。详细说明见 [docs/test-samples/TEST_GUIDE.md](./docs/test-samples/TEST_GUIDE.md)。

## 系统架构

### 模块划分

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Alpine.js SPA)                      │
│   工作台 │ JD 管理 │ 简历管理 │ 分析结果  (Hash 路由)            │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP /api/*
┌────────────────────────────▼────────────────────────────────────┐
│                     Spring Boot 后端                               │
│                                                                    │
│  ┌──────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │  Controller  │  │     Service      │  │  Event / Listener │  │
│  │              │  │                  │  │                   │  │
│  │ UploadCtrl   │──│ DocumentParse    │  │ ResumeUploaded    │  │
│  │ AnalysisCtrl │──│ ResumeExtract    │──│ Event             │  │
│  │ InterviewCtrl│──│ Matching         │  │ ResumeExtract     │  │
│  │              │  │ QuestionGen      │  │ Listener (@Async) │  │
│  │              │  │ FollowUp         │  │                   │  │
│  └──────────────┘  └────────┬─────────┘  └───────────────────┘  │
│                             │                                      │
│  ┌──────────────────────────▼──────────────────────────────────┐  │
│  │  Repository (MyBatis-Plus)  +  Config                        │  │
│  │  AiConfig │ CacheConfig │ AsyncConfig │ WebConfig            │  │
│  └──────────────────────────┬──────────────────────────────────┘  │
└─────────────────────────────┼─────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐   ┌────────────┐   ┌──────────────┐
        │ H2 数据库 │   │ ./uploads/ │   │ Deepseek API │
        │ (文件型)  │   │ 简历文件   │   │ (LLM 调用)   │
        └──────────┘   └────────────┘   └──────────────┘
```

| 模块 | 职责 |
|------|------|
| **UploadController** | JD / 简历上传、文档解析、JD 与简历 CRUD |
| **AnalysisController** | 手动触发结构化提取、批量匹配评分、查询匹配结果 |
| **InterviewController** | 面试题生成、追问生成 |
| **DocumentParseService** | PDFBox / POI 提取文档纯文本 |
| **ResumeExtractService** | 调用 LLM 将简历文本转为结构化 JSON |
| **MatchingService** | 调用 LLM 进行 JD–简历多维度匹配评分 |
| **QuestionGenService** | 调用 LLM 生成个性化面试题 |
| **FollowUpService** | 调用 LLM 生成简历追问 |
| **ResumeExtractListener** | 简历上传后异步触发结构化提取 |

### 数据流向

```
用户上传 JD / 简历 (.pdf / .docx)
        │
        ▼
DocumentParseService ── 提取纯文本
        │
        ├─ JD 文本 ──────────────────────────► job_descriptions 表
        │
        └─ 简历文本 ──► resumes 表 (raw_text + file_path)
                │
                ▼
        ResumeUploadedEvent（Spring 事件）
                │
                ▼
        ResumeExtractListener (@Async 线程池)
                │
                ▼
        ResumeExtractService ── LLM (extract-resume.st)
                │
                ▼
        resumes.structured_data（结构化 JSON）
                │
        用户选择 JD + 简历，点击「开始分析」
                │
                ▼
        MatchingService ── 等待结构化数据就绪（轮询，最长 60s）
                │         └── LLM (match-score.st)
                ▼
        analysis_results 表（match_score / reason / dimensions）
                │
        前端并行请求
        ┌───────┴───────┐
        ▼               ▼
QuestionGenService   FollowUpService
(gen-questions.st)   (gen-followup.st)
        │               │
        └───────┬───────┘
                ▼
        analysis_results 表（questions / followup_questions）
                │
                ▼
        前端展示：匹配评分 + 面试题 + 追问
```

**关键行为说明：**

- 简历上传后，结构化提取在**后台异步**执行，上传接口立即返回，不阻塞用户操作。
- 匹配评分时，若结构化数据尚未就绪，`MatchingService` 会以 2 秒间隔轮询数据库，最长等待 60 秒。
- 面试题和追问在用户选中候选人后**并行生成**，结果写入 `analysis_results` 并缓存。
- 所有 LLM 调用结果通过 Caffeine 缓存（TTL 1 小时），相同输入在缓存有效期内不会重复调用 API。

## Prompt 设计思路

项目将 Prompt 模板独立存放在 `backend/src/main/resources/prompts/` 目录，通过字符串占位符注入动态内容，便于迭代调优而不改动业务代码。

| 模板文件 | 用途 | Temperature |
|----------|------|-------------|
| `extract-resume.st` | 简历结构化提取 | 0.3 |
| `match-score.st` | JD–简历匹配评分 | 0.3 |
| `gen-questions.st` | 面试题生成 | 0.7 |
| `gen-followup.st` | 追问生成 | 0.7 |

### 稳定输出的设计策略

**1. 角色设定 + 任务边界**

每个 Prompt 以明确的角色开头（如「专业的简历解析专家」「经验丰富的技术面试官」），限定模型只处理当前任务，减少无关输出。

**2. 内嵌 JSON Schema 示例**

Prompt 中直接给出完整的 JSON 结构示例，字段名、类型、取值范围一目了然。例如匹配评分模板规定了 `score`、`dimensions`、`recommendation`（`PROCEED` / `REJECT` / `MAYBE`）等字段，模型按示例填充而非自由发挥。

**3. 强制纯 JSON 输出**

所有模板末尾均强调：

> 只返回有效的 JSON 对象（不要使用 markdown 格式，不要添加解释说明）

同时在 Java 层实现了 `stripMarkdownFences()` 兜底处理——即使模型仍输出 ` ```json ... ``` ` 包裹的内容，也会在解析前自动剥除。

**4. 按任务类型调节 Temperature**

- **结构化提取 / 匹配评分**（`temperature = 0.3`）：需要格式稳定、评分一致，低温度减少随机性。
- **试题 / 追问生成**（`temperature = 0.7`）：需要多样性和创造性，适当提高温度。

**5. 业务规则内嵌 Prompt**

- 提取阶段：「只提取简历中实际存在的信息，缺失字段用空值」—— 防止模型编造经历。
- 匹配阶段：给出 85/70/50 分档的评分指南和四维评估框架（技能、年限、项目、行业）。
- 出题阶段：规定题型比例（技术 60% / 行为 30% / 场景 10%）和难度分布（简单 30% / 中等 50% / 困难 20%）。
- 追问阶段：列举 5 类需澄清的模式（模糊量化、不明确职责、技术深度空白等）。

**6. 服务端 JSON 校验**

解析 AI 响应后，各 Service 会校验必要字段是否存在。缺字段时记录告警日志但不中断流程（提取阶段）；JSON 格式错误时抛出明确异常提示用户重试。

## 难点与解决方案

### 1. LLM 输出格式不稳定

**挑战**：大模型经常不遵守「只返回 JSON」的指令，可能包裹 markdown 代码块、添加解释文字，或遗漏字段。

**解决方案**：
- Prompt 中内嵌完整 JSON Schema 并反复强调格式要求。
- 所有 AI Service 统一实现 `stripMarkdownFences()` 剥除 ` ``` ` 标记。
- 使用 Jackson 解析并校验必要字段，格式错误时返回明确错误信息。
- 结构化任务使用低 temperature（0.3）降低输出随机性。

### 2. 简历上传与 AI 提取的时序竞争

**挑战**：用户上传简历后立即点击「开始分析」，此时异步结构化提取可能尚未完成，匹配服务拿不到 `structured_data`。

**解决方案**：
- 上传接口通过 `ResumeUploadedEvent` + `@Async` 监听器在后台触发提取，上传响应不等待 AI 完成。
- `MatchingService.waitForStructuredData()` 以 2 秒间隔轮询数据库，最长等待 60 秒（可通过 `resume.extract.match-wait-timeout-ms` 配置）。
- 超时后返回明确错误提示，引导用户稍后重试。

### 3. 文档解析的边界情况

**挑战**：扫描件 PDF 无法提取文字；旧版 `.doc` 格式不支持；Word 表格内容可能丢失。

**解决方案**：
- 仅支持 `.pdf` 和 `.docx`，上传时按扩展名路由到 PDFBox / POI 解析器。
- 提取结果为空时抛出明确错误：「不支持扫描件/图片型 PDF」。
- Word 解析遍历段落文本（`XWPFParagraph`），覆盖大多数简历排版场景。

### 4. MyBatis-Plus 字段写入策略

**挑战**：`Resume.structuredData` 字段配置了 `FieldStrategy.IGNORED`，普通 `updateById` 无法写入该字段。

**解决方案**：
- 使用 `LambdaUpdateWrapper` 显式 `.set(Resume::getStructuredData, json)` 写入，绕过字段策略限制。
- 代码注释中记录了此陷阱，避免后续维护踩坑。

### 5. LLM 调用成本与响应延迟

**挑战**：每次分析涉及多次 LLM 调用（提取 + 匹配 + 出题 + 追问），重复操作会浪费 API 配额和时间。

**解决方案**：
- 基于 Caffeine 实现四级缓存：`resumeExtract`、`matchScore`、`questions`、`followup`。
- 缓存 TTL 1 小时、最大 1000 条，相同简历/JD 组合在有效期内直接返回缓存结果。
- 异步提取使用独立线程池（core=2, max=4），避免阻塞 Web 请求线程。

## 项目结构

```
resume-analysis-demo/
├── backend/                        # Spring Boot 后端
│   └── src/main/
│       ├── java/com/demo/resume/
│       │   ├── config/             # AiConfig, CacheConfig, AsyncConfig, WebConfig
│       │   ├── controller/         # Upload, Analysis, Interview
│       │   ├── service/            # DocumentParse, ResumeExtract, Matching, QuestionGen, FollowUp
│       │   ├── listener/           # ResumeExtractListener（异步事件监听）
│       │   ├── event/              # ResumeUploadedEvent
│       │   ├── model/
│       │   │   ├── entity/         # JobDescription, Resume, AnalysisResult
│       │   │   └── dto/            # 各类请求/响应 DTO
│       │   └── repository/         # MyBatis-Plus Mapper 接口
│       └── resources/
│           ├── application.yml
│           ├── schema.sql          # 建表脚本（H2 / MySQL 兼容）
│           ├── prompts/            # Prompt 模板（.st 文件）
│           └── static/             # 前端静态资源（与 frontend/ 同步）
├── frontend/                       # 前端源码（亦可独立打开）
│   ├── index.html
│   ├── js/app.js
│   └── css/style.css
├── docs/test-samples/              # 测试用 JD / 简历样例
├── uploads/                        # 上传简历文件存储目录
├── .env.example                    # 环境变量模板
├── DEVELOPMENT_GUIDE.md            # 开发引导文档
└── README.md
```

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/upload/jd` | 上传 JD 文件 |
| POST | `/api/upload/resumes` | 批量上传简历（触发异步提取） |
| GET/PUT/DELETE | `/api/upload/jd/{id}` | JD 查询 / 更新 / 删除 |
| GET/PUT/DELETE | `/api/upload/resumes/{id}` | 简历查询 / 更新 / 删除 |
| GET | `/api/upload/jd/list` | JD 列表 |
| GET | `/api/upload/resumes/list` | 简历列表 |
| POST | `/api/analysis/extract/{resumeId}` | 手动触发结构化提取 |
| POST | `/api/analysis/match` | 批量匹配评分 |
| GET | `/api/analysis/result/{resumeId}?jdId=` | 获取匹配结果 |
| POST | `/api/interview/questions/{resumeId}` | 生成面试题 |
| POST | `/api/interview/followup/{resumeId}` | 生成追问 |

## 数据库说明

项目使用 H2 内嵌数据库，以 MySQL 兼容模式运行，**无需安装任何外部数据库**。

- 数据文件存储在 `./data/resumedb.mv.db`（相对于进程工作目录）
- `schema.sql` 在启动时自动执行建表
- 三张核心表：`job_descriptions`、`resumes`、`analysis_results`
- 如需迁移到 MySQL，只需修改 `application.yml` 的 datasource 配置，`schema.sql` 无需改动

## 已知限制

- 仅支持 `.pdf` 和 `.docx`，不支持 `.doc` 及扫描件 PDF
- Word 解析仅提取段落文本，表格/页眉页脚内容可能丢失
- 简历原文编辑后，Caffeine 缓存不会自动失效，1 小时内可能返回旧结果
- 前端 `API_BASE` 硬编码为 `http://localhost:8080/api`，部署到其他地址需手动修改

## 开发指南

详细开发步骤、API 设计和分阶段计划请参考 [DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md)。

## License

MIT
