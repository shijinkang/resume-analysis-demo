# 第四阶段：前端开发与联调执行计划

## 当前状态评估

前端已有基础框架，实现了：
- JD 和简历的分区上传（带拖拽）
- 分析结果展示（候选人列表 + 详情面板 + 面试题）
- 与后端 API 的基本联调逻辑

**缺少的核心需求**：查看当前已有 JD 和简历（数据库中已存的记录），以及整体 UI 打磨。

---

## 需要新增的后端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/upload/jd/list` | 获取所有已上传的 JD 列表 |
| GET | `/api/upload/resumes/list` | 获取所有已上传的简历列表 |

---

## 前端改造方案

### 整体页面结构（三列布局 + 结果区）

```
+----------------------------------+
|        Header（顶部导航）          |
+----------+----------+------------+
|  JD 管理  |  简历管理  |  分析控制   |
|  已有列表  |  已有列表  |  选择 JD   |
|  上传新JD |  上传新简历|  选择简历   |
+----------+----------+  开始分析   |
|              分析结果区域            |
+------------------------------------+
```

**JD 管理（蓝色主题）**：已有 JD 卡片列表，点击选中，底部上传新 JD  
**简历管理（紫色主题）**：已有简历 checkbox 列表，可勾选多个，底部上传新简历  
**分析控制**：显示当前选中 JD 和已勾选简历数量，"开始 AI 分析"按钮

---

## 任务拆解

### Task 1：后端补充列表查询接口

**文件**：`backend/src/main/java/com/demo/resume/controller/UploadController.java`

1. 创建两个 DTO：`JdSummaryDTO` 和 `ResumeSummaryDTO`（包含 id、title/fileName、createdAt）
2. 新增两个 GET 接口：`/api/upload/jd/list` 和 `/api/upload/resumes/list`
3. 使用 `LambdaQueryWrapper` 按 `createdAt` 降序查询并返回

---

### Task 2：前端 app.js 重构

**文件**：`frontend/js/app.js`

**新增状态变量**：
- `jdList: []` - 已有 JD 列表
- `resumeList: []` - 已有简历列表
- `selectedJdId: null` - 当前选中的 JD ID
- `checkedResumeIds: []` - 勾选的简历 ID 数组
- `uploadingJd: false` 和 `uploadingResume: false` - 上传状态
- `successMsg: ''` - 成功提示消息

**新增方法**：
1. `init()` - 页面加载时调用 `loadJdList()` 和 `loadResumeList()`
2. `loadJdList()` 和 `loadResumeList()` - 从后端加载列表
3. `selectJd(id)` - 选中 JD
4. `toggleResume(id)` - 切换简历勾选状态
5. `isResumeChecked(id)` - 检查简历是否已勾选
6. `toggleAllResumes()` - 全选/取消全选
7. `handleNewJdUpload(event)` - 上传新 JD 后自动选中
8. `handleNewResumeUpload(event)` - 上传新简历后自动勾选
9. `showSuccess(msg)` - 显示成功提示

**修改方法**：
- `startAnalysis()` - 改为使用已选 `selectedJdId` 和 `checkedResumeIds`，而非先上传
- `reset()` - 重置交互状态，保留列表数据

---

### Task 3：前端 index.html 重构

**文件**：`frontend/index.html`

1. `<body>` 标签增加 `x-init="init()"`
2. Toast 区域增加绿色成功提示（`x-show="successMsg"`）
3. STEP 0 完整替换为三列布局：
   - **左列（JD 管理）**：已有 JD 列表 + 上传区
   - **中列（简历管理）**：已有简历 checkbox 列表 + 上传区
   - **右列（分析控制）**：已选 JD 摘要 + 已选简历摘要 + 开始分析按钮
4. 列表使用 `max-h-72 overflow-y-auto` 限制高度
5. 空状态显示友好提示："暂无 JD，请上传第一份" / "暂无简历，请上传"

---

### Task 4：样式优化

**文件**：`frontend/css/style.css`

新增：
- `.list-scroll` - 列表滚动条样式（宽度 4px，圆角）
- `.list-item-enter` - 列表项入场动画（slideDown）
- `.skeleton` - 骨架屏加载效果（可选）

---

### Task 5：联调验证

**测试场景清单**：

1. **初始加载**：页面自动加载 JD 和简历列表，有数据正常渲染，无数据显示空状态
2. **上传新 JD**：拖拽/点击上传 PDF/DOCX，成功后出现在列表并自动选中
3. **上传新简历**：批量上传，成功后出现在列表并自动勾选
4. **选择与勾选**：点击 JD 卡片高亮，勾选简历更新右侧摘要，全选按钮正常工作
5. **发起分析**：点击"开始 AI 分析"，显示 loading，完成后切换到结果展示区
6. **重置与复用**：点击"重新分析"返回，列表数据保留，可重新选择组合

---

## 预期工作量

| 任务 | 预估时间 | 难度 |
|------|---------|------|
| Task 1: 后端 API | 30 分钟 | 简单 |
| Task 2: 前端 JS 重构 | 2 小时 | 中等 |
| Task 3: 前端 HTML 重构 | 2 小时 | 中等 |
| Task 4: 样式优化 | 1 小时 | 简单 |
| Task 5: 联调测试 | 2 小时 | 中等 |
| **总计** | **7.5 小时** | **约 1 个工作日** |

---

## 成功标准

**功能完整性**：
- 可查看所有已上传的 JD 和简历
- 可上传新的 JD 和简历（上传后自动入列表并选中）
- 可选择已有数据发起分析
- 分析结果完整展示

**用户体验**：
- 页面简洁美观，色彩主题清晰（蓝色 JD / 紫色简历）
- 交互流畅，状态反馈及时
- 错误提示友好明确，加载状态可感知

---

## 关键设计决策

1. **维持轻量技术栈**：Alpine.js + 单 HTML，不引入路由框架
2. **上传与选用解耦**：上传是"入库"，分析是"使用已有数据"，同一份 JD 可多次复用
3. **数组管理多选**：Alpine.js 使用数组 + `indexOf` 实现勾选逻辑

---

**计划版本**：v1.0  
**创建时间**：2026-06-13  
**预计完成时间**：1 个工作日
