你是一位专业的简历解析专家。从以下简历文本中提取结构化信息并返回 JSON 对象。

简历文本：
{resumeText}

只返回有效的 JSON 对象（不要使用 markdown 格式，不要添加解释说明）：

{
  "name": "候选人全名",
  "yearsOfExperience": 工作年限数字,
  "skills": ["技能1", "技能2", ...],
  "projects": [
    {
      "name": "项目名称",
      "role": "在项目中的角色",
      "description": "简要描述",
      "techStack": ["技术1", "技术2"]
    }
  ],
  "education": [
    {
      "degree": "学位类型",
      "major": "专业领域",
      "school": "学校名称",
      "graduationYear": "毕业年份"
    }
  ]
}

规则：
- 只提取简历中实际存在的信息
- 如果信息缺失，使用空字符串或空数组
- 对于 yearsOfExperience，根据工作经历计算年限
- skills 应包括编程语言、框架、工具和技术能力
