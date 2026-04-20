"""
正式档案表单读取服务。

中文注释：
1. 这一层直接读取正式业务表，不再依赖 ai_chat_profile_results.profile_json 回显。
2. 返回给前端的是“按表分组”的档案快照，以及对应的表单元数据。
3. 前端据此渲染真实表单，用户修改后再走正式业务表更新与六维图重算。
"""

from __future__ import annotations

from datetime import date
from typing import Any

from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from backend.services.business_profile_persistence_service import MULTI_ROW_TABLES
from backend.services.business_profile_persistence_service import SINGLE_ROW_TABLES
from backend.services.code_resolution_service import AL_LEVEL_SUBJECT_CONFIG
from backend.services.code_resolution_service import AP_COURSE_CONFIG
from backend.services.code_resolution_service import CHINESE_HIGH_SCHOOL_SUBJECT_CONFIG
from backend.services.code_resolution_service import COUNTRY_CONFIG
from backend.services.code_resolution_service import IB_SUBJECT_CONFIG
from backend.services.code_resolution_service import MAJOR_CONFIG
from backend.services.code_resolution_service import US_HIGH_SCHOOL_COURSE_CONFIG


TABLE_ORDER = [
    "student_basic_info",
    "student_basic_info_curriculum_system",
    "student_academic",
    "student_academic_us_high_school_profile",
    "student_academic_us_high_school_course",
    "student_academic_other_curriculum_profile",
    "student_academic_a_level_profile",
    "student_academic_a_level_subject",
    "student_academic_ap_profile",
    "student_academic_ap_course",
    "student_academic_ib_profile",
    "student_academic_ib_subject",
    "student_academic_chinese_high_school_profile",
    "student_academic_chinese_high_school_subject",
    "student_language_ielts",
    "student_language_toefl_ibt",
    "student_language_toefl_essentials",
    "student_language_det",
    "student_language_pte",
    "student_language_languagecert",
    "student_language_cambridge",
    "student_language_other",
    "student_standardized_test_records",
    "student_competition_entries",
    "student_activity_entries",
    "student_project_entries",
]

TABLE_LABELS = {
    "student_basic_info": "学生基本信息",
    "student_basic_info_curriculum_system": "课程体系",
    "student_academic": "学术信息",
    "student_academic_us_high_school_profile": "美高配置",
    "student_academic_us_high_school_course": "美高逐门课成绩",
    "student_academic_other_curriculum_profile": "其他课程体系配置",
    "student_academic_a_level_profile": "A-Level 配置",
    "student_academic_a_level_subject": "A-Level 科目成绩",
    "student_academic_ap_profile": "AP 配置",
    "student_academic_ap_course": "AP 课程成绩",
    "student_academic_ib_profile": "IB 配置",
    "student_academic_ib_subject": "IB 科目成绩",
    "student_academic_chinese_high_school_profile": "普高配置",
    "student_academic_chinese_high_school_subject": "普高科目成绩",
    "student_language_ielts": "雅思",
    "student_language_toefl_ibt": "托福 iBT",
    "student_language_toefl_essentials": "托福 Essentials",
    "student_language_det": "多邻国",
    "student_language_pte": "PTE",
    "student_language_languagecert": "LanguageCert",
    "student_language_cambridge": "剑桥英语",
    "student_language_other": "其他语言考试",
    "student_standardized_test_records": "标化考试",
    "student_competition_entries": "竞赛",
    "student_activity_entries": "活动",
    "student_project_entries": "项目",
}

FIELD_LABELS = {
    "MAJ_INTEREST_TEXT": "目标专业原始文本",
    "student_id": "学生 ID",
    "schema_version": "架构版本",
    "profile_type": "档案类型",
    "current_grade": "当前年级",
    "graduation_year": "毕业年份",
    "target_entry_term": "目标入学季",
    "CTRY_CODE_VAL": "目标国家",
    "MAJ_CODE_VAL": "目标专业",
    "curriculum_system_notes": "课程体系补充说明",
    "curriculum_system_code": "课程体系",
    "is_primary": "是否主课程体系",
    "student_academic_id": "学术主表 ID",
    "school_name": "学校名称",
    "school_city": "所在城市",
    "school_type_code": "学校类型",
    "school_type_notes": "学校类型补充说明",
    "grade_size": "年级人数",
    "total_students": "学校总人数",
    "rank_number": "排名名次",
    "rank_percentile": "排名百分位",
    "rank_scope_code": "排名范围",
    "rank_evidence_level_code": "排名证据等级",
    "rank_evidence_notes": "排名证据说明",
    "other_curriculum_notes": "其他课程体系说明",
    "curriculum_combination_notes": "混合课程体系说明",
    "curriculum_scope_code": "课程体系范围",
    "best_n_subjects_for_scoring": "用于评分的最佳科目数",
    "include_further_math_bonus": "是否计入高数加分",
    "al_subject_id": "A-Level 科目",
    "stage_code": "阶段",
    "grade_code": "成绩等级",
    "is_predicted": "是否预估",
    "board_code": "考试局",
    "session_code": "考试场次",
    "exam_series": "考试批次",
    "school_gpa": "校内 GPA",
    "gpa_scale": "GPA 满分",
    "is_weighted": "是否加权",
    "class_rank_available": "是否有班级排名",
    "honors_count": "荣誉课程数量",
    "ap_count": "AP 数量",
    "max_ap_offered_by_school": "学校提供 AP 最大数量",
    "best_n_ap_for_scoring": "用于评分的最佳 AP 门数",
    "course_load_rigor_notes": "课程强度说明",
    "student_us_high_school_course_record_id": "美高课程记录 ID",
    "school_year_label": "学年/年级标签",
    "term_code": "学期",
    "us_high_school_course_id": "美高课程",
    "course_name_text": "课程名称",
    "course_category_code": "课程类别",
    "course_level_code": "课程级别",
    "grade_letter_code": "字母成绩",
    "grade_percent": "百分制成绩",
    "credit_earned": "学分",
    "ap_course_id": "AP 课程",
    "score": "分数",
    "year_taken": "考试年份",
    "ib_total_predicted": "IB 总预估分",
    "tok_grade_code": "TOK 等级",
    "ee_grade_code": "EE 等级",
    "hl_weight_multiplier": "HL 加权系数",
    "ib_subject_id": "IB 科目",
    "level_code": "级别",
    "grading_system_code": "评分体系",
    "average_score_100": "百分制平均分",
    "gpa": "GPA",
    "best_subjects_for_scoring_rule": "最佳科目评分规则",
    "chs_subject_id": "普高科目",
    "score_100": "百分制分数",
    "student_language_id": "语言主表 ID",
    "best_test_type_code": "最佳语言考试类型",
    "best_score_status_code": "最佳成绩状态",
    "best_test_date": "最佳考试日期",
    "best_language_ability_index_100": "语言能力指数",
    "overall_cefr_level_code": "综合 CEFR 等级",
    "use_best_single_test": "是否使用最佳单次成绩",
    "prefer_scored_over_estimated": "是否优先已出分",
    "language_index_method_notes": "语言指数说明",
    "status_code": "成绩状态",
    "test_date": "考试日期",
    "overall_score": "总分",
    "reading_score": "阅读分",
    "listening_score": "听力分",
    "speaking_score": "口语分",
    "writing_score": "写作分",
    "estimated_total": "预估总分",
    "evidence_level_code": "证据等级",
    "normalized_index_100": "标准化指数",
    "is_best_score": "是否最佳成绩",
    "total_score": "总分",
    "core_score_1_12": "核心总分",
    "literacy_1_12": "读写能力分",
    "conversation_1_12": "交流能力分",
    "comprehension_1_12": "理解能力分",
    "literacy_score": "Literacy 分",
    "comprehension_score": "Comprehension 分",
    "conversation_score": "Conversation 分",
    "production_score": "Production 分",
    "exam_name": "考试名称",
    "cefr_level_code": "CEFR 等级",
    "use_of_english_score": "Use of English 分",
    "test_name": "考试名称",
    "score_total": "总分",
    "band_or_scale_desc": "分制说明",
    "score_breakdown_json": "分项成绩 JSON",
    "student_standardized_test_id": "标化主表 ID",
    "is_applicable": "是否适用",
    "not_applicable_reason": "不适用原因",
    "best_test_type": "最佳标化考试类型",
    "best_total_score": "最佳总分",
    "test_type": "考试类型",
    "status": "考试状态",
    "estimated_total_score": "预估总分",
    "sat_erw": "SAT 读写",
    "sat_math": "SAT 数学",
    "act_english": "ACT 英语",
    "act_math": "ACT 数学",
    "act_reading": "ACT 阅读",
    "act_science": "ACT 科学",
    "t1_examples_text": "T1 示例说明",
    "t2_examples_text": "T2 示例说明",
    "t3_examples_text": "T3 示例说明",
    "t4_examples_text": "T4 示例说明",
    "tier_reference_notes": "层级补充说明",
    "sort_order": "排序",
    "competition_name": "竞赛名称",
    "competition_field": "竞赛领域",
    "competition_tier": "竞赛层级",
    "competition_level": "竞赛级别",
    "result_text": "结果描述",
    "rank_value": "名次",
    "participants_count": "参赛人数",
    "percentile": "百分位",
    "competition_year": "年份",
    "evidence_type": "佐证类型",
    "evidence_link_or_note": "佐证链接或备注",
    "prefer_long_term": "是否偏好长期活动",
    "prefer_leadership_roles": "是否偏好领导角色",
    "impact_required_field": "影响力必填字段",
    "quality_rules_notes": "活动质量规则说明",
    "activity_name": "活动名称",
    "activity_category": "活动类别",
    "activity_role": "活动角色",
    "duration_months": "持续月数",
    "weekly_hours": "每周投入小时",
    "team_size": "团队人数",
    "people_reached": "影响人数",
    "events_run": "举办活动数",
    "funds_raised": "筹款金额",
    "awards_or_media": "获奖或媒体报道",
    "output_preferred": "是否偏好有产出",
    "internal_only_output_penalty": "仅内部产出是否扣分",
    "strictness_rules_notes": "项目严格性规则说明",
    "project_id": "项目 ID",
    "project_name": "项目名称",
    "project_type": "项目类型",
    "project_field": "项目领域",
    "relevance_to_major": "与专业相关性",
    "hours_total": "总投入小时",
    "output_type": "产出类型",
    "link_or_note": "链接或备注",
    "notes": "备注",
}

TEXTAREA_FIELDS = {
    "curriculum_system_notes",
    "other_curriculum_notes",
    "curriculum_combination_notes",
    "notes",
    "language_index_method_notes",
    "not_applicable_reason",
    "tier_reference_notes",
    "quality_rules_notes",
    "strictness_rules_notes",
    "score_breakdown_json",
    "t1_examples_text",
    "t2_examples_text",
    "t3_examples_text",
    "t4_examples_text",
}

HIDDEN_FIELDS = {
    "student_id",
    "student_academic_id",
    "student_language_id",
    "student_standardized_test_id",
    "schema_version",
    "profile_type",
    "project_id",
}

# 中文注释：
# 这里按表控制需要隐藏的字段。
# 这样前端仍然能拿到完整正式档案快照，但不会把不希望学生直接改的字段渲染出来。
HIDDEN_FIELDS_BY_TABLE = {
    "student_basic_info": {"curriculum_system_notes"},
    "student_basic_info_curriculum_system": {"notes"},
    "student_academic": {
        "school_code_val",
        "school_type_notes",
        "rank_scope_code",
        "rank_evidence_notes",
        "other_curriculum_notes",
        "curriculum_combination_notes",
    },
    "student_academic_a_level_profile": {"notes"},
    "student_academic_a_level_subject": {"notes"},
    "student_academic_ap_profile": {
        "class_rank_available",
        "honors_count",
        "ap_count",
        "max_ap_offered_by_school",
        "best_n_ap_for_scoring",
        "notes",
    },
    "student_academic_ap_course": {"notes"},
    "student_academic_ib_profile": {"notes"},
    "student_academic_ib_subject": {"notes"},
    "student_academic_chinese_high_school_profile": {"notes"},
    "student_academic_chinese_high_school_subject": {"notes"},
    "student_academic_other_curriculum_profile": {"curriculum_scope_code", "notes"},
    "student_language_ielts": {"estimated_total", "evidence_level_code", "normalized_index_100", "is_best_score"},
    "student_language_toefl_ibt": {"estimated_total", "evidence_level_code", "normalized_index_100", "is_best_score"},
    "student_language_toefl_essentials": {"estimated_total", "evidence_level_code", "normalized_index_100", "is_best_score"},
    "student_language_det": {"estimated_total", "evidence_level_code", "normalized_index_100", "is_best_score"},
    "student_language_pte": {"estimated_total", "evidence_level_code", "normalized_index_100", "is_best_score"},
    "student_language_languagecert": {"estimated_total", "evidence_level_code", "normalized_index_100", "is_best_score"},
    "student_language_cambridge": {"estimated_total", "evidence_level_code", "normalized_index_100", "is_best_score"},
    "student_language_other": {"estimated_total", "evidence_level_code", "normalized_index_100", "is_best_score"},
    "student_standardized_test_records": {"is_best_score"},
    "student_competition_entries": {
        "sort_order",
        "competition_tier",
        "result_text",
        "rank_value",
        "participants_count",
        "percentile",
        "competition_year",
        "evidence_type",
        "evidence_link_or_note",
    },
    "student_activity_entries": {
        "sort_order",
        "activity_name",
        "activity_category",
        "activity_role",
        "duration_months",
        "weekly_hours",
        "team_size",
        "people_reached",
        "events_run",
        "funds_raised",
        "awards_or_media",
        "evidence_type",
        "evidence_link_or_note",
    },
    "student_project_entries": {
        "project_id",
        "sort_order",
        "project_name",
        "project_type",
        "project_field",
        "relevance_to_major",
        "duration_months",
        "hours_total",
        "team_size",
        "evidence_type",
        "evidence_link_or_note",
    },
}

FIELD_HELPERS = {
    ("student_basic_info", "MAJ_INTEREST_TEXT"): "如果目标专业下拉里没有合适选项，可在这里填写原始表述，例如：人文。",
    ("student_competition_entries", "notes"): "可以写介绍一下你参加的比赛和你拿到的名次。",
}

EXCLUDED_COLUMNS_BY_TABLE = {
    "student_competitions": {"student_competition_id"},
    "student_competition_entries": {"competition_id"},
    "student_academic_us_high_school_course": {"student_us_high_school_course_record_id"},
    "student_academic_ap_course": {"student_ap_course_record_id"},
    "student_academic_ib_subject": {"student_ib_subject_record_id"},
    "student_academic_chinese_high_school_subject": {"student_chs_subject_record_id"},
    "student_language_ielts": {"ielts_record_id"},
    "student_language_toefl_ibt": {"toefl_ibt_record_id"},
    "student_language_toefl_essentials": {"toefl_essentials_record_id"},
    "student_language_det": {"det_record_id"},
    "student_language_pte": {"pte_record_id"},
    "student_language_languagecert": {"languagecert_record_id"},
    "student_language_cambridge": {"cambridge_record_id"},
    "student_language_other": {"other_language_record_id"},
    "student_standardized_test_records": {"test_record_id"},
    "student_activities": {"student_activity_id"},
    "student_activity_entries": {"student_activity_entry_id"},
    "student_projects_experience": {"student_project_experience_id"},
    "student_project_outputs": {"output_id"},
}

TABLE_ORDER_BY = {
    "student_basic_info_curriculum_system": "ORDER BY is_primary DESC, curriculum_system_code ASC",
    "student_academic_us_high_school_course": "ORDER BY school_year_label DESC, term_code ASC, student_us_high_school_course_record_id ASC",
    "student_academic_a_level_subject": "ORDER BY al_subject_id ASC, stage_code ASC, is_predicted ASC, exam_series ASC",
    "student_academic_ap_course": "ORDER BY year_taken DESC, ap_course_id ASC",
    "student_academic_ib_subject": "ORDER BY ib_subject_id ASC, level_code ASC, is_predicted ASC",
    "student_academic_chinese_high_school_subject": "ORDER BY chs_subject_id ASC",
    "student_language_ielts": "ORDER BY test_date DESC, is_best_score DESC",
    "student_language_toefl_ibt": "ORDER BY test_date DESC, is_best_score DESC",
    "student_language_toefl_essentials": "ORDER BY test_date DESC, is_best_score DESC",
    "student_language_det": "ORDER BY test_date DESC, is_best_score DESC",
    "student_language_pte": "ORDER BY test_date DESC, is_best_score DESC",
    "student_language_languagecert": "ORDER BY test_date DESC, is_best_score DESC",
    "student_language_cambridge": "ORDER BY test_date DESC, is_best_score DESC",
    "student_language_other": "ORDER BY test_date DESC, is_best_score DESC",
    "student_standardized_test_records": "ORDER BY test_date DESC, is_best_score DESC",
    "student_competition_entries": "ORDER BY sort_order ASC, competition_year DESC",
    "student_activity_entries": "ORDER BY sort_order ASC",
    "student_project_entries": "ORDER BY sort_order ASC",
    "student_project_outputs": "ORDER BY project_id ASC, sort_order ASC",
}

STATIC_FIELD_OPTIONS = {
    ("student_basic_info", "current_grade"): "current_grade",
    ("student_basic_info", "graduation_year"): "graduation_year",
    ("student_basic_info", "target_entry_term"): "target_entry_term",
    ("student_basic_info", "CTRY_CODE_VAL"): "country_code",
    ("student_basic_info", "MAJ_CODE_VAL"): "major_code",
    ("student_basic_info_curriculum_system", "curriculum_system_code"): "curriculum_system",
    ("student_academic_other_curriculum_profile", "curriculum_scope_code"): "curriculum_system",
    ("student_academic", "school_city"): "school_city",
    ("student_academic_us_high_school_course", "us_high_school_course_id"): "us_high_school_course",
    ("student_academic_a_level_subject", "al_subject_id"): "a_level_subject",
    ("student_academic_ap_course", "ap_course_id"): "ap_course",
    ("student_academic_ib_subject", "ib_subject_id"): "ib_subject",
    ("student_academic_chinese_high_school_subject", "chs_subject_id"): "chs_subject",
    ("student_language", "best_test_type_code"): "language_test_type",
}

ENUM_FIELD_OPTIONS = {
    ("student_academic", "school_type_code"): ["PUBLIC", "PRIVATE", "INTERNATIONAL", "OTHER"],
    ("student_academic", "rank_scope_code"): ["GRADE", "CLASS", "TRACK", "SCHOOL", "OTHER"],
    ("student_academic", "rank_evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED", "TRANSCRIPT", "SCHOOL_SYSTEM", "OTHER"],
    ("student_academic_us_high_school_course", "term_code"): ["FALL", "SPRING", "FULL_YEAR", "SUMMER", "OTHER"],
    ("student_academic_us_high_school_course", "course_category_code"): ["ENGLISH", "MATH", "SCIENCE", "SOCIAL_SCIENCE", "WORLD_LANGUAGE", "ARTS", "CS", "BUSINESS", "ELECTIVE", "OTHER"],
    ("student_academic_us_high_school_course", "course_level_code"): ["REGULAR", "HONORS", "AP", "IB", "DUAL_ENROLLMENT", "ADVANCED", "OTHER"],
    ("student_academic_us_high_school_course", "grade_letter_code"): ["A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D", "F", "P", "NP", "W", "NA"],
    ("student_academic_a_level_subject", "stage_code"): ["AS", "A2", "FULL_A_LEVEL", "IN_PROGRESS"],
    ("student_academic_a_level_subject", "grade_code"): ["A*", "A", "B", "C", "D", "E", "U", "NA"],
    ("student_academic_a_level_subject", "board_code"): ["CAIE", "EDEXCEL", "AQA", "OCR", "OTHER", "UNKNOWN"],
    ("student_academic_a_level_subject", "session_code"): ["MAY_JUNE", "OCT_NOV", "JAN", "OTHER", "UNKNOWN"],
    ("student_academic_ib_profile", "tok_grade_code"): ["A", "B", "C", "D", "E", "NA"],
    ("student_academic_ib_profile", "ee_grade_code"): ["A", "B", "C", "D", "E", "NA"],
    ("student_academic_ib_subject", "level_code"): ["HL", "SL"],
    ("student_academic_chinese_high_school_profile", "grading_system_code"): ["PERCENT_100", "GPA_4", "GPA_5", "OTHER"],
    ("student_language", "best_score_status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language", "overall_cefr_level_code"): ["A1", "A2", "B1", "B2", "C1", "C2", "UNKNOWN"],
    ("student_language_ielts", "status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language_toefl_ibt", "status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language_toefl_essentials", "status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language_det", "status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language_pte", "status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language_languagecert", "status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language_cambridge", "status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language_other", "status_code"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_language_ielts", "evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED"],
    ("student_language_toefl_ibt", "evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED"],
    ("student_language_toefl_essentials", "evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED"],
    ("student_language_det", "evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED"],
    ("student_language_pte", "evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED"],
    ("student_language_languagecert", "evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED"],
    ("student_language_cambridge", "evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED"],
    ("student_language_other", "evidence_level_code"): ["CONFIRMED", "SELF_REPORTED", "ESTIMATED"],
    ("student_language_languagecert", "cefr_level_code"): ["A1", "A2", "B1", "B2", "C1", "C2", "UNKNOWN"],
    ("student_language_cambridge", "cefr_level_code"): ["A1", "A2", "B1", "B2", "C1", "C2", "UNKNOWN"],
    ("student_language_other", "cefr_level_code"): ["A1", "A2", "B1", "B2", "C1", "C2", "UNKNOWN"],
    ("student_standardized_tests", "best_test_type"): ["SAT", "ACT"],
    ("student_standardized_test_records", "test_type"): ["SAT", "ACT"],
    ("student_standardized_test_records", "status"): ["SCORED", "PLANNED", "ESTIMATED"],
    ("student_competition_entries", "competition_field"): ["MATH", "CS", "PHYSICS", "CHEM", "BIO", "ECON", "DEBATE", "WRITING", "OTHER"],
    ("student_competition_entries", "competition_tier"): ["T1", "T2", "T3", "T4", "UNKNOWN"],
    ("student_competition_entries", "competition_level"): ["SCHOOL", "CITY", "PROVINCE", "NATIONAL", "INTERNATIONAL"],
    ("student_competition_entries", "evidence_type"): ["CERTIFICATE", "LINK", "SCHOOL_CONFIRMATION", "NONE"],
    ("student_activity_entries", "activity_category"): ["LEADERSHIP", "ACADEMIC", "SPORTS", "ARTS", "COMMUNITY", "ENTREPRENEURSHIP", "OTHER"],
    ("student_activity_entries", "activity_role"): ["FOUNDER", "PRESIDENT", "CORE_MEMBER", "MEMBER", "OTHER"],
    ("student_activity_entries", "evidence_type"): ["LINK", "SCHOOL_CONFIRMATION", "PHOTO", "NONE"],
    ("student_project_entries", "project_type"): ["RESEARCH", "INTERNSHIP", "ENGINEERING_PROJECT", "STARTUP", "CREATIVE_PROJECT", "VOLUNTEER_WORK", "OTHER"],
    ("student_project_entries", "project_field"): ["CS", "ECON", "FIN", "BIO", "PHYS", "DESIGN", "OTHER"],
    ("student_project_entries", "relevance_to_major"): ["HIGH", "MEDIUM", "LOW"],
    ("student_project_entries", "evidence_type"): ["LINK", "MENTOR_LETTER", "EMPLOYER_CONFIRMATION", "NONE"],
    ("student_project_outputs", "output_type"): ["PAPER", "REPORT", "CODE", "DEMO", "PRODUCT", "PORTFOLIO", "PRESENTATION", "OTHER"],
}

ENUM_OPTION_LABELS = {
    ("student_academic", "school_type_code"): {
        "PUBLIC": "公立学校",
        "PRIVATE": "私立学校",
        "INTERNATIONAL": "国际学校",
        "OTHER": "其他",
    },
    ("student_academic", "rank_scope_code"): {
        "GRADE": "年级",
        "CLASS": "班级",
        "TRACK": "课程轨道",
        "SCHOOL": "全校",
        "OTHER": "其他",
    },
    ("student_academic", "rank_evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "估算",
        "TRANSCRIPT": "成绩单",
        "SCHOOL_SYSTEM": "学校系统",
        "OTHER": "其他",
    },
    ("student_academic_us_high_school_course", "term_code"): {
        "FALL": "秋季学期",
        "SPRING": "春季学期",
        "FULL_YEAR": "全年课",
        "SUMMER": "暑期",
        "OTHER": "其他",
    },
    ("student_academic_us_high_school_course", "course_category_code"): {
        "ENGLISH": "英语",
        "MATH": "数学",
        "SCIENCE": "科学",
        "SOCIAL_SCIENCE": "社会科学",
        "WORLD_LANGUAGE": "外语",
        "ARTS": "艺术",
        "CS": "计算机",
        "BUSINESS": "商科",
        "ELECTIVE": "选修",
        "OTHER": "其他",
    },
    ("student_academic_us_high_school_course", "course_level_code"): {
        "REGULAR": "Regular",
        "HONORS": "Honors",
        "AP": "AP",
        "IB": "IB",
        "DUAL_ENROLLMENT": "Dual Enrollment",
        "ADVANCED": "Advanced",
        "OTHER": "其他",
    },
    ("student_academic_us_high_school_course", "grade_letter_code"): {
        "A+": "A+",
        "A": "A",
        "A-": "A-",
        "B+": "B+",
        "B": "B",
        "B-": "B-",
        "C+": "C+",
        "C": "C",
        "C-": "C-",
        "D": "D",
        "F": "F",
        "P": "Pass",
        "NP": "No Pass",
        "W": "Withdraw",
        "NA": "暂未评级",
    },
    ("student_academic_a_level_subject", "stage_code"): {
        "AS": "AS 阶段",
        "A2": "A2 阶段",
        "FULL_A_LEVEL": "完整 A-Level",
        "IN_PROGRESS": "修读中",
    },
    ("student_academic_a_level_subject", "grade_code"): {
        "A*": "A*",
        "A": "A",
        "B": "B",
        "C": "C",
        "D": "D",
        "E": "E",
        "U": "U",
        "NA": "暂未评级",
    },
    ("student_academic_a_level_subject", "board_code"): {
        "CAIE": "剑桥国际",
        "EDEXCEL": "爱德思",
        "AQA": "AQA",
        "OCR": "OCR",
        "OTHER": "其他",
        "UNKNOWN": "未知",
    },
    ("student_academic_a_level_subject", "session_code"): {
        "MAY_JUNE": "5-6 月",
        "OCT_NOV": "10-11 月",
        "JAN": "1 月",
        "OTHER": "其他",
        "UNKNOWN": "未知",
    },
    ("student_academic_ib_subject", "level_code"): {
        "HL": "HL 高阶",
        "SL": "SL 标准级",
    },
    ("student_academic_ib_profile", "tok_grade_code"): {
        "A": "A 等",
        "B": "B 等",
        "C": "C 等",
        "D": "D 等",
        "E": "E 等",
        "NA": "暂未评级",
    },
    ("student_academic_ib_profile", "ee_grade_code"): {
        "A": "A 等",
        "B": "B 等",
        "C": "C 等",
        "D": "D 等",
        "E": "E 等",
        "NA": "暂未评级",
    },
    ("student_academic_chinese_high_school_profile", "grading_system_code"): {
        "PERCENT_100": "百分制",
        "GPA_4": "4 分制 GPA",
        "GPA_5": "5 分制 GPA",
        "OTHER": "其他",
    },
    ("student_language", "best_score_status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language", "overall_cefr_level_code"): {
        "A1": "入门级",
        "A2": "初级",
        "B1": "中级",
        "B2": "中高级",
        "C1": "高级",
        "C2": "精通级",
        "UNKNOWN": "未知",
    },
    ("student_language_ielts", "status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language_toefl_ibt", "status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language_toefl_essentials", "status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language_det", "status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language_pte", "status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language_languagecert", "status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language_cambridge", "status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language_other", "status_code"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_language_ielts", "evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "预估",
    },
    ("student_language_toefl_ibt", "evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "预估",
    },
    ("student_language_toefl_essentials", "evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "预估",
    },
    ("student_language_det", "evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "预估",
    },
    ("student_language_pte", "evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "预估",
    },
    ("student_language_languagecert", "evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "预估",
    },
    ("student_language_cambridge", "evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "预估",
    },
    ("student_language_other", "evidence_level_code"): {
        "CONFIRMED": "已确认",
        "SELF_REPORTED": "学生自述",
        "ESTIMATED": "预估",
    },
    ("student_language_languagecert", "cefr_level_code"): {
        "A1": "入门级",
        "A2": "初级",
        "B1": "中级",
        "B2": "中高级",
        "C1": "高级",
        "C2": "精通级",
        "UNKNOWN": "未知",
    },
    ("student_language_cambridge", "cefr_level_code"): {
        "A1": "入门级",
        "A2": "初级",
        "B1": "中级",
        "B2": "中高级",
        "C1": "高级",
        "C2": "精通级",
        "UNKNOWN": "未知",
    },
    ("student_language_other", "cefr_level_code"): {
        "A1": "入门级",
        "A2": "初级",
        "B1": "中级",
        "B2": "中高级",
        "C1": "高级",
        "C2": "精通级",
        "UNKNOWN": "未知",
    },
    ("student_standardized_tests", "best_test_type"): {
        "SAT": "SAT",
        "ACT": "ACT",
    },
    ("student_standardized_test_records", "test_type"): {
        "SAT": "SAT",
        "ACT": "ACT",
    },
    ("student_standardized_test_records", "status"): {
        "SCORED": "已出分",
        "PLANNED": "计划参加",
        "ESTIMATED": "预估",
    },
    ("student_competition_entries", "competition_field"): {
        "MATH": "数学",
        "CS": "计算机",
        "PHYSICS": "物理",
        "CHEM": "化学",
        "BIO": "生物",
        "ECON": "经济",
        "DEBATE": "辩论",
        "WRITING": "写作",
        "OTHER": "其他",
    },
    ("student_competition_entries", "competition_tier"): {
        "T1": "第一梯队",
        "T2": "第二梯队",
        "T3": "第三梯队",
        "T4": "第四梯队",
        "UNKNOWN": "未知",
    },
    ("student_competition_entries", "competition_level"): {
        "SCHOOL": "校级",
        "CITY": "市级",
        "PROVINCE": "省级",
        "NATIONAL": "国家级",
        "INTERNATIONAL": "国际级",
    },
    ("student_competition_entries", "evidence_type"): {
        "CERTIFICATE": "证书",
        "LINK": "链接",
        "SCHOOL_CONFIRMATION": "学校证明",
        "NONE": "无",
    },
    ("student_activity_entries", "activity_category"): {
        "LEADERSHIP": "领导力活动",
        "ACADEMIC": "学术活动",
        "SPORTS": "体育活动",
        "ARTS": "艺术活动",
        "COMMUNITY": "社区活动",
        "ENTREPRENEURSHIP": "创业活动",
        "OTHER": "其他",
    },
    ("student_activity_entries", "activity_role"): {
        "FOUNDER": "创始人",
        "PRESIDENT": "负责人",
        "CORE_MEMBER": "核心成员",
        "MEMBER": "成员",
        "OTHER": "其他",
    },
    ("student_activity_entries", "evidence_type"): {
        "LINK": "链接",
        "SCHOOL_CONFIRMATION": "学校证明",
        "PHOTO": "照片",
        "NONE": "无",
    },
    ("student_project_entries", "project_type"): {
        "RESEARCH": "科研项目",
        "INTERNSHIP": "实习经历",
        "ENGINEERING_PROJECT": "工程项目",
        "STARTUP": "创业项目",
        "CREATIVE_PROJECT": "创意项目",
        "VOLUNTEER_WORK": "志愿工作",
        "OTHER": "其他",
    },
    ("student_project_entries", "project_field"): {
        "CS": "计算机",
        "ECON": "经济",
        "FIN": "金融",
        "BIO": "生物",
        "PHYS": "物理",
        "DESIGN": "设计",
        "OTHER": "其他",
    },
    ("student_project_entries", "relevance_to_major"): {
        "HIGH": "高相关",
        "MEDIUM": "中相关",
        "LOW": "低相关",
    },
    ("student_project_entries", "evidence_type"): {
        "LINK": "链接",
        "MENTOR_LETTER": "导师证明",
        "EMPLOYER_CONFIRMATION": "单位证明",
        "NONE": "无",
    },
    ("student_project_outputs", "output_type"): {
        "PAPER": "论文",
        "REPORT": "报告",
        "CODE": "代码",
        "DEMO": "演示",
        "PRODUCT": "产品",
        "PORTFOLIO": "作品集",
        "PRESENTATION": "展示材料",
        "OTHER": "其他",
    },
}

DICTIONARY_OPTION_LOADERS = {
    "current_grade": lambda _db: [
        {"value": "初一", "label": "初一"},
        {"value": "初二", "label": "初二"},
        {"value": "初三", "label": "初三"},
        {"value": "高一", "label": "高一"},
        {"value": "高二", "label": "高二"},
        {"value": "高三", "label": "高三"},
        {"value": "G9", "label": "G9 (初三)"},
        {"value": "G10", "label": "G10 (高一)"},
        {"value": "G11", "label": "G11 (高二)"},
        {"value": "G12", "label": "G12 (高三)"},
        {"value": "大一", "label": "大一"},
        {"value": "大二", "label": "大二"},
        {"value": "大三", "label": "大三"},
        {"value": "大四", "label": "大四"},
        {"value": "Gap Year", "label": "Gap Year"},
        {"value": "TRANSFER_YEAR_1", "label": "大一转学申请"},
        {"value": "已毕业", "label": "已毕业"},
        {"value": "OTHER", "label": "其他"},
    ],
    "graduation_year": lambda _db: _build_graduation_year_options(),
    "target_entry_term": lambda _db: _build_entry_term_options(),
    "country_code": lambda db: _load_resolution_options(db, COUNTRY_CONFIG),
    "major_code": lambda db: _load_resolution_options(db, MAJOR_CONFIG),
    "curriculum_system": lambda db: _load_simple_dictionary_options(
        db,
        table_name="dict_curriculum_system",
        value_column="curriculum_system_code",
        label_cn_column="curriculum_system_name_cn",
        label_en_column="curriculum_system_name_en",
    ),
    "language_test_type": lambda db: _load_simple_dictionary_options(
        db,
        table_name="dict_language_test_type",
        value_column="test_type_code",
        label_cn_column="test_type_name_cn",
        label_en_column="test_type_name_en",
    ),
    "school_city": lambda _db: _build_static_options(
        [
            "北京",
            "上海",
            "广州",
            "深圳",
            "杭州",
            "南京",
            "苏州",
            "成都",
            "重庆",
            "武汉",
            "西安",
            "天津",
            "青岛",
            "宁波",
            "长沙",
            "郑州",
            "合肥",
            "厦门",
            "福州",
            "南昌",
            "昆明",
            "海口",
            "香港",
            "澳门",
            "台北",
            "新加坡",
            "伦敦",
            "曼彻斯特",
            "爱丁堡",
            "纽约",
            "波士顿",
            "洛杉矶",
            "芝加哥",
            "多伦多",
            "温哥华",
            "墨尔本",
            "悉尼",
            "其他",
        ]
    ),
    "us_high_school_course": lambda db: _load_resolution_options(db, US_HIGH_SCHOOL_COURSE_CONFIG),
    "a_level_subject": lambda db: _load_resolution_options(db, AL_LEVEL_SUBJECT_CONFIG),
    "ap_course": lambda db: _load_resolution_options(db, AP_COURSE_CONFIG),
    "ib_subject": lambda db: _load_resolution_options(db, IB_SUBJECT_CONFIG),
    "chs_subject": lambda db: _load_resolution_options(db, CHINESE_HIGH_SCHOOL_SUBJECT_CONFIG),
}


def load_business_profile_form_bundle(
    db: Session,
    *,
    student_id: str,
) -> dict[str, Any]:
    """
    读取正式档案表快照与表单元数据。

    中文注释：
    这里返回的 archive_form 是真正来自正式业务表的数据，
    前端不再从 ai_chat_profile_results.profile_json 里拼表单。
    """

    archive_form = load_business_profile_snapshot(db, student_id=student_id)
    form_meta = build_business_profile_form_meta(db)
    return {
        "archive_form": archive_form,
        "form_meta": form_meta,
    }


def load_business_profile_snapshot(
    db: Session,
    *,
    student_id: str,
) -> dict[str, Any]:
    """按正式业务表结构读取当前学生档案快照。"""

    payload: dict[str, Any] = {}

    for table_name in SINGLE_ROW_TABLES:
        row = _load_single_row(db, table_name=table_name, student_id=student_id)
        payload[table_name] = row or {}

    for table_name in MULTI_ROW_TABLES:
        rows = _load_multi_rows(db, table_name=table_name, student_id=student_id)
        payload[table_name] = rows

    payload["student_basic_info_target_country_entries"] = _load_guided_target_country_entries(
        db,
        student_id=student_id,
    )
    payload["student_basic_info_target_major_entries"] = _load_guided_target_major_entries(
        db,
        student_id=student_id,
    )

    return payload


def _load_guided_target_country_entries(db: Session, *, student_id: str) -> list[dict[str, Any]]:
    rows = db.execute(
        text(
            """
            SELECT country_code, sort_order, is_primary, remark
            FROM student_basic_info_target_country_entries
            WHERE student_id = :student_id AND delete_flag = '1'
            ORDER BY is_primary DESC, sort_order ASC, id ASC
            """
        ),
        {"student_id": student_id},
    ).mappings().all()
    return [dict(row) for row in rows]


def _load_guided_target_major_entries(db: Session, *, student_id: str) -> list[dict[str, Any]]:
    rows = db.execute(
        text(
            """
            SELECT major_direction_code, major_direction_label, major_code, sort_order, is_primary
            FROM student_basic_info_target_major_entries
            WHERE student_id = :student_id AND delete_flag = '1'
            ORDER BY is_primary DESC, sort_order ASC, id ASC
            """
        ),
        {"student_id": student_id},
    ).mappings().all()
    return [dict(row) for row in rows]


def build_business_profile_form_meta(db: Session) -> dict[str, Any]:
    """构建前端动态表单所需的元数据。"""

    cached_dictionary_options: dict[str, list[dict[str, str]]] = {}
    table_meta: dict[str, Any] = {}

    for table_name in TABLE_ORDER:
        table_kind = "single" if table_name in SINGLE_ROW_TABLES else "multi"
        columns = _get_editable_columns(db, table_name)
        fields: list[dict[str, Any]] = []

        for column in columns:
            field_name = column["name"]
            options = _resolve_field_options(
                db,
                table_name=table_name,
                field_name=field_name,
                cached_dictionary_options=cached_dictionary_options,
            )
            fields.append(
                {
                    "name": field_name,
                    "label": FIELD_LABELS.get(field_name, field_name),
                    "input_type": _infer_input_type(field_name=field_name, column_type=column["type"], options=options),
                    "hidden": _is_field_hidden(table_name=table_name, field_name=field_name),
                    "options": options,
                    "helper_text": FIELD_HELPERS.get((table_name, field_name)),
                }
            )

        table_meta[table_name] = {
            "label": TABLE_LABELS.get(table_name, table_name),
            "kind": table_kind,
            "fields": fields,
        }

    return {
        "table_order": TABLE_ORDER,
        "tables": table_meta,
    }


def _load_single_row(
    db: Session,
    *,
    table_name: str,
    student_id: str,
) -> dict[str, Any] | None:
    columns = _get_editable_columns(db, table_name)
    if not columns:
        return None

    select_columns = ", ".join(f"`{item['name']}`" for item in columns)
    sql = text(
        f"""
        SELECT {select_columns}
        FROM `{table_name}`
        WHERE `student_id` = :student_id
          AND `delete_flag` = '1'
        LIMIT 1
        """
    )
    row = db.execute(sql, {"student_id": student_id}).mappings().first()
    if row is None:
        return None
    return dict(row)


def _load_multi_rows(
    db: Session,
    *,
    table_name: str,
    student_id: str,
) -> list[dict[str, Any]]:
    columns = _get_editable_columns(db, table_name)
    if not columns:
        return []

    select_columns = ", ".join(f"`{item['name']}`" for item in columns)
    if table_name == "student_project_outputs":
        sql = text(
            f"""
            SELECT {select_columns}
            FROM `student_project_outputs`
            WHERE `project_id` IN (
                SELECT `project_id`
                FROM `student_project_entries`
                WHERE `student_id` = :student_id
                  AND `delete_flag` = '1'
            )
              AND `delete_flag` = '1'
            {TABLE_ORDER_BY.get(table_name, "")}
            """
        )
    else:
        sql = text(
            f"""
            SELECT {select_columns}
            FROM `{table_name}`
            WHERE `student_id` = :student_id
              AND `delete_flag` = '1'
            {TABLE_ORDER_BY.get(table_name, "")}
            """
        )

    rows = db.execute(sql, {"student_id": student_id}).mappings().all()
    return [dict(row) for row in rows]


def _get_editable_columns(
    db: Session,
    table_name: str,
) -> list[dict[str, str]]:
    try:
        rows = db.execute(text(f"SHOW COLUMNS FROM `{table_name}`")).mappings().all()
    except SQLAlchemyError:
        return []
    excluded_columns = set(EXCLUDED_COLUMNS_BY_TABLE.get(table_name, set()))
    excluded_columns.update({"create_time", "update_time", "delete_flag"})

    columns: list[dict[str, str]] = []
    for row in rows:
        field_name = str(row["Field"])
        if field_name in excluded_columns:
            continue
        columns.append(
            {
                "name": field_name,
                "type": str(row["Type"]),
            }
        )
    return columns


def _resolve_field_options(
    db: Session,
    *,
    table_name: str,
    field_name: str,
    cached_dictionary_options: dict[str, list[dict[str, str]]],
) -> list[dict[str, str]] | None:
    dictionary_key = STATIC_FIELD_OPTIONS.get((table_name, field_name))
    if dictionary_key:
        if dictionary_key not in cached_dictionary_options:
            cached_dictionary_options[dictionary_key] = DICTIONARY_OPTION_LOADERS[dictionary_key](db)
        return cached_dictionary_options[dictionary_key]

    enum_values = ENUM_FIELD_OPTIONS.get((table_name, field_name))
    if enum_values:
        option_labels = ENUM_OPTION_LABELS.get((table_name, field_name), {})
        return [{"value": item, "label": option_labels.get(item, item)} for item in enum_values]

    return None


def _infer_input_type(
    *,
    field_name: str,
    column_type: str,
    options: list[dict[str, str]] | None,
) -> str:
    normalized_column_type = column_type.lower()

    if options:
        return "select"
    if field_name in TEXTAREA_FIELDS or normalized_column_type == "json":
        return "textarea"
    if normalized_column_type.startswith("date"):
        return "date"
    if normalized_column_type.startswith("tinyint(1)") or field_name.startswith("is_") or field_name.startswith("prefer_"):
        return "checkbox"
    if any(token in normalized_column_type for token in ["int", "decimal", "float", "double"]):
        return "number"
    return "text"


def _load_resolution_options(db: Session, config) -> list[dict[str, str]]:
    return _load_simple_dictionary_options(
        db,
        table_name=config.table_name,
        value_column=config.code_column,
        label_cn_column=config.cn_name_column,
        label_en_column=config.en_name_column,
    )


def _load_simple_dictionary_options(
    db: Session,
    *,
    table_name: str,
    value_column: str,
    label_cn_column: str,
    label_en_column: str | None = None,
) -> list[dict[str, str]]:
    select_columns = [f"`{value_column}` AS value", f"`{label_cn_column}` AS label_cn"]
    if label_en_column:
        select_columns.append(f"`{label_en_column}` AS label_en")
    else:
        select_columns.append("NULL AS label_en")

    sql = text(
        f"""
        SELECT {", ".join(select_columns)}
        FROM `{table_name}`
        WHERE `delete_flag` = '1'
        ORDER BY value ASC
        """
    )
    rows = db.execute(sql).mappings().all()
    return [
        {
            "value": str(row["value"]),
            "label": str(row["label_cn"] or row["label_en"] or row["value"]),
        }
        for row in rows
    ]


def _build_static_options(values: list[str]) -> list[dict[str, str]]:
    return [{"value": item, "label": item} for item in values]


def _build_graduation_year_options() -> list[dict[str, str]]:
    current_year = date.today().year
    return _build_static_options([str(year) for year in range(current_year - 1, current_year + 9)])


def _build_entry_term_options() -> list[dict[str, str]]:
    current_year = date.today().year
    options: list[str] = []
    for year in range(current_year, current_year + 6):
        options.append(f"{year}春季入学")
        options.append(f"{year}秋季入学")
    return _build_static_options(options)


def _is_field_hidden(*, table_name: str, field_name: str) -> bool:
    if field_name in HIDDEN_FIELDS:
        return True
    return field_name in HIDDEN_FIELDS_BY_TABLE.get(table_name, set())
