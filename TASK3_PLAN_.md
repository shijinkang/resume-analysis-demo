## 任务3：前端 index.html 重构 - 详细执行计划

根据 `PHASE4_PLAN.md` 的要求，任务3需要将现有的单一上传流程改造为**三列布局 + 结果区**的架构。以下是细粒度的执行步骤：

---

### 3.1 初始化逻辑注入（5分钟）

**目标**：在页面加载时自动获取已有的 JD 和简历列表

**操作**：
- 在 `<body>` 标签添加 `x-init="init()"` 属性
- 确保 Alpine.js 在页面加载时自动调用 `app.js` 中的 `init()` 方法

**验收**：打开开发者工具控制台，确认页面加载时触发了列表加载请求

---

### 3.2 Toast 提示区域增强（10分钟）

**目标**：添加绿色成功提示，与现有的错误提示（红色）和警告提示（黄色）形成完整的反馈体系

**操作**：
- 在现有 Toast 区域增加成功提示组件
- 使用 `x-show="successMsg"` 控制显示
- 样式采用绿色主题（`bg-green-50 border-green-500 text-green-800`）
- 添加自动消失动画（与现有 Toast 保持一致）

**验收**：成功上传文件后能看到绿色提示

---

### 3.3 删除旧的 STEP 0 上传区（2分钟）

**目标**：移除现有的简单上传区，为新的三列布局腾出空间

**操作**：
- 定位现有的 STEP 0 HTML 块（应该包含 JD 和简历的上传表单）
- 完整删除该区域

**验收**：旧的上传 UI 完全消失

---

### 3.4 创建三列布局容器（5分钟）

**目标**：建立页面主体的网格布局结构

**操作**：
- 创建一个 `<div class="grid grid-cols-3 gap-6 mb-8">` 容器
- 确保容器位于页面顶部，结果区域之前
- 使用 Tailwind 的 `gap-6` 保证列间距

**验收**：页面形成三列等宽布局

---

### 3.5 左列：JD 管理区域（30分钟）

#### 3.5.1 标题与主题色
- 添加 `<div class="bg-white rounded-lg shadow-md p-6 border-t-4 border-blue-500">`
- 标题：`<h3 class="text-lg font-semibold text-blue-700 mb-4">📋 JD 管理</h3>`

#### 3.5.2 已有 JD 列表
- 列表容器：`<div class="max-h-72 overflow-y-auto mb-4 space-y-2 list-scroll">`
- 使用 `x-for="jd in jdList"` 循环渲染
- 每个 JD 卡片：
  ```html
  <div class="p-3 border rounded cursor-pointer hover:bg-blue-50 transition"
       :class="selectedJdId === jd.id ? 'bg-blue-100 border-blue-500' : 'border-gray-200'"
       @click="selectJd(jd.id)">
  ```
- 显示字段：`jd.title`（加粗）、`jd.createdAt`（灰色小字）

#### 3.5.3 空状态
- 使用 `x-show="jdList.length === 0"` 显示空状态提示
- 文案：`<p class="text-gray-400 text-center py-8">暂无 JD，请上传第一份 📄</p>`

#### 3.5.4 上传新 JD 区域
- 分隔线：`<div class="border-t pt-4">`
- 小标题：`<p class="text-sm text-gray-600 mb-2">上传新 JD</p>`
- 文件输入框：
  ```html
  <input type="file" accept=".pdf,.docx"
         @change="handleNewJdUpload"
         :disabled="uploadingJd"
         class="block w-full text-sm">
  ```
- 上传中状态：显示 spinner 和"上传中..."文字

**验收**：能显示 JD 列表，点击高亮，上传功能正常

---

### 3.6 中列：简历管理区域（35分钟）

#### 3.6.1 标题与主题色
- 添加 `<div class="bg-white rounded-lg shadow-md p-6 border-t-4 border-purple-500">`
- 标题：`<h3 class="text-lg font-semibold text-purple-700 mb-4">👤 简历管理</h3>`

#### 3.6.2 全选操作
- 添加全选按钮：
  ```html
  <button @click="toggleAllResumes()"
          class="text-sm text-purple-600 hover:underline mb-2">
    <span x-text="checkedResumeIds.length === resumeList.length ? '取消全选' : '全选'"></span>
  </button>
  ```

#### 3.6.3 已有简历列表
- 列表容器：`<div class="max-h-72 overflow-y-auto mb-4 space-y-2 list-scroll">`
- 使用 `x-for="resume in resumeList"` 循环渲染
- 每个简历项使用 checkbox：
  ```html
  <label class="flex items-center p-3 border rounded hover:bg-purple-50 cursor-pointer transition"
         :class="isResumeChecked(resume.id) ? 'bg-purple-100 border-purple-500' : 'border-gray-200'">
    <input type="checkbox"
           :checked="isResumeChecked(resume.id)"
           @change="toggleResume(resume.id)"
           class="mr-3">
    <div>
      <p class="font-medium" x-text="resume.fileName"></p>
      <p class="text-xs text-gray-500" x-text="resume.createdAt"></p>
    </div>
  </label>
  ```

#### 3.6.4 空状态
- 使用 `x-show="resumeList.length === 0"`
- 文案：`<p class="text-gray-400 text-center py-8">暂无简历，请上传 📄</p>`

#### 3.6.5 上传新简历区域
- 分隔线：`<div class="border-t pt-4">`
- 小标题：`<p class="text-sm text-gray-600 mb-2">批量上传新简历</p>`
- 文件输入框：
  ```html
  <input type="file" accept=".pdf,.docx" multiple
         @change="handleNewResumeUpload"
         :disabled="uploadingResume"
         class="block w-full text-sm">
  ```
- 上传中状态：显示进度信息

**验收**：能勾选多个简历，全选/取消全选正常，批量上传功能正常

---

### 3.7 右列：分析控制区域（25分钟）

#### 3.7.1 标题与容器
- 添加 `<div class="bg-white rounded-lg shadow-md p-6 border-t-4 border-green-500">`
- 标题：`<h3 class="text-lg font-semibold text-green-700 mb-4">🚀 开始分析</h3>`

#### 3.7.2 已选 JD 摘要
- 条件显示：`x-show="selectedJdId"`
- 内容：
  ```html
  <div class="mb-4 p-3 bg-blue-50 rounded">
    <p class="text-sm text-gray-600">已选 JD</p>
    <p class="font-medium text-blue-700" x-text="jdList.find(j => j.id === selectedJdId)?.title"></p>
  </div>
  ```
- 未选中提示：
  ```html
  <div x-show="!selectedJdId" class="mb-4 p-3 bg-gray-50 rounded">
    <p class="text-sm text-gray-500">请先选择一个 JD ⬅️</p>
  </div>
  ```

#### 3.7.3 已选简历摘要
- 条件显示：`x-show="checkedResumeIds.length > 0"`
- 内容：
  ```html
  <div class="mb-4 p-3 bg-purple-50 rounded">
    <p class="text-sm text-gray-600">已选简历</p>
    <p class="font-medium text-purple-700">
      <span x-text="checkedResumeIds.length"></span> 份简历
    </p>
  </div>
  ```
- 未选中提示：
  ```html
  <div x-show="checkedResumeIds.length === 0" class="mb-4 p-3 bg-gray-50 rounded">
    <p class="text-sm text-gray-500">请选择至少一份简历 ⬅️</p>
  </div>
  ```

#### 3.7.4 开始分析按钮
- 大按钮：
  ```html
  <button @click="startAnalysis()"
          :disabled="!selectedJdId || checkedResumeIds.length === 0 || analyzing"
          class="w-full py-3 px-6 rounded-lg font-semibold text-white transition"
          :class="(selectedJdId && checkedResumeIds.length > 0 && !analyzing) 
                  ? 'bg-green-600 hover:bg-green-700' 
                  : 'bg-gray-300 cursor-not-allowed'">
    <span x-show="!analyzing">🎯 开始 AI 分析</span>
    <span x-show="analyzing">
      <svg class="animate-spin h-5 w-5 inline mr-2">...</svg>
      分析中...
    </span>
  </button>
  ```

**验收**：显示当前选择状态，只有选择了 JD 和简历后按钮才可点击

---

### 3.8 响应式优化（10分钟）

**目标**：确保在小屏幕上也能正常使用

**操作**：
- 将 `grid-cols-3` 改为响应式：`grid-cols-1 md:grid-cols-3`
- 确保移动端每列垂直堆叠
- 测试在不同屏幕尺寸下的显示效果

**验收**：在手机、平板、桌面端都能正常显示

---

### 3.9 无障碍优化（8分钟）

**目标**：提升页面可访问性

**操作**：
- 为所有交互元素添加 `aria-label`
- 确保键盘可以导航所有功能
- 为 checkbox 和 radio 添加清晰的 label 关联
- 为加载状态添加 `aria-busy` 属性

**验收**：使用 Tab 键能顺序访问所有交互元素

---

### 3.10 测试验证（15分钟）

**测试清单**：
1. 页面加载时自动获取 JD 和简历列表 ✓
2. 空状态提示正确显示 ✓
3. 选择 JD，右侧摘要实时更新 ✓
4. 勾选简历，右侧数量实时更新 ✓
5. 上传新 JD 后，列表自动刷新并选中 ✓
6. 批量上传简历后，列表自动刷新并全部勾选 ✓
7. 点击"开始分析"，传递正确的 ID 到后端 ✓
8. 成功/失败提示正确显示 ✓
9. 分析完成后，点击"重新分析"能回到选择界面 ✓
10. 列表滚动条样式美观 ✓
