# ALevel 题库接入方案

## 1. 目标

将 OxfordAQA A-Level 题库接入当前模拟考试系统。

当前阶段仅记录方案，不落代码。

## 2. 已确认边界

### 2.1 考试范围

- 当前讨论对象不是 SAT。
- 当前讨论对象是 OxfordAQA A-Level 题库。
- 新题库表统一使用 `alevel_` 前缀。

### 2.2 与雅思题库的关系

- A-Level 题库表与当前雅思题库表拆开。
- 不继续复用雅思题库表承载 A-Level 题目。

### 2.3 与统一作答功能的关系

- 题库表拆开。
- 作答记录类功能尽量统一兼容：
  - 历史记录
  - 错题本
  - 收藏夹
  - 做题进度
  - 提交记录
- 推荐通过统一引用层兼容，不让统一作答表直接绑定雅思题库主键。

### 2.4 导入阶段

- 暂时不做人工审核页面。
- 题库导入采用自动解析后直接入库。
- 但必须保留解析留痕，便于后续排错、重导和比对。

### 2.5 作图题

- 暂时不做在线作图。
- 作图题方案改为：
  - 学生点击按钮下载题目图片
  - 学生线下作图
  - 学生上传作答图片或 PDF

### 2.6 显示答案

- A-Level 的显示答案不能只显示一个标准答案。
- 必须展示对应 `mark-scheme` 文件中的得分点。
- 显示答案应包含：
  - 参考答案
  - 得分点列表
  - 每个得分点分值
  - allow / ignore / comments 等说明

## 3. 总体架构

推荐结构如下：

```text
原始 zip/pdf
-> 原文件持久化
-> 自动识别 question-paper / mark-scheme / insert / exam-report
-> PDF 解析
-> 结构化中间结果
-> 直接写入 alevel_* 正式题库表
-> 前端通过适配层渲染到现有模拟考试界面
```

## 4. 题库表设计

推荐新增以下题库主表：

- `alevel_paper`
- `alevel_module`
- `alevel_question`
- `alevel_question_option`
- `alevel_question_answer`
- `alevel_content_block`
- `alevel_asset`
- `alevel_source_file`

### 4.1 alevel_paper

存一份 A-Level 试卷。

建议字段：

- `alevel_paper_id`
- `paper_code`
- `paper_name`
- `exam_board`
- `qualification`
- `subject_code`
- `subject_name`
- `unit_code`
- `unit_name`
- `exam_session`
- `duration_seconds`
- `total_score`
- `status`
- `remark`
- `create_time`
- `update_time`
- `delete_flag`

### 4.2 alevel_module

存试卷下的 section 或模块。

建议字段：

- `alevel_module_id`
- `alevel_paper_id`
- `module_code`
- `module_name`
- `module_type`
- `instructions_html`
- `instructions_text`
- `sort_order`
- `status`
- `create_time`
- `update_time`
- `delete_flag`

### 4.3 alevel_question

必须支持大题和小问。

建议字段：

- `alevel_question_id`
- `alevel_paper_id`
- `alevel_module_id`
- `parent_question_id`
- `question_code`
- `question_no_display`
- `mark_scheme_question_key`
- `question_type`
- `response_mode`
- `auto_gradable`
- `stem_html`
- `stem_text`
- `content_html`
- `content_text`
- `max_score`
- `source_page_no`
- `source_bbox_json`
- `sort_order`
- `status`
- `create_time`
- `update_time`
- `delete_flag`

说明：

- `question_no_display` 用于展示，例如 `01.1`、`3(b)(ii)`。
- `mark_scheme_question_key` 用于和 mark-scheme 小问精确对齐。
- `response_mode` 示例：
  - `radio`
  - `checkbox`
  - `input`
  - `textarea`
  - `upload_image`
  - `manual_only`

### 4.4 alevel_question_option

用于选择题的题目级选项。

建议字段：

- `alevel_question_option_id`
- `alevel_question_id`
- `option_key`
- `option_html`
- `option_text`
- `structure_json`
- `sort_order`
- `status`
- `create_time`
- `update_time`
- `delete_flag`

### 4.5 alevel_question_answer

用于存正确答案、得分点、评分说明。

建议字段：

- `alevel_question_answer_id`
- `alevel_question_id`
- `answer_raw`
- `answer_json`
- `mark_scheme_json`
- `mark_scheme_excerpt_text`
- `grading_mode`
- `status`
- `create_time`
- `update_time`
- `delete_flag`

其中：

- `answer_json` 存结构化答案。
- `mark_scheme_json` 存结构化得分点。
- `mark_scheme_excerpt_text` 存无法完全结构化时的原始评分文本片段。

### 4.6 alevel_content_block

用于存表格、公式、图示、段落等内容块。

建议字段：

- `alevel_content_block_id`
- `owner_type`
- `owner_id`
- `block_type`
- `content_html`
- `content_text`
- `structure_json`
- `source_file_id`
- `source_page_no`
- `source_bbox_json`
- `sort_order`
- `status`
- `create_time`
- `update_time`
- `delete_flag`

说明：

- `owner_type` 可取：
  - `paper`
  - `module`
  - `question`
  - `option`
  - `answer`
- `block_type` 可取：
  - `paragraph`
  - `table`
  - `image`
  - `formula`
  - `diagram`
  - `source_extract`

### 4.7 alevel_asset

用于存题图、附件、下载文件等资源。

建议字段：

- `alevel_asset_id`
- `alevel_paper_id`
- `alevel_module_id`
- `alevel_question_id`
- `asset_type`
- `asset_role`
- `asset_name`
- `source_path`
- `storage_path`
- `asset_url`
- `source_page_no`
- `source_bbox_json`
- `sort_order`
- `status`
- `create_time`
- `update_time`
- `delete_flag`

### 4.8 alevel_source_file

用于保存原始 question-paper / mark-scheme / insert / exam-report。

建议字段：

- `alevel_source_file_id`
- `alevel_paper_id`
- `source_file_type`
- `source_file_name`
- `source_file_hash`
- `storage_path`
- `asset_url`
- `page_count`
- `parse_status`
- `parse_result_json`
- `parse_warning_json`
- `import_version`
- `is_verified`
- `error_message`
- `create_time`
- `update_time`
- `delete_flag`

## 5. 统一作答兼容方案

题库表拆开，但统一作答功能继续复用当前模拟考试域。

推荐增加统一引用层：

- `mockexam_paper_ref`
- `mockexam_question_ref`

用途：

- `mockexam_paper_ref` 统一指向一份可练习试卷。
- `mockexam_question_ref` 统一指向一题可收藏、可错题统计、可做题记录的题目。

统一作答记录继续使用或扩展现有：

- 历史记录
- 做题进度
- 收藏夹
- 错题本
- 提交记录

但这些表后续应尽量通过 `mockexam_paper_ref` / `mockexam_question_ref` 关联，而不是直接硬绑雅思题库主键。

## 6. 表格存储方案

不是图片的表格，不截图存。

统一采用三份表达：

- `content_html`
- `structure_json`
- `content_text`

示例：

- `content_html` 用于前端直接渲染 `<table>`
- `structure_json` 用于结构化处理
- `content_text` 用于检索、翻译、AI 分析

## 7. 作图题方案

第一期不做在线作图。

作图题按以下方式处理：

- `alevel_question.question_type = drawing`
- `alevel_question.response_mode = upload_image`
- 在 `alevel_asset` 中保存题图或下载模板
- 学生端提供：
  - `下载题图`
  - `上传作答图片/PDF`

统一作答域建议补：

- `mockexam_answer_asset`

用于保存学生上传的作答图片或 PDF。

## 8. mark-scheme 存储与展示方案

显示答案时，要以 `mark-scheme` 得分点为主。

推荐 `mark_scheme_json` 结构：

```json
{
  "display_mode": "mark_scheme",
  "total_marks": 2,
  "marking_points": [
    {
      "point_code": "P1",
      "mark_value": 1,
      "guidance_text": "Cl2 + 2Br- -> 2Cl- + Br2",
      "comments_text": "ignore state symbols; allow multiples"
    },
    {
      "point_code": "P2",
      "mark_value": 1,
      "guidance_text": "Goes yellow/orange/brown (solution)",
      "comments_text": ""
    }
  ]
}
```

如果自动结构化失败，则降级保存原始评分片段：

```json
{
  "display_mode": "raw_excerpt",
  "raw_excerpt_text": "Question Marking guidance Mark Comments 01.3 ..."
}
```

## 9. 当前不做的内容

本轮明确暂不做：

- 人工审核页面
- 在线作图画布
- 作图题自动评分

## 10. 推荐实施步骤

### 第一步

先落表结构和基础模型：

- `alevel_*` 表
- `mockexam_paper_ref`
- `mockexam_question_ref`
- `mockexam_answer_asset`

### 第二步

落原始文件保存和自动解析入口：

- 上传 zip/pdf
- 分类识别文件类型
- 写入 `alevel_source_file`

### 第三步

落 PDF 解析和入库：

- question-paper 解析为题目结构
- mark-scheme 解析为得分点
- insert 解析为材料资源
- 自动写入 `alevel_*`

### 第四步

落统一引用层映射：

- `alevel_paper -> mockexam_paper_ref`
- `alevel_question -> mockexam_question_ref`

### 第五步

落前端适配：

- A-Level payload 转现有 mockexam 页面可渲染结构
- 作图题展示下载与上传入口
- 显示答案展示 mark-scheme 得分点

### 第六步

落统一作答记录兼容：

- 进度
- 收藏
- 错题本
- 历史记录
- 提交记录

## 11. 当前状态

当前文档仅为方案沉淀。

后续执行时，应按步骤逐项推进，不要一次性并行改动全部链路。
