from __future__ import annotations

from copy import deepcopy
from datetime import datetime
import json
import logging
import re
from typing import Any
from uuid import uuid4

from sqlalchemy import text
from sqlalchemy.orm import Session

from backend.services.ai_prompt_runtime_service import execute_prompt_with_context
from backend.services.business_profile_form_service import DICTIONARY_OPTION_LOADERS
from backend.services.business_profile_form_service import ENUM_FIELD_OPTIONS
from backend.services.business_profile_form_service import ENUM_OPTION_LABELS
from backend.services.business_profile_form_service import STATIC_FIELD_OPTIONS
from backend.services.business_profile_form_service import build_business_profile_form_meta
from backend.services.business_profile_persistence_service import build_db_payload_from_profile_json
from backend.services.business_profile_persistence_service import persist_business_profile_snapshot


logger = logging.getLogger(__name__)

QUESTIONNAIRE_CODE = "student_profile_guided_v1"
QUESTIONNAIRE_VERSION = 1
SCORING_PROMPT_KEY = "student_profile_build.scoring"


def _opt(value: str, label: str | None = None) -> dict[str, str]:
    return {"value": value, "label": label or value}


QUESTIONS: list[dict[str, Any]] = [
    {
        "code": "Q1",
        "module_code": "basic",
        "module_title": "一、基础申请目标",
        "type": "single",
        "title": "你目前所在年级是？",
        "options": [
            _opt("G9", "G9 (初三)"),
            _opt("G10", "G10 (高一)"),
            _opt("G11", "G11 (高二)"),
            _opt("G12", "G12 (高三)"),
            _opt("Gap Year", "Gap Year"),
            _opt("TRANSFER_YEAR_1", "大一转学申请"),
            _opt("OTHER", "其他"),
        ],
    },
    {
        "code": "Q2",
        "module_code": "basic",
        "module_title": "一、基础申请目标",
        "type": "single",
        "title": "你计划申请哪一届入学？",
        "options": [
            _opt("2026_FALL", "2026 Fall"),
            _opt("2027_FALL", "2027 Fall"),
            _opt("2028_FALL", "2028 Fall"),
            _opt("OTHER", "其他"),
        ],
    },
    {
        "code": "Q3",
        "module_code": "basic",
        "module_title": "一、基础申请目标",
        "type": "multi",
        "title": "你主要考虑哪些国家或地区？最多选 3 个。",
        "max_select": 3,
        "searchable": True,
        "options": [
            _opt("US", "美国"),
            _opt("UK", "英国"),
            _opt("HK", "中国香港"),
            _opt("SG", "新加坡"),
            _opt("CA", "加拿大"),
            _opt("AU", "澳大利亚"),
            _opt("EU", "欧洲"),
            _opt("OTHER", "其他"),
        ],
    },
    {
        "code": "Q4",
        "module_code": "basic",
        "module_title": "一、基础申请目标",
        "type": "multi",
        "title": "你目前感兴趣的专业方向是？最多选 2 个。",
        "max_select": 2,
        "searchable": True,
        "default_selected_values": ["UNDECIDED"],
        "exclusive_option_values": ["UNDECIDED"],
        "options": [
            _opt("CS_AI_DS", "计算机 / AI / 数据科学"),
            _opt("MATH_STATS", "数学 / 统计"),
            _opt("ENGINEERING", "工程"),
            _opt("BUSINESS_ECON_FINANCE", "经济 / 金融 / 商科"),
            _opt("SCIENCE", "物理 / 化学 / 生物"),
            _opt("PSY_EDU", "心理 / 教育"),
            _opt("SOCIAL_SCIENCE", "社科 / 政治 / 国际关系"),
            _opt("MEDIA_COMM", "传媒 / 新闻 / 传播"),
            _opt("LAW", "法学相关"),
            _opt("ART_DESIGN_ARCH", "艺术 / 设计 / 建筑"),
            _opt("UNDECIDED", "还不确定"),
        ],
    },
    {
        "code": "Q5",
        "module_code": "academic",
        "module_title": "二、校内学术背景",
        "type": "single",
        "title": "你目前就读的课程体系是？",
        "options": [
            _opt("A_LEVEL", "A-Level"),
            _opt("AP", "AP"),
            _opt("IB", "IB"),
            _opt("CHINESE_HIGH_SCHOOL", "普高"),
            _opt("US_HIGH_SCHOOL", "国际学校美高体系"),
            _opt("INTERNATIONAL_OTHER", "国际学校其他体系"),
            _opt("OTHER", "其他"),
        ],
    },
    {
        "code": "Q6",
        "module_code": "academic",
        "module_title": "二、校内学术背景",
        "type": "single",
        "title": "你在年级中的大致表现是？",
        "options": [
            _opt("TOP_1", "年级前 1%"),
            _opt("TOP_5", "年级前 5%"),
            _opt("TOP_10", "年级前 10%"),
            _opt("TOP_20", "年级前 20%"),
            _opt("TOP_30", "年级前 30%"),
            _opt("UPPER_MIDDLE", "年级中上"),
            _opt("MIDDLE", "年级中等"),
            _opt("UNKNOWN", "暂不清楚"),
        ],
    },
    {
        "code": "Q7",
        "module_code": "academic",
        "module_title": "二、校内学术背景",
        "type": "single",
        "title": "你所在年级大约有多少人？",
        "custom_input_type": "number",
        "custom_placeholder": "请输入具体人数",
        "options": [
            _opt("LT_50", "50 人以下"),
            _opt("50_100", "50-100 人"),
            _opt("100_200", "100-200 人"),
            _opt("200_500", "200-500 人"),
            _opt("GT_500", "500 人以上"),
            _opt("UNKNOWN", "不清楚"),
            _opt("CUSTOM", "自填"),
        ],
    },
    {
        "code": "Q8",
        "module_code": "academic",
        "module_title": "二、校内学术背景",
        "type": "branch_form",
        "title": "你的校内成绩可以怎么填写？",
    },
]

QUESTIONS.extend(
    [
        {
            "code": "Q9",
            "module_code": "language",
            "module_title": "三、语言成绩",
            "type": "single",
            "title": "你目前有哪类正式语言成绩？",
            "options": [
                _opt("IELTS", "雅思"),
                _opt("TOEFL_IBT", "托福 iBT"),
                _opt("TOEFL_ESSENTIALS", "托福 Essentials"),
                _opt("DET", "多邻国"),
                _opt("PTE", "PTE"),
                _opt("LANGUAGECERT", "LanguageCert"),
                _opt("CAMBRIDGE", "剑桥英语"),
                _opt("OTHER", "其他语言考试"),
            ],
        },
        {
            "code": "Q10",
            "module_code": "language",
            "module_title": "三、语言成绩",
            "type": "branch_form",
            "title": "请填写你已有的正式语言成绩。",
        },
        {
            "code": "Q11",
            "module_code": "language",
            "module_title": "三、语言成绩",
            "type": "single",
            "title": "如果还没有正式语言成绩，你当前大致目标或预估水平是？",
            "options": [
                _opt("IELTS_6", "IELTS 6"),
                _opt("IELTS_6_5", "IELTS 6.5"),
                _opt("IELTS_7", "IELTS 7"),
                _opt("IELTS_7_5_PLUS", "IELTS 7.5+"),
                _opt("TOEFL_90", "TOEFL 90"),
                _opt("TOEFL_100", "TOEFL 100"),
                _opt("TOEFL_105_PLUS", "TOEFL 105+"),
                _opt("OTHER", "其他"),
            ],
        },
        {
            "code": "Q12",
            "module_code": "standardized",
            "module_title": "四、标化与外部考试",
            "type": "multi",
            "title": "你目前涉及哪些外部考试？可多选。",
            "exclusive_option_values": ["NONE"],
            "options": [
                _opt("A_LEVEL", "A-Level"),
                _opt("AS", "AS"),
                _opt("AP", "AP"),
                _opt("IB", "IB"),
                _opt("SAT", "SAT"),
                _opt("ACT", "ACT"),
                _opt("NONE", "还没有"),
            ],
        },
        {
            "code": "Q13",
            "module_code": "standardized",
            "module_title": "四、标化与外部考试",
            "type": "branch_form",
            "title": "请补充你已有或预估的外部考试成绩。",
        },
        {
            "code": "Q14",
            "module_code": "standardized",
            "module_title": "四、标化与外部考试",
            "type": "single",
            "title": "如果还没有正式标化成绩，你当前大致目标或预估水平是？",
            "allow_custom_text": True,
            "custom_placeholder": "可补充预估分数或说明",
            "options": [
                _opt("SAT_1450_1500", "SAT 1450-1500"),
                _opt("SAT_1500_1550", "SAT 1500-1550"),
                _opt("ACT_32_33", "ACT 32-33"),
                _opt("ACT_34_36", "ACT 34-36"),
                _opt("A_LEVEL_ASTARAA", "A-Level A*A*A"),
                _opt("AP_3X5", "AP 3 门 5 分"),
                _opt("IB_40_PLUS", "IB 40+"),
                _opt("UNSURE", "还不确定"),
            ],
        },
        {
            "code": "Q15",
            "module_code": "competition",
            "module_title": "五、竞赛经历",
            "type": "experience_form",
            "title": "你是否有比较重要的竞赛经历？如有，请填写最有代表性的一项。",
        },
        {
            "code": "Q16",
            "module_code": "activity",
            "module_title": "六、活动经历",
            "type": "experience_form",
            "title": "你是否有长期活动、社团、志愿、领导力或创业相关经历？",
        },
        {
            "code": "Q17",
            "module_code": "project",
            "module_title": "七、项目经历",
            "type": "experience_form",
            "title": "你是否有研究、实习、工程、作品集或其他项目经历？",
        },
    ]
)

STANDARDIZED_SCORE_QUESTION_TEMPLATES: dict[str, dict[str, Any]] = {
    "A_LEVEL": {
        "code": "Q13_A_LEVEL",
        "module_code": "standardized",
        "module_title": "四、标化与外部考试",
        "type": "repeatable_form",
        "title": "请填写你的 A-Level 成绩。",
        "exam_type": "A_LEVEL",
        "min_rows": 1,
    },
    "IB": {
        "code": "Q13_IB",
        "module_code": "standardized",
        "module_title": "四、标化与外部考试",
        "type": "repeatable_form",
        "title": "请填写你的 IB 成绩。",
        "exam_type": "IB",
        "min_rows": 1,
    },
    "AS": {
        "code": "Q13_AS",
        "module_code": "standardized",
        "module_title": "四、标化与外部考试",
        "type": "repeatable_form",
        "title": "请填写你的 AS 成绩。",
        "exam_type": "AS",
        "min_rows": 1,
    },
    "AP": {
        "code": "Q13_AP",
        "module_code": "standardized",
        "module_title": "四、标化与外部考试",
        "type": "repeatable_form",
        "title": "请填写你的 AP 成绩。",
        "exam_type": "AP",
        "min_rows": 1,
    },
    "SAT": {
        "code": "Q13_SAT",
        "module_code": "standardized",
        "module_title": "四、标化与外部考试",
        "type": "branch_form",
        "title": "请填写你的 SAT 成绩。",
        "exam_type": "SAT",
    },
    "ACT": {
        "code": "Q13_ACT",
        "module_code": "standardized",
        "module_title": "四、标化与外部考试",
        "type": "branch_form",
        "title": "请填写你的 ACT 成绩。",
        "exam_type": "ACT",
    },
}

STANDARDIZED_SCORE_QUESTION_BY_CODE = {
    question["code"]: question for question in STANDARDIZED_SCORE_QUESTION_TEMPLATES.values()
}

QUESTION_BY_CODE = {
    question["code"]: question
    for question in QUESTIONS + list(STANDARDIZED_SCORE_QUESTION_TEMPLATES.values())
}
QUESTION_ORDER = [question["code"] for question in QUESTIONS]


def _field(name: str, label: str, kind: str = "text", **extra: Any) -> dict[str, Any]:
    result = {"name": name, "label": label, "kind": kind}
    result.update(extra)
    return result


def _selected_standardized_tests_in_order(answers: dict[str, dict[str, Any]]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in _selected_values(answers.get("Q12")):
        if value == "NONE" or value not in STANDARDIZED_SCORE_QUESTION_TEMPLATES or value in seen:
            continue
        result.append(value)
        seen.add(value)
    return result


def _standardized_score_questions(answers: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    return [STANDARDIZED_SCORE_QUESTION_TEMPLATES[value] for value in _selected_standardized_tests_in_order(answers)]


def _visible_question_index(answers: dict[str, dict[str, Any]], question_code: str) -> int:
    for index, question in enumerate(_visible_questions(answers)):
        if question["code"] == question_code:
            return index
    return 0


QUESTION_OPTION_FIELD_SOURCES: dict[str, tuple[str, str]] = {
    "Q1": ("student_basic_info", "current_grade"),
    "Q2": ("student_basic_info", "target_entry_term"),
    "Q5": ("student_basic_info_curriculum_system", "curriculum_system_code"),
}

GUIDED_CURRENT_GRADE_APPEND_OPTIONS = [
    _opt("G9", "G9 (初三)"),
    _opt("G10", "G10 (高一)"),
    _opt("G11", "G11 (高二)"),
    _opt("G12", "G12 (高三)"),
    _opt("Gap Year", "Gap Year"),
    _opt("TRANSFER_YEAR_1", "大一转学申请"),
    _opt("OTHER", "其他"),
]

GUIDED_CURRICULUM_LABEL_OVERRIDES = {
    "CHINESE_HIGH_SCHOOL": "普高",
    "US_HIGH_SCHOOL": "国际学校美高体系",
    "INTERNATIONAL_OTHER": "国际学校其他体系",
    "OTHER": "其他",
}

GUIDED_CURRICULUM_ORDER = [
    "A_LEVEL",
    "AP",
    "IB",
    "CHINESE_HIGH_SCHOOL",
    "US_HIGH_SCHOOL",
    "INTERNATIONAL_OTHER",
    "OTHER",
]

SUPPORTED_GUIDED_LANGUAGE_TEST_TYPES = {
    "IELTS",
    "TOEFL_IBT",
    "TOEFL_ESSENTIALS",
    "DET",
    "PTE",
    "LANGUAGECERT",
    "CAMBRIDGE",
    "OTHER",
}

GUIDED_LANGUAGE_TEST_LABEL_OVERRIDES = {
    "IELTS": "雅思",
    "TOEFL_IBT": "托福 iBT",
    "TOEFL_ESSENTIALS": "托福 Essentials",
    "DET": "多邻国",
    "PTE": "PTE",
    "LANGUAGECERT": "LanguageCert",
    "CAMBRIDGE": "剑桥英语",
    "OTHER": "其他语言考试",
}

GUIDED_LANGUAGE_TEST_ORDER = [
    "IELTS",
    "TOEFL_IBT",
    "TOEFL_ESSENTIALS",
    "DET",
    "PTE",
    "LANGUAGECERT",
    "CAMBRIDGE",
    "OTHER",
]

GUIDED_LANGUAGE_TEST_DETAIL_TABLES = {
    "IELTS": "student_language_ielts",
    "TOEFL_IBT": "student_language_toefl_ibt",
    "TOEFL_ESSENTIALS": "student_language_toefl_essentials",
    "DET": "student_language_det",
    "PTE": "student_language_pte",
    "LANGUAGECERT": "student_language_languagecert",
    "CAMBRIDGE": "student_language_cambridge",
    "OTHER": "student_language_other",
}

GUIDED_LANGUAGE_DETAIL_ALLOWED_FIELDS = {
    "student_language_ielts": {
        "status_code",
        "test_date",
        "overall_score",
        "reading_score",
        "listening_score",
        "speaking_score",
        "writing_score",
        "estimated_total",
        "evidence_level_code",
        "normalized_index_100",
        "is_best_score",
        "notes",
    },
    "student_language_toefl_ibt": {
        "status_code",
        "test_date",
        "total_score",
        "reading_score",
        "listening_score",
        "speaking_score",
        "writing_score",
        "estimated_total",
        "evidence_level_code",
        "normalized_index_100",
        "is_best_score",
        "notes",
    },
    "student_language_toefl_essentials": {
        "status_code",
        "test_date",
        "core_score_1_12",
        "literacy_1_12",
        "conversation_1_12",
        "comprehension_1_12",
        "estimated_total",
        "evidence_level_code",
        "normalized_index_100",
        "is_best_score",
        "notes",
    },
    "student_language_det": {
        "status_code",
        "test_date",
        "total_score",
        "literacy_score",
        "comprehension_score",
        "conversation_score",
        "production_score",
        "estimated_total",
        "evidence_level_code",
        "normalized_index_100",
        "is_best_score",
        "notes",
    },
    "student_language_pte": {
        "status_code",
        "test_date",
        "total_score",
        "reading_score",
        "listening_score",
        "speaking_score",
        "writing_score",
        "estimated_total",
        "evidence_level_code",
        "normalized_index_100",
        "is_best_score",
        "notes",
    },
    "student_language_languagecert": {
        "status_code",
        "test_date",
        "total_score",
        "reading_score",
        "listening_score",
        "speaking_score",
        "writing_score",
        "cefr_level_code",
        "estimated_total",
        "evidence_level_code",
        "normalized_index_100",
        "is_best_score",
        "notes",
    },
    "student_language_cambridge": {
        "status_code",
        "test_date",
        "total_score",
        "reading_score",
        "use_of_english_score",
        "listening_score",
        "speaking_score",
        "writing_score",
        "cefr_level_code",
        "estimated_total",
        "evidence_level_code",
        "normalized_index_100",
        "is_best_score",
        "notes",
    },
    "student_language_other": {
        "status_code",
        "test_date",
        "test_name",
        "score_total",
        "band_or_scale_desc",
        "cefr_level_code",
        "score_breakdown_json",
        "estimated_total",
        "evidence_level_code",
        "normalized_index_100",
        "is_best_score",
        "notes",
    },
}

GUIDED_LANGUAGE_NUMERIC_FIELDS = {
    "overall_score",
    "reading_score",
    "listening_score",
    "speaking_score",
    "writing_score",
    "total_score",
    "core_score_1_12",
    "literacy_1_12",
    "conversation_1_12",
    "comprehension_1_12",
    "literacy_score",
    "comprehension_score",
    "conversation_score",
    "production_score",
    "use_of_english_score",
    "score_total",
    "estimated_total",
    "normalized_index_100",
}


def _form_field_options(
    db: Session | None,
    *,
    table_name: str,
    field_name: str,
) -> list[dict[str, str]]:
    if db is None:
        return []

    dictionary_key = STATIC_FIELD_OPTIONS.get((table_name, field_name))
    if dictionary_key:
        loader = DICTIONARY_OPTION_LOADERS.get(dictionary_key)
        if loader:
            return deepcopy(loader(db))

    enum_values = ENUM_FIELD_OPTIONS.get((table_name, field_name))
    if enum_values:
        option_labels = ENUM_OPTION_LABELS.get((table_name, field_name), {})
        return [{"value": item, "label": option_labels.get(item, item)} for item in enum_values]

    return []


def _guided_fields_from_profile_form_meta(db: Session | None, *, table_name: str) -> list[dict[str, Any]]:
    if db is None:
        return []

    try:
        form_meta = build_business_profile_form_meta(db)
    except Exception:
        logger.exception("[标准问卷建档] 读取正式档案表单元数据失败：table=%s", table_name)
        return []

    table_meta = form_meta.get("tables", {}).get(table_name) or {}
    fields: list[dict[str, Any]] = []
    for field in table_meta.get("fields") or []:
        if field.get("hidden"):
            continue
        input_type = str(field.get("input_type") or "text")
        kind = input_type if input_type in {"select", "textarea"} else "text"
        fields.append(
            _field(
                str(field.get("name") or ""),
                str(field.get("label") or field.get("name") or ""),
                kind,
                input_type=input_type,
                options=deepcopy(field.get("options") or []),
                helper_text=field.get("helper_text"),
            )
        )
    return [field for field in fields if field["name"]]


def _fallback_language_detail_fields(table_name: str | None) -> list[dict[str, Any]]:
    if table_name == "student_language_ielts":
        return [
            _field("status_code", "成绩状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("test_date", "考试日期", input_type="date"),
            _field("overall_score", "总分", input_type="number"),
            _field("listening_score", "听力分", input_type="number"),
            _field("reading_score", "阅读分", input_type="number"),
            _field("writing_score", "写作分", input_type="number"),
            _field("speaking_score", "口语分", input_type="number"),
        ]
    if table_name == "student_language_toefl_ibt":
        return [
            _field("status_code", "成绩状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("test_date", "考试日期", input_type="date"),
            _field("total_score", "总分", input_type="number"),
            _field("reading_score", "阅读分", input_type="number"),
            _field("listening_score", "听力分", input_type="number"),
            _field("speaking_score", "口语分", input_type="number"),
            _field("writing_score", "写作分", input_type="number"),
        ]
    if table_name == "student_language_toefl_essentials":
        return [
            _field("status_code", "成绩状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("test_date", "考试日期", input_type="date"),
            _field("core_score_1_12", "核心总分", input_type="number"),
            _field("literacy_1_12", "读写能力分", input_type="number"),
            _field("conversation_1_12", "会话能力分", input_type="number"),
            _field("comprehension_1_12", "理解能力分", input_type="number"),
        ]
    if table_name == "student_language_det":
        return [
            _field("status_code", "成绩状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("test_date", "考试日期", input_type="date"),
            _field("total_score", "总分", input_type="number"),
            _field("literacy_score", "Literacy 分", input_type="number"),
            _field("comprehension_score", "Comprehension 分", input_type="number"),
            _field("conversation_score", "Conversation 分", input_type="number"),
            _field("production_score", "Production 分", input_type="number"),
        ]
    if table_name in {"student_language_pte", "student_language_languagecert"}:
        fields = [
            _field("status_code", "成绩状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("test_date", "考试日期", input_type="date"),
            _field("total_score", "总分", input_type="number"),
            _field("reading_score", "阅读分", input_type="number"),
            _field("listening_score", "听力分", input_type="number"),
            _field("speaking_score", "口语分", input_type="number"),
            _field("writing_score", "写作分", input_type="number"),
        ]
        if table_name == "student_language_languagecert":
            fields.append(_field("cefr_level_code", "CEFR 等级", "select", options=CEFR_LEVEL_OPTIONS))
        return fields
    if table_name == "student_language_cambridge":
        return [
            _field("status_code", "成绩状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("test_date", "考试日期", input_type="date"),
            _field("total_score", "总分", input_type="number"),
            _field("reading_score", "阅读分", input_type="number"),
            _field("use_of_english_score", "Use of English 分", input_type="number"),
            _field("listening_score", "听力分", input_type="number"),
            _field("speaking_score", "口语分", input_type="number"),
            _field("writing_score", "写作分", input_type="number"),
            _field("cefr_level_code", "CEFR 等级", "select", options=CEFR_LEVEL_OPTIONS),
        ]
    if table_name == "student_language_other":
        return [
            _field("status_code", "成绩状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("test_date", "考试日期", input_type="date"),
            _field("test_name", "考试名称"),
            _field("score_total", "总分", input_type="number"),
            _field("band_or_scale_desc", "分制说明"),
            _field("cefr_level_code", "CEFR 等级", "select", options=CEFR_LEVEL_OPTIONS),
            _field("score_breakdown_json", "分项成绩 JSON", "textarea"),
        ]
    return []


def _merge_option_overrides(
    options: list[dict[str, str]],
    *,
    label_overrides: dict[str, str] | None = None,
    append_options: list[dict[str, str]] | None = None,
) -> list[dict[str, str]]:
    label_overrides = label_overrides or {}
    result: list[dict[str, str]] = []
    seen: set[str] = set()

    for option in options:
        value = str(option.get("value") or "").strip()
        if not value or value in seen:
            continue
        result.append(
            {
                "value": value,
                "label": label_overrides.get(value, str(option.get("label") or value)),
            }
        )
        seen.add(value)

    for option in append_options or []:
        value = str(option.get("value") or "").strip()
        if not value or value in seen:
            continue
        result.append(
            {
                "value": value,
                "label": str(option.get("label") or value),
            }
        )
        seen.add(value)

    return result


def _order_options(options: list[dict[str, str]], ordered_values: list[str]) -> list[dict[str, str]]:
    order_index = {value: index for index, value in enumerate(ordered_values)}
    return sorted(options, key=lambda item: (order_index.get(item["value"], len(order_index)), item["label"]))


def _question_options(
    db: Session | None,
    *,
    question_code: str,
) -> list[dict[str, str]] | None:
    if question_code == "Q1":
        return _merge_option_overrides(
            _form_field_options(db, table_name="student_basic_info", field_name="current_grade"),
            append_options=GUIDED_CURRENT_GRADE_APPEND_OPTIONS,
        )

    if question_code == "Q3":
        base_options = _form_field_options(db, table_name="student_basic_info", field_name="CTRY_CODE_VAL")
        if not base_options:
            base_options = deepcopy(QUESTION_BY_CODE["Q3"].get("options") or [])
        return _merge_option_overrides(base_options, append_options=[_opt("OTHER", "其他")])

    if question_code == "Q4":
        base_options = _form_field_options(db, table_name="student_basic_info", field_name="MAJ_CODE_VAL")
        if not base_options:
            base_options = deepcopy(QUESTION_BY_CODE["Q4"].get("options") or [])
        return _merge_option_overrides(base_options, append_options=[_opt("UNDECIDED", "还不确定")])

    if question_code == "Q5":
        options = _merge_option_overrides(
            _form_field_options(db, table_name="student_basic_info_curriculum_system", field_name="curriculum_system_code"),
            label_overrides=GUIDED_CURRICULUM_LABEL_OVERRIDES,
            append_options=[_opt(value, GUIDED_CURRICULUM_LABEL_OVERRIDES.get(value, value)) for value in GUIDED_CURRICULUM_ORDER],
        )
        return _order_options(options, GUIDED_CURRICULUM_ORDER)

    field_source = QUESTION_OPTION_FIELD_SOURCES.get(question_code)
    if field_source:
        table_name, field_name = field_source
        return _form_field_options(db, table_name=table_name, field_name=field_name)

    if question_code == "Q9":
        options = _merge_option_overrides(
            [
                option
                for option in _form_field_options(
                    db,
                    table_name="student_language",
                    field_name="best_test_type_code",
                )
                if option.get("value") in SUPPORTED_GUIDED_LANGUAGE_TEST_TYPES
            ],
            label_overrides=GUIDED_LANGUAGE_TEST_LABEL_OVERRIDES,
            append_options=[_opt(value, label) for value, label in GUIDED_LANGUAGE_TEST_LABEL_OVERRIDES.items()],
        )
        return _order_options(
            [
                option
                for option in options
                if option.get("value") in SUPPORTED_GUIDED_LANGUAGE_TEST_TYPES
            ],
            GUIDED_LANGUAGE_TEST_ORDER,
        )

    return None


STANDARDIZED_TEST_TYPE_OPTIONS = [_opt("SAT", "SAT"), _opt("ACT", "ACT")]
SCORE_STATUS_OPTIONS = [
    _opt("SCORED", "已出分"),
    _opt("PLANNED", "计划参加"),
    _opt("ESTIMATED", "预估"),
]
CEFR_LEVEL_OPTIONS = [
    _opt("A1", "入门级"),
    _opt("A2", "初级"),
    _opt("B1", "中级"),
    _opt("B2", "中高级"),
    _opt("C1", "高级"),
    _opt("C2", "精通级"),
    _opt("UNKNOWN", "未知"),
]
A_LEVEL_GRADE_OPTIONS = [_opt(value) for value in ["A*", "A", "B", "C", "D", "E", "U", "NA"]]
A_LEVEL_BOARD_OPTIONS = [
    _opt("CAIE", "剑桥国际"),
    _opt("EDEXCEL", "爱德思"),
    _opt("AQA", "AQA"),
    _opt("OCR", "OCR"),
    _opt("OTHER", "其他"),
    _opt("UNKNOWN", "未知"),
]
AP_SCORE_OPTIONS = [_opt(str(value), str(value)) for value in [5, 4, 3, 2, 1]]
IB_LEVEL_OPTIONS = [_opt("HL", "HL 高阶"), _opt("SL", "SL 标准级")]
IB_SCORE_OPTIONS = [_opt(str(value), str(value)) for value in [7, 6, 5, 4, 3, 2, 1]]

COMPETITION_FIELD_OPTIONS = [
    _opt("MATH", "数学"),
    _opt("CS", "计算机"),
    _opt("PHYSICS", "物理"),
    _opt("CHEM", "化学"),
    _opt("BIO", "生物"),
    _opt("ECON", "经济"),
    _opt("DEBATE", "辩论"),
    _opt("WRITING", "写作"),
    _opt("OTHER", "其他"),
]
COMPETITION_TIER_OPTIONS = [
    _opt("T1", "第一梯队"),
    _opt("T2", "第二梯队"),
    _opt("T3", "第三梯队"),
    _opt("T4", "第四梯队"),
    _opt("UNKNOWN", "未知"),
]
COMPETITION_LEVEL_OPTIONS = [
    _opt("SCHOOL", "校级"),
    _opt("CITY", "市级"),
    _opt("PROVINCE", "省级"),
    _opt("NATIONAL", "国家级"),
    _opt("INTERNATIONAL", "国际级"),
]
ACTIVITY_CATEGORY_OPTIONS = [
    _opt("LEADERSHIP", "领导力活动"),
    _opt("ACADEMIC", "学术活动"),
    _opt("SPORTS", "体育活动"),
    _opt("ARTS", "艺术活动"),
    _opt("COMMUNITY", "社区活动"),
    _opt("ENTREPRENEURSHIP", "创业活动"),
    _opt("OTHER", "其他"),
]
ACTIVITY_ROLE_OPTIONS = [
    _opt("FOUNDER", "创始人"),
    _opt("PRESIDENT", "负责人"),
    _opt("CORE_MEMBER", "核心成员"),
    _opt("MEMBER", "成员"),
    _opt("OTHER", "其他"),
]
PROJECT_TYPE_OPTIONS = [
    _opt("RESEARCH", "科研项目"),
    _opt("INTERNSHIP", "实习经历"),
    _opt("ENGINEERING_PROJECT", "工程项目"),
    _opt("STARTUP", "创业项目"),
    _opt("CREATIVE_PROJECT", "创意项目"),
    _opt("VOLUNTEER_WORK", "志愿工作"),
    _opt("OTHER", "其他"),
]
PROJECT_FIELD_OPTIONS = [
    _opt("CS", "计算机"),
    _opt("ECON", "经济"),
    _opt("FIN", "金融"),
    _opt("BIO", "生物"),
    _opt("PHYS", "物理"),
    _opt("DESIGN", "设计"),
    _opt("OTHER", "其他"),
]
RELEVANCE_OPTIONS = [_opt("HIGH", "高相关"), _opt("MEDIUM", "中相关"), _opt("LOW", "低相关")]
COMPETITION_EVIDENCE_OPTIONS = [
    _opt("CERTIFICATE", "证书"),
    _opt("LINK", "链接"),
    _opt("SCHOOL_CONFIRMATION", "学校证明"),
    _opt("NONE", "无"),
]
ACTIVITY_EVIDENCE_OPTIONS = [
    _opt("LINK", "链接"),
    _opt("SCHOOL_CONFIRMATION", "学校证明"),
    _opt("PHOTO", "照片"),
    _opt("NONE", "无"),
]
PROJECT_EVIDENCE_OPTIONS = [
    _opt("LINK", "链接"),
    _opt("MENTOR_LETTER", "导师证明"),
    _opt("EMPLOYER_CONFIRMATION", "单位证明"),
    _opt("NONE", "无"),
]

GUIDED_CURRICULUM_TO_DB_CODE = {
    "A_LEVEL": "A_LEVEL",
    "AP": "AP",
    "IB": "IB",
    "CHINESE_HIGH_SCHOOL": "CHINESE_HIGH_SCHOOL",
    "US_HIGH_SCHOOL": "US_HIGH_SCHOOL",
    "INTERNATIONAL_OTHER": "INTERNATIONAL_OTHER",
    "OTHER": "OTHER",
}


def _fields_for_question(
    question_code: str,
    answers: dict[str, dict[str, Any]],
    db: Session | None = None,
) -> list[dict[str, Any]]:
    if question_code == "Q8":
        curriculum = _selected_value(answers.get("Q5"))
        if curriculum == "CHINESE_HIGH_SCHOOL":
            grading_system_options = _form_field_options(
                db,
                table_name="student_academic_chinese_high_school_profile",
                field_name="grading_system_code",
            )
            subject_options = _form_field_options(
                db,
                table_name="student_academic_chinese_high_school_subject",
                field_name="chs_subject_id",
            )
            fields = [
                _field("grading_system_code", "评分体系", "select", options=grading_system_options),
                _field("average_score_100", "百分制平均分"),
                _field("gpa", "GPA"),
                _field("gpa_scale", "GPA 满分"),
            ]
            for index in range(1, 7):
                fields.extend(
                    [
                        _field(f"chs_subject_{index}_id", f"普高科目 {index}", "select", options=subject_options),
                        _field(f"chs_subject_{index}_score_100", f"科目 {index} 百分制分数"),
                    ]
                )
            return fields
        if curriculum:
            return [
                _field("gpa", "GPA"),
                _field("gpa_scale", "满分"),
                _field(
                    "is_weighted",
                    "是否加权",
                    "select",
                    options=[_opt("yes", "是"), _opt("no", "否"), _opt("unknown", "不清楚")],
                ),
            ]
        return []

    if question_code == "Q10":
        language_type = _selected_value(answers.get("Q9"))
        table_name = GUIDED_LANGUAGE_TEST_DETAIL_TABLES.get(language_type or "")
        if not table_name:
            return []
        return _guided_fields_from_profile_form_meta(db, table_name=table_name) or _fallback_language_detail_fields(table_name)

    if question_code == "Q13_A_LEVEL":
        return [
            _field("a_level_board", "A-Level 考试局", "select", options=A_LEVEL_BOARD_OPTIONS),
        ]

    if question_code == "Q13_AS":
        return [
            _field("a_level_board", "AS 考试局", "select", options=A_LEVEL_BOARD_OPTIONS),
        ]

    if question_code == "Q13_IB":
        return [
            _field("ib_total", "IB 总分"),
        ]

    if question_code == "Q13_SAT":
        return [
            _field("sat_status", "考试状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("sat_total", "SAT 总分"),
            _field("sat_ebrw", "EBRW"),
            _field("sat_math", "Math"),
        ]

    if question_code == "Q13_ACT":
        return [
            _field("act_status", "考试状态", "select", options=SCORE_STATUS_OPTIONS),
            _field("act_total", "ACT 总分"),
            _field("act_english", "English"),
            _field("act_math", "Math"),
            _field("act_reading", "Reading"),
            _field("act_science", "Science"),
        ]

    if question_code == "Q13":
        selected_tests = set(_selected_values(answers.get("Q12")))
        fields: list[dict[str, Any]] = []
        a_level_subject_options = _form_field_options(
            db,
            table_name="student_academic_a_level_subject",
            field_name="al_subject_id",
        )
        a_level_stage_options = _form_field_options(
            db,
            table_name="student_academic_a_level_subject",
            field_name="stage_code",
        ) or [_opt("FULL_A_LEVEL", "A-Level"), _opt("AS", "AS")]
        ap_course_options = _form_field_options(
            db,
            table_name="student_academic_ap_course",
            field_name="ap_course_id",
        )
        ib_subject_options = _form_field_options(
            db,
            table_name="student_academic_ib_subject",
            field_name="ib_subject_id",
        )
        if selected_tests & {"A_LEVEL", "AS"}:
            fields.append(_field("a_level_board", "A-Level / AS 考试局", "select", options=A_LEVEL_BOARD_OPTIONS))
            for index in range(1, 5):
                fields.extend(
                    [
                        _field(
                            f"a_level_subject_{index}",
                            f"A-Level / AS 科目 {index}",
                            "select",
                            options=a_level_subject_options,
                        ),
                        _field(f"a_level_stage_{index}", f"科目 {index} 阶段", "select", options=a_level_stage_options),
                        _field(f"a_level_grade_{index}", f"科目 {index} 成绩", "select", options=A_LEVEL_GRADE_OPTIONS),
                        _field(f"a_level_status_{index}", f"科目 {index} 状态", "select", options=SCORE_STATUS_OPTIONS),
                    ]
                )
        if "AP" in selected_tests:
            for index in range(1, 5):
                fields.extend(
                    [
                        _field(f"ap_subject_{index}", f"AP 科目 {index}", "select", options=ap_course_options),
                        _field(f"ap_score_{index}", f"AP 科目 {index} 分数", "select", options=AP_SCORE_OPTIONS),
                        _field(f"ap_status_{index}", f"AP 科目 {index} 状态", "select", options=SCORE_STATUS_OPTIONS),
                    ]
                )
        if "IB" in selected_tests:
            fields.extend(
                [
                    _field("ib_total", "IB 总分"),
                ]
            )
            for index in range(1, 4):
                fields.extend(
                    [
                        _field(f"ib_subject_{index}", f"IB 科目 {index}", "select", options=ib_subject_options),
                        _field(f"ib_level_{index}", f"IB 科目 {index} 级别", "select", options=IB_LEVEL_OPTIONS),
                        _field(f"ib_score_{index}", f"IB 科目 {index} 分数", "select", options=IB_SCORE_OPTIONS),
                    ]
                )
        if "SAT" in selected_tests:
            fields.extend(
                [
                    _field("sat_test_type", "考试类型", "select", options=[_opt("SAT", "SAT")]),
                    _field("sat_status", "考试状态", "select", options=SCORE_STATUS_OPTIONS),
                    _field("sat_total", "SAT 总分"),
                    _field("sat_ebrw", "EBRW"),
                    _field("sat_math", "Math"),
                ]
            )
        if "ACT" in selected_tests:
            fields.extend(
                [
                    _field("act_test_type", "考试类型", "select", options=[_opt("ACT", "ACT")]),
                    _field("act_status", "考试状态", "select", options=SCORE_STATUS_OPTIONS),
                    _field("act_total", "ACT 总分"),
                    _field("act_english", "English"),
                    _field("act_math", "Math"),
                    _field("act_reading", "Reading"),
                    _field("act_science", "Science"),
                ]
            )
        return fields

    if question_code in {"Q15", "Q16", "Q17"}:
        label = {"Q15": "竞赛", "Q16": "活动", "Q17": "项目"}[question_code]
        base_fields = [
            _field(
                "has_experience",
                f"是否有{label}经历",
                "select",
                options=[_opt("yes", "有"), _opt("no", "暂时没有")],
            ),
            _field("name", f"{label}名称"),
        ]
        if question_code == "Q15":
            base_fields.extend(
                [
                    _field("competition_field", "竞赛领域", "select", options=COMPETITION_FIELD_OPTIONS),
                    _field("competition_tier", "竞赛层级", "select", options=COMPETITION_TIER_OPTIONS),
                    _field("competition_level", "竞赛级别", "select", options=COMPETITION_LEVEL_OPTIONS),
                    _field("result_or_output", "结果描述"),
                    _field("evidence_type", "佐证类型", "select", options=COMPETITION_EVIDENCE_OPTIONS),
                ]
            )
        elif question_code == "Q16":
            base_fields.extend(
                [
                    _field("activity_category", "活动类别", "select", options=ACTIVITY_CATEGORY_OPTIONS),
                    _field("activity_role", "活动角色", "select", options=ACTIVITY_ROLE_OPTIONS),
                    _field("duration_months", "持续月数"),
                    _field("weekly_hours", "每周投入小时"),
                    _field("result_or_output", "影响 / 获奖 / 媒体报道"),
                    _field("evidence_type", "佐证类型", "select", options=ACTIVITY_EVIDENCE_OPTIONS),
                ]
            )
        else:
            base_fields.extend(
                [
                    _field("project_type", "项目类型", "select", options=PROJECT_TYPE_OPTIONS),
                    _field("project_field", "项目领域", "select", options=PROJECT_FIELD_OPTIONS),
                    _field("relevance_to_major", "与专业相关性", "select", options=RELEVANCE_OPTIONS),
                    _field("hours_total", "总投入小时"),
                    _field("result_or_output", "产出 / 结果"),
                    _field("evidence_type", "佐证类型", "select", options=PROJECT_EVIDENCE_OPTIONS),
                ]
            )
        base_fields.append(_field("evidence", "证明材料或补充说明", "textarea"))
        return base_fields
    return []


def _row_fields_for_question(
    question_code: str,
    db: Session | None = None,
) -> list[dict[str, Any]]:
    if question_code in {"Q13_A_LEVEL", "Q13_AS"}:
        subject_options = _form_field_options(
            db,
            table_name="student_academic_a_level_subject",
            field_name="al_subject_id",
        )
        return [
            _field("subject", "科目", "select", options=subject_options),
            _field("grade", "成绩", "select", options=A_LEVEL_GRADE_OPTIONS),
            _field("status", "状态", "select", options=SCORE_STATUS_OPTIONS),
        ]

    if question_code == "Q13_AP":
        course_options = _form_field_options(
            db,
            table_name="student_academic_ap_course",
            field_name="ap_course_id",
        )
        return [
            _field("subject", "AP 科目", "select", options=course_options),
            _field("score", "分数", "select", options=AP_SCORE_OPTIONS),
            _field("status", "状态", "select", options=SCORE_STATUS_OPTIONS),
        ]

    if question_code == "Q13_IB":
        subject_options = _form_field_options(
            db,
            table_name="student_academic_ib_subject",
            field_name="ib_subject_id",
        )
        return [
            _field("subject", "IB 科目", "select", options=subject_options),
            _field("level", "级别", "select", options=IB_LEVEL_OPTIONS),
            _field("score", "分数", "select", options=IB_SCORE_OPTIONS),
            _field("status", "状态", "select", options=SCORE_STATUS_OPTIONS),
        ]

    return []


def _json_dump(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _json_load(value: Any, default: Any = None) -> Any:
    if value is None:
        return default
    if isinstance(value, (dict, list)):
        return value
    try:
        return json.loads(value)
    except (TypeError, json.JSONDecodeError):
        return default


def _row_to_dict(row: Any) -> dict[str, Any] | None:
    if row is None:
        return None
    return dict(row._mapping)


def _selected_value(answer: dict[str, Any] | None) -> str | None:
    if not isinstance(answer, dict):
        return None
    value = answer.get("selected_value")
    return str(value) if value is not None and str(value) else None


def _selected_values(answer: dict[str, Any] | None) -> list[str]:
    if not isinstance(answer, dict):
        return []
    values = answer.get("selected_values")
    if not isinstance(values, list):
        return []
    return [str(value) for value in values if value is not None and str(value)]


def _option_label(question: dict[str, Any], value: str | None) -> str:
    if not value:
        return ""
    for option in question.get("options") or []:
        if option.get("value") == value:
            return str(option.get("label") or value)
    return value


def _option_labels(question: dict[str, Any], values: list[str]) -> list[str]:
    return [_option_label(question, value) for value in values]


def _answer_display_text(question: dict[str, Any], answer: dict[str, Any]) -> str:
    if answer.get("skipped") is True:
        return "已跳过"
    question_type = question.get("type")
    if question_type == "single":
        label = _option_label(question, _selected_value(answer))
        custom_text = str(answer.get("custom_text") or "").strip()
        return f"{label}：{custom_text}" if label and custom_text else label
    if question_type == "multi":
        values = _selected_values(answer)
        labels = _option_labels(question, values)
        custom_text = str(answer.get("custom_text") or "").strip()
        return "、".join(labels + ([custom_text] if custom_text else []))
    if question_type == "repeatable_form":
        pairs: list[str] = []
        for field in question.get("fields") or []:
            value = answer.get(field["name"])
            if value is None or value == "":
                continue
            if field.get("kind") == "select":
                field_question = {"options": field.get("options") or []}
                value = _option_label(field_question, str(value))
            pairs.append(f"{field['label']}：{value}")

        row_fields = question.get("row_fields") or []
        for row in answer.get("rows") or []:
            if not isinstance(row, dict):
                continue
            row_pairs: list[str] = []
            for field in row_fields:
                value = row.get(field["name"])
                if value is None or value == "":
                    continue
                if field.get("kind") == "select":
                    field_question = {"options": field.get("options") or []}
                    value = _option_label(field_question, str(value))
                row_pairs.append(f"{field['label']}：{value}")
            if row_pairs:
                pairs.append("；".join(row_pairs))
        return "；".join(pairs) if pairs else "已填写"
    fields = question.get("fields") or []
    if not fields:
        fields = _fields_for_question(question["code"], {})
    pairs = []
    for field in fields:
        value = answer.get(field["name"])
        if value is None or value == "":
            continue
        if field.get("kind") == "select":
            field_question = {"options": field.get("options") or []}
            value = _option_label(field_question, str(value))
        pairs.append(f"{field['label']}：{value}")
    return "；".join(pairs) if pairs else "已填写"


def _normalize_answer(question: dict[str, Any], raw_answer: dict[str, Any], answers: dict[str, dict[str, Any]]) -> dict[str, Any]:
    question_type = question.get("type")
    if question_type == "single":
        value = raw_answer.get("selected_value")
        if value is None:
            value = raw_answer.get("value")
        return {
            "selected_value": str(value) if value is not None else "",
            "custom_text": str(raw_answer.get("custom_text") or "").strip(),
        }
    if question_type == "multi":
        values = raw_answer.get("selected_values")
        if values is None:
            values = raw_answer.get("values")
        if not isinstance(values, list):
            values = []
        normalized = [str(value) for value in values if value is not None and str(value)]
        exclusive_values = {str(value) for value in question.get("exclusive_option_values") or []}
        if exclusive_values and any(value in exclusive_values for value in normalized):
            non_exclusive_values = [value for value in normalized if value not in exclusive_values]
            normalized = non_exclusive_values or [next(value for value in normalized if value in exclusive_values)]
        max_select = int(question.get("max_select") or 0)
        if max_select > 0:
            normalized = normalized[:max_select]
        return {
            "selected_values": normalized,
            "custom_text": str(raw_answer.get("custom_text") or "").strip(),
        }
    if question_type == "repeatable_form":
        normalized: dict[str, Any] = {}
        fields = question.get("fields") or _fields_for_question(question["code"], answers)
        for field in fields:
            value = raw_answer.get(field["name"])
            normalized[field["name"]] = "" if value is None else str(value).strip()

        row_fields = question.get("row_fields") or _row_fields_for_question(question["code"])
        rows = raw_answer.get("rows")
        if not isinstance(rows, list):
            rows = []
        normalized_rows: list[dict[str, str]] = []
        for row in rows:
            if not isinstance(row, dict):
                continue
            normalized_row: dict[str, str] = {}
            for field in row_fields:
                value = row.get(field["name"])
                normalized_row[field["name"]] = "" if value is None else str(value).strip()
            if any(normalized_row.values()):
                normalized_rows.append(normalized_row)
        normalized["rows"] = normalized_rows
        return normalized
    normalized = {}
    fields = question.get("fields") or _fields_for_question(question["code"], answers)
    for field in fields:
        value = raw_answer.get(field["name"])
        normalized[field["name"]] = "" if value is None else str(value).strip()
    return normalized


def _answer_is_meaningful(question: dict[str, Any], answer: dict[str, Any]) -> bool:
    if answer.get("skipped") is True:
        return True
    if question.get("type") == "single":
        return bool(_selected_value(answer) or str(answer.get("custom_text") or "").strip())
    if question.get("type") == "multi":
        return bool(_selected_values(answer) or str(answer.get("custom_text") or "").strip())
    if question.get("type") == "repeatable_form":
        if any(str(answer.get(field["name"]) or "").strip() for field in question.get("fields") or []):
            return True
        for row in answer.get("rows") or []:
            if isinstance(row, dict) and any(str(value or "").strip() for value in row.values()):
                return True
        return False
    return any(str(value or "").strip() for value in answer.values())


def _has_scored_external_exam_answer(q13: dict[str, Any]) -> bool:
    if not isinstance(q13, dict):
        return False

    def has_any_value(*field_names: str) -> bool:
        return any(str(q13.get(field_name) or "").strip() for field_name in field_names)

    for index in range(1, 5):
        status = str(q13.get(f"a_level_status_{index}") or "").strip()
        if status == "SCORED":
            return True
        if status not in {"PLANNED", "ESTIMATED"} and has_any_value(f"a_level_subject_{index}", f"a_level_grade_{index}"):
            return True

        ap_status = str(q13.get(f"ap_status_{index}") or "").strip()
        if ap_status == "SCORED":
            return True
        if ap_status not in {"PLANNED", "ESTIMATED"} and has_any_value(f"ap_subject_{index}", f"ap_score_{index}"):
            return True

    if has_any_value("ib_total"):
        return True
    for index in range(1, 4):
        if has_any_value(f"ib_subject_{index}", f"ib_score_{index}"):
            return True

    for prefix in ("sat", "act"):
        status = str(q13.get(f"{prefix}_status") or "").strip()
        if status == "SCORED":
            return True
        score_fields = (
            ("sat_total", "sat_ebrw", "sat_math")
            if prefix == "sat"
            else ("act_total", "act_english", "act_math", "act_reading", "act_science")
        )
        if status not in {"PLANNED", "ESTIMATED"} and has_any_value(*score_fields):
            return True

    return False


def _has_scored_external_exam_answer_from_answers(answers: dict[str, dict[str, Any]]) -> bool:
    for exam_type in _selected_standardized_tests_in_order(answers):
        answer = answers.get(STANDARDIZED_SCORE_QUESTION_TEMPLATES[exam_type]["code"]) or {}
        if exam_type in {"A_LEVEL", "AS"}:
            for row in answer.get("rows") or []:
                if not isinstance(row, dict):
                    continue
                status = str(row.get("status") or "").strip()
                if status == "SCORED":
                    return True
                if status not in {"PLANNED", "ESTIMATED"} and str(row.get("grade") or "").strip():
                    return True
        elif exam_type == "AP":
            for row in answer.get("rows") or []:
                if not isinstance(row, dict):
                    continue
                status = str(row.get("status") or "").strip()
                if status == "SCORED":
                    return True
                if status not in {"PLANNED", "ESTIMATED"} and str(row.get("score") or "").strip():
                    return True
        elif exam_type == "IB":
            if str(answer.get("ib_total") or "").strip():
                return True
            for row in answer.get("rows") or []:
                if not isinstance(row, dict):
                    continue
                status = str(row.get("status") or "").strip()
                if status == "SCORED":
                    return True
                if status not in {"PLANNED", "ESTIMATED"} and str(row.get("score") or "").strip():
                    return True
        elif exam_type == "SAT":
            status = str(answer.get("sat_status") or "").strip()
            if status == "SCORED":
                return True
            if status not in {"PLANNED", "ESTIMATED"} and _has_any_value(answer, "sat_total", "sat_ebrw", "sat_math"):
                return True
        elif exam_type == "ACT":
            status = str(answer.get("act_status") or "").strip()
            if status == "SCORED":
                return True
            if status not in {"PLANNED", "ESTIMATED"} and _has_any_value(
                answer,
                "act_total",
                "act_english",
                "act_math",
                "act_reading",
                "act_science",
            ):
                return True

    legacy_q13 = answers.get("Q13")
    return _has_scored_external_exam_answer(legacy_q13 or {})


def _is_question_visible(question: dict[str, Any], answers: dict[str, dict[str, Any]]) -> bool:
    code = question["code"]
    if code in STANDARDIZED_SCORE_QUESTION_BY_CODE:
        return code in {item["code"] for item in _standardized_score_questions(answers)}
    if code == "Q8":
        return bool(_selected_value(answers.get("Q5")))
    if code == "Q10":
        language_type = _selected_value(answers.get("Q9"))
        return bool(language_type and language_type != "NO_SCORE")
    if code == "Q11":
        q9_answer = answers.get("Q9")
        return _selected_value(q9_answer) == "NO_SCORE" or (isinstance(q9_answer, dict) and q9_answer.get("skipped") is True)
    if code == "Q13":
        return False
    if code == "Q14":
        values = set(_selected_values(answers.get("Q12")))
        if not values:
            return False
        if "NONE" in values:
            return True
        return not _has_scored_external_exam_answer_from_answers(answers)
    return True


def _visible_questions(answers: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for question in QUESTIONS:
        if question["code"] == "Q13":
            result.extend(_standardized_score_questions(answers))
            continue
        if _is_question_visible(question, answers):
            result.append(question)
    return result


def _serialize_visible_questions(
    answers: dict[str, dict[str, Any]],
    db: Session | None = None,
) -> list[dict[str, Any]]:
    visible_questions = _visible_questions(answers)
    total = len(visible_questions)
    return [
        _serialize_question(question, answers, db, index=index, total=total)
        for index, question in enumerate(visible_questions, start=1)
    ]


def _serialize_question(
    question: dict[str, Any],
    answers: dict[str, dict[str, Any]],
    db: Session | None = None,
    *,
    index: int | None = None,
    total: int | None = None,
) -> dict[str, Any]:
    result = deepcopy(question)
    resolved_options = _question_options(db, question_code=question["code"])
    if resolved_options is not None:
        result["options"] = resolved_options
    result["index"] = index if index is not None else (QUESTION_ORDER.index(question["code"]) + 1 if question["code"] in QUESTION_ORDER else None)
    if total is not None:
        result["total"] = total
    result["fields"] = _fields_for_question(question["code"], answers, db)
    result["row_fields"] = _row_fields_for_question(question["code"], db)
    return result


def _serialize_session(row: dict[str, Any] | None) -> dict[str, Any] | None:
    if row is None:
        return None
    return {
        "session_id": row["session_id"],
        "student_id": row["student_id"],
        "questionnaire_code": row["questionnaire_code"],
        "questionnaire_version": row["questionnaire_version"],
        "current_question_code": row["current_question_code"],
        "current_question_index": row["current_question_index"],
        "session_status": row["session_status"],
        "current_stage": row["current_stage"],
        "version_no": row["version_no"],
        "last_generated_version_no": row["last_generated_version_no"],
        "started_at": row.get("started_at"),
        "finished_at": row.get("finished_at"),
        "exited_at": row.get("exited_at"),
        "create_time": row.get("create_time"),
        "update_time": row.get("update_time"),
    }


def _get_latest_session(db: Session, *, student_id: str) -> dict[str, Any] | None:
    row = db.execute(
        text(
            """
            SELECT * FROM student_profile_guided_sessions
            WHERE student_id = :student_id
              AND questionnaire_code = :questionnaire_code
              AND delete_flag = '1'
            ORDER BY
              CASE session_status WHEN 'active' THEN 0 WHEN 'exited' THEN 1 ELSE 2 END,
              update_time DESC,
              id DESC
            LIMIT 1
            """
        ),
        {"student_id": student_id, "questionnaire_code": QUESTIONNAIRE_CODE},
    ).first()
    return _row_to_dict(row)


def _get_session_by_id(db: Session, *, session_id: str, student_id: str) -> dict[str, Any]:
    row = db.execute(
        text(
            """
            SELECT * FROM student_profile_guided_sessions
            WHERE session_id = :session_id AND student_id = :student_id AND delete_flag = '1'
            LIMIT 1
            """
        ),
        {"session_id": session_id, "student_id": student_id},
    ).first()
    session = _row_to_dict(row)
    if session is None:
        raise ValueError("固定问卷会话不存在")
    return session


def _load_answers(db: Session, *, session_id: str) -> dict[str, dict[str, Any]]:
    rows = db.execute(
        text(
            """
            SELECT question_code, answer_json
            FROM student_profile_guided_answers
            WHERE session_id = :session_id AND delete_flag = '1'
            """
        ),
        {"session_id": session_id},
    ).mappings()
    result: dict[str, dict[str, Any]] = {}
    for row in rows:
        answer = _json_load(row["answer_json"], {})
        if isinstance(answer, dict):
            result[str(row["question_code"])] = answer
    return result


def _load_answer_rows(db: Session, *, session_id: str) -> list[dict[str, Any]]:
    rows = db.execute(
        text(
            """
            SELECT question_code, question_type, module_code, answer_json, answer_display_text, version_no, update_time
            FROM student_profile_guided_answers
            WHERE session_id = :session_id AND delete_flag = '1'
            ORDER BY id ASC
            """
        ),
        {"session_id": session_id},
    ).mappings()
    return [
        {
            **dict(row),
            "answer_json": _json_load(row["answer_json"], {}),
        }
        for row in rows
    ]


def _load_messages(db: Session, *, session_id: str) -> list[dict[str, Any]]:
    rows = db.execute(
        text(
            """
            SELECT sequence_no, message_role, message_kind, question_code, content, payload_json, create_time
            FROM student_profile_guided_messages
            WHERE session_id = :session_id AND delete_flag = '1'
            ORDER BY sequence_no ASC, id ASC
            """
        ),
        {"session_id": session_id},
    ).mappings()
    return [
        {
            **dict(row),
            "payload_json": _json_load(row["payload_json"], {}),
        }
        for row in rows
    ]


def _next_sequence_no(db: Session, *, session_id: str) -> int:
    value = db.execute(
        text(
            """
            SELECT COALESCE(MAX(sequence_no), 0) + 1
            FROM student_profile_guided_messages
            WHERE session_id = :session_id
            """
        ),
        {"session_id": session_id},
    ).scalar()
    return int(value or 1)


def _insert_message(
    db: Session,
    *,
    session_id: str,
    student_id: str,
    role: str,
    kind: str,
    content: str,
    question_code: str | None = None,
    payload: dict[str, Any] | None = None,
) -> None:
    db.execute(
        text(
            """
            INSERT INTO student_profile_guided_messages
              (session_id, student_id, sequence_no, message_role, message_kind, question_code, content, payload_json)
            VALUES
              (:session_id, :student_id, :sequence_no, :message_role, :message_kind, :question_code, :content, :payload_json)
            """
        ),
        {
            "session_id": session_id,
            "student_id": student_id,
            "sequence_no": _next_sequence_no(db, session_id=session_id),
            "message_role": role,
            "message_kind": kind,
            "question_code": question_code,
            "content": content,
            "payload_json": _json_dump(payload or {}),
        },
    )


def _find_latest_message_id(
    db: Session,
    *,
    session_id: str,
    role: str,
    kind: str,
    question_code: str,
) -> int | None:
    row = db.execute(
        text(
            """
            SELECT id
            FROM student_profile_guided_messages
            WHERE session_id = :session_id
              AND message_role = :message_role
              AND message_kind = :message_kind
              AND question_code = :question_code
              AND delete_flag = '1'
            ORDER BY sequence_no DESC, id DESC
            LIMIT 1
            """
        ),
        {
            "session_id": session_id,
            "message_role": role,
            "message_kind": kind,
            "question_code": question_code,
        },
    ).scalar()
    return int(row) if row is not None else None


def _upsert_question_message(
    db: Session,
    *,
    session_id: str,
    student_id: str,
    role: str,
    kind: str,
    question_code: str,
    content: str,
    payload: dict[str, Any] | None = None,
    replace_existing: bool = False,
) -> None:
    message_id = (
        _find_latest_message_id(
            db,
            session_id=session_id,
            role=role,
            kind=kind,
            question_code=question_code,
        )
        if replace_existing
        else None
    )
    if message_id is None:
        _insert_message(
            db,
            session_id=session_id,
            student_id=student_id,
            role=role,
            kind=kind,
            question_code=question_code,
            content=content,
            payload=payload,
        )
        return

    db.execute(
        text(
            """
            UPDATE student_profile_guided_messages
            SET content = :content,
                payload_json = :payload_json,
                update_time = CURRENT_TIMESTAMP,
                delete_flag = '1'
            WHERE id = :message_id
            """
        ),
        {
            "message_id": message_id,
            "content": content,
            "payload_json": _json_dump(payload or {}),
        },
    )


def _find_next_question(answers: dict[str, dict[str, Any]]) -> dict[str, Any] | None:
    for question in _visible_questions(answers):
        answer = answers.get(question["code"])
        if not isinstance(answer, dict) or not _answer_is_meaningful(question, answer):
            return question
    return None


def _create_session(db: Session, *, student_id: str) -> dict[str, Any]:
    session_id = str(uuid4())
    first_question = QUESTIONS[0]
    db.execute(
        text(
            """
            INSERT INTO student_profile_guided_sessions
              (session_id, student_id, questionnaire_code, questionnaire_version,
               current_question_code, current_question_index, session_status, current_stage)
            VALUES
              (:session_id, :student_id, :questionnaire_code, :questionnaire_version,
               :current_question_code, :current_question_index, 'active', 'collecting')
            """
        ),
        {
            "session_id": session_id,
            "student_id": student_id,
            "questionnaire_code": QUESTIONNAIRE_CODE,
            "questionnaire_version": QUESTIONNAIRE_VERSION,
            "current_question_code": first_question["code"],
            "current_question_index": 0,
        },
    )
    _insert_message(
        db,
        session_id=session_id,
        student_id=student_id,
        role="assistant",
        kind="question",
        question_code=first_question["code"],
        content=first_question["title"],
        payload=_serialize_question(first_question, {}, db, index=1, total=len(_visible_questions({}))),
    )
    db.commit()
    return _get_session_by_id(db, session_id=session_id, student_id=student_id)


def get_or_create_current_bundle(
    db: Session,
    *,
    student_id: str,
    create_if_missing: bool = True,
) -> dict[str, Any]:
    session = _get_latest_session(db, student_id=student_id)
    if session is None and create_if_missing:
        session = _create_session(db, student_id=student_id)
    if session is None:
        return {"session": None, "messages": [], "answers": [], "current_question": None, "questions": _serialize_visible_questions({}, db)}
    return get_session_bundle(db, student_id=student_id, session_id=session["session_id"])


def restart_current_session(db: Session, *, student_id: str) -> dict[str, Any]:
    db.execute(
        text(
            """
            UPDATE student_profile_guided_sessions
            SET delete_flag = '0', session_status = 'exited', exited_at = COALESCE(exited_at, NOW())
            WHERE student_id = :student_id
              AND questionnaire_code = :questionnaire_code
              AND delete_flag = '1'
              AND session_status IN ('active', 'exited', 'completed', 'failed')
            """
        ),
        {"student_id": student_id, "questionnaire_code": QUESTIONNAIRE_CODE},
    )
    db.commit()
    return get_or_create_current_bundle(db, student_id=student_id, create_if_missing=True)


def _upsert_answer(
    db: Session,
    *,
    session_id: str,
    student_id: str,
    question: dict[str, Any],
    answer: dict[str, Any],
    display_text: str,
    version_no: int,
) -> None:
    db.execute(
        text(
            """
            INSERT INTO student_profile_guided_answers
              (session_id, student_id, question_code, question_type, module_code,
               answer_json, answer_display_text, version_no)
            VALUES
              (:session_id, :student_id, :question_code, :question_type, :module_code,
               :answer_json, :answer_display_text, :version_no)
            ON DUPLICATE KEY UPDATE
              question_type = VALUES(question_type),
              module_code = VALUES(module_code),
              answer_json = VALUES(answer_json),
              answer_display_text = VALUES(answer_display_text),
              version_no = VALUES(version_no),
              update_time = CURRENT_TIMESTAMP,
              delete_flag = '1'
            """
        ),
        {
            "session_id": session_id,
            "student_id": student_id,
            "question_code": question["code"],
            "question_type": question["type"],
            "module_code": question.get("module_code"),
            "answer_json": _json_dump(answer),
            "answer_display_text": display_text,
            "version_no": version_no,
        },
    )


def get_session_bundle(db: Session, *, student_id: str, session_id: str) -> dict[str, Any]:
    session = _get_session_by_id(db, session_id=session_id, student_id=student_id)
    answers = _load_answers(db, session_id=session_id)
    visible_questions = _serialize_visible_questions(answers, db)
    serialized_by_code = {question["code"]: question for question in visible_questions}
    current_question = None
    current_code = session.get("current_question_code")
    if current_code and current_code in serialized_by_code:
        current_question = serialized_by_code[current_code]
    if current_question is None and session["session_status"] == "active":
        next_question = _find_next_question(answers)
        current_question = serialized_by_code.get(next_question["code"]) if next_question else None
    return {
        "session": _serialize_session(session),
        "messages": _load_messages(db, session_id=session_id),
        "answers": _load_answer_rows(db, session_id=session_id),
        "current_question": current_question,
        "questions": visible_questions,
        "result": _load_latest_result(db, student_id=student_id, session_id=session_id),
    }


def submit_guided_answer(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    question_code: str,
    raw_answer: dict[str, Any],
) -> dict[str, Any]:
    session = _get_session_by_id(db, session_id=session_id, student_id=student_id)
    if session["session_status"] == "completed":
        raise ValueError("该固定问卷已完成，如需重填请重新开始")
    if question_code not in QUESTION_BY_CODE:
        raise ValueError("题目不存在")

    answers = _load_answers(db, session_id=session_id)
    question = QUESTION_BY_CODE[question_code]
    if not _is_question_visible(question, answers):
        raise ValueError("当前题目不在可作答范围内")

    existing_answer = answers.get(question_code)
    is_editing_existing_answer = isinstance(existing_answer, dict) and _answer_is_meaningful(question, existing_answer)

    enriched_question = _serialize_question(question, answers, db)
    if raw_answer.get("skip") is True:
        normalized_answer = {"skipped": True}
    else:
        normalized_answer = _normalize_answer(enriched_question, raw_answer, answers)
    display_text = _answer_display_text(enriched_question, normalized_answer)
    if not _answer_is_meaningful(enriched_question, normalized_answer):
        raise ValueError("请先填写答案")

    version_no = int(session["version_no"] or 0) + 1
    _upsert_answer(
        db,
        session_id=session_id,
        student_id=student_id,
        question=question,
        answer=normalized_answer,
        display_text=display_text,
        version_no=version_no,
    )
    _upsert_question_message(
        db,
        session_id=session_id,
        student_id=student_id,
        role="user",
        kind="answer",
        question_code=question_code,
        content=display_text,
        payload={"answer": normalized_answer},
        replace_existing=is_editing_existing_answer,
    )

    answers[question_code] = normalized_answer
    next_question = _find_next_question(answers)
    visible_count = len(_visible_questions(answers))
    if next_question is None:
        db.execute(
            text(
                """
                UPDATE student_profile_guided_sessions
                SET session_status = 'completed',
                    current_stage = 'build_ready',
                    current_question_code = NULL,
                    current_question_index = :current_question_index,
                    version_no = :version_no,
                    finished_at = NOW()
                WHERE session_id = :session_id
                """
            ),
            {
                "session_id": session_id,
                "current_question_index": visible_count,
                "version_no": version_no,
            },
        )
        _insert_message(
            db,
            session_id=session_id,
            student_id=student_id,
            role="assistant",
            kind="notice",
            content="问卷已完成，系统已开始整理你的建档结果。",
            payload={"status": "completed"},
        )
        db.commit()
        generate_guided_result(db, student_id=student_id, session_id=session_id, trigger_reason="completed")
    else:
        next_index = _visible_question_index(answers, next_question["code"])
        db.execute(
            text(
                """
                UPDATE student_profile_guided_sessions
                SET session_status = 'active',
                    current_stage = 'collecting',
                    current_question_code = :current_question_code,
                    current_question_index = :current_question_index,
                    version_no = :version_no
                WHERE session_id = :session_id
                """
            ),
            {
                "session_id": session_id,
                "current_question_code": next_question["code"],
                "current_question_index": next_index,
                "version_no": version_no,
            },
        )
        _upsert_question_message(
            db,
            session_id=session_id,
            student_id=student_id,
            role="assistant",
            kind="question",
            question_code=next_question["code"],
            content=next_question["title"],
            payload=_serialize_question(next_question, answers, db, index=next_index + 1, total=visible_count),
            replace_existing=True,
        )
        db.commit()
        _sync_guided_business_snapshot(db, student_id=student_id, session_id=session_id)

    return get_session_bundle(db, student_id=student_id, session_id=session_id)


def exit_guided_session(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    trigger_reason: str = "manual_exit",
) -> dict[str, Any]:
    session = _get_session_by_id(db, session_id=session_id, student_id=student_id)
    if session["session_status"] != "completed":
        db.execute(
            text(
                """
                UPDATE student_profile_guided_sessions
                SET session_status = 'exited',
                    current_stage = 'build_ready',
                    exited_at = COALESCE(exited_at, NOW())
                WHERE session_id = :session_id
                """
            ),
            {"session_id": session_id},
        )
        _insert_message(
            db,
            session_id=session_id,
            student_id=student_id,
            role="assistant",
            kind="notice",
            content="已保存当前问卷进度，并根据已有信息生成建档结果。",
            payload={"status": "exited", "trigger_reason": trigger_reason},
        )
        db.commit()
    generate_guided_result(db, student_id=student_id, session_id=session_id, trigger_reason=trigger_reason)
    return get_session_bundle(db, student_id=student_id, session_id=session_id)


def _safe_float(value: Any) -> float | None:
    if value is None or value == "":
        return None
    try:
        return float(str(value).replace("%", "").strip())
    except ValueError:
        return None


def _first_safe_float(*values: Any) -> float | None:
    for value in values:
        parsed = _safe_float(value)
        if parsed is not None:
            return parsed
    return None


def _safe_int(value: Any) -> int | None:
    parsed = _safe_float(value)
    return int(parsed) if parsed is not None else None


def _rank_percentile(value: str | None) -> float | None:
    return {
        "TOP_1": 1,
        "TOP_5": 5,
        "TOP_10": 10,
        "TOP_20": 20,
        "TOP_30": 30,
        "UPPER_MIDDLE": 40,
        "MIDDLE": 50,
    }.get(value or "")


def _grade_size(value: str | None, custom_text: str | None) -> int | None:
    if value == "CUSTOM":
        return _safe_int(custom_text)
    return {
        "LT_50": 50,
        "50_100": 100,
        "100_200": 200,
        "200_500": 500,
        "GT_500": 501,
    }.get(value or "")


def _infer_chinese_high_school_grading_system_code(q8: dict[str, Any]) -> str | None:
    explicit_code = str(q8.get("grading_system_code") or "").strip()
    if explicit_code:
        return explicit_code

    gpa_scale = _safe_float(q8.get("gpa_scale"))
    if gpa_scale == 4:
        return "GPA_4"
    if gpa_scale == 5:
        return "GPA_5"

    has_percent_scores = _safe_float(q8.get("average_score_100")) is not None or any(
        _safe_float(q8.get(f"chs_subject_{index}_score_100")) is not None for index in range(1, 7)
    )
    return "PERCENT_100" if has_percent_scores else None


def _build_chinese_high_school_subject_rows(
    *,
    student_id: str,
    q8: dict[str, Any],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    seen_subject_ids: set[str] = set()

    for index in range(1, 7):
        subject_id = str(q8.get(f"chs_subject_{index}_id") or "").strip()
        if not subject_id or subject_id in seen_subject_ids:
            continue
        rows.append(
            {
                "student_id": student_id,
                "chs_subject_id": subject_id,
                "score_100": _safe_float(q8.get(f"chs_subject_{index}_score_100")),
            }
        )
        seen_subject_ids.add(subject_id)

    return rows


def _country_labels(db: Session, answers: dict[str, dict[str, Any]]) -> list[tuple[str, str]]:
    question = _serialize_question(QUESTION_BY_CODE["Q3"], answers, db)
    result = [(value, _option_label(question, value)) for value in _selected_values(answers.get("Q3"))]
    custom_text = str((answers.get("Q3") or {}).get("custom_text") or "").strip()
    if custom_text:
        result.append(("OTHER_CUSTOM", custom_text))
    return result


def _major_labels(db: Session, answers: dict[str, dict[str, Any]]) -> list[tuple[str, str]]:
    question = _serialize_question(QUESTION_BY_CODE["Q4"], answers, db)
    return [(value, _option_label(question, value)) for value in _selected_values(answers.get("Q4"))]


def _resolve_primary_country_code(db: Session, labels: list[tuple[str, str]]) -> str | None:
    if not labels:
        return None
    code, _label = labels[0]
    return None if code in {"OTHER", "OTHER_CUSTOM"} else code


def _resolve_primary_major_code(db: Session, labels: list[tuple[str, str]]) -> str | None:
    if not labels:
        return None
    for code, _label in labels:
        if code not in {"UNDECIDED", "OTHER", "OTHER_CUSTOM"}:
            return code
    return None


def _curriculum_db_code(value: str | None) -> str | None:
    if not value:
        return None
    return GUIDED_CURRICULUM_TO_DB_CODE.get(value, value)


def _other_curriculum_notes(answer: dict[str, Any]) -> str | None:
    parts: list[str] = []
    gpa = str(answer.get("gpa") or "").strip()
    if gpa:
        parts.append(f"GPA: {gpa}")
    gpa_scale = str(answer.get("gpa_scale") or "").strip()
    if gpa_scale:
        parts.append(f"满分: {gpa_scale}")
    weighted = str(answer.get("is_weighted") or "").strip()
    if weighted == "yes":
        parts.append("是否加权: 是")
    elif weighted == "no":
        parts.append("是否加权: 否")
    elif weighted == "unknown":
        parts.append("是否加权: 不清楚")
    strong_courses = str(answer.get("strong_courses") or "").strip()
    if strong_courses:
        parts.append(f"优势课程: {strong_courses}")
    return "；".join(parts) if parts else None


def _yes_no_unknown_to_bool(value: str | None) -> int | None:
    normalized = str(value or "").strip().lower()
    if normalized == "yes":
        return 1
    if normalized == "no":
        return 0
    return None


def _school_gpa_profile_payload(q8: dict[str, Any]) -> dict[str, Any]:
    return {
        "school_gpa": _first_safe_float(q8.get("gpa"), q8.get("school_gpa_or_average")),
        "gpa_scale": _safe_float(q8.get("gpa_scale")),
        "is_weighted": _yes_no_unknown_to_bool(q8.get("is_weighted")),
    }


def _split_course_tokens(raw_text: str | None) -> list[str]:
    if not raw_text:
        return []
    candidates = re.split(r"[\n,，;；]+", raw_text)
    result: list[str] = []
    seen: set[str] = set()
    for candidate in candidates:
        cleaned = str(candidate).strip()
        if not cleaned:
            continue
        normalized = cleaned.casefold()
        if normalized in seen:
            continue
        seen.add(normalized)
        result.append(cleaned)
    return result[:3]


def _infer_us_high_school_course_level(raw_name: str) -> str | None:
    normalized = raw_name.casefold()
    if normalized.startswith("ap ") or normalized.startswith("ap-") or " ap " in normalized:
        return "AP"
    if normalized.startswith("honors ") or normalized.startswith("honours ") or normalized.endswith(" honors") or normalized.endswith(" honours"):
        return "HONORS"
    if normalized.startswith("ib ") or normalized.endswith(" ib"):
        return "IB"
    if normalized.startswith("dual enrollment ") or "dual enrollment" in normalized:
        return "DUAL_ENROLLMENT"
    if normalized.startswith("advanced ") or normalized.endswith(" advanced"):
        return "ADVANCED"
    return None


def _strip_us_high_school_course_level(raw_name: str, level_code: str | None) -> str:
    cleaned = raw_name.strip()
    if not level_code:
        return cleaned
    patterns_by_level = {
        "AP": [r"^ap[\s\-]+", r"[\s\-]+ap$"],
        "HONORS": [r"^(honors|honours)[\s\-]+", r"[\s\-]+(honors|honours)$"],
        "IB": [r"^ib[\s\-]+", r"[\s\-]+ib$"],
        "DUAL_ENROLLMENT": [r"^dual[\s\-]+enrollment[\s\-]+", r"[\s\-]+dual[\s\-]+enrollment$"],
        "ADVANCED": [r"^advanced[\s\-]+", r"[\s\-]+advanced$"],
    }
    for pattern in patterns_by_level.get(level_code, []):
        cleaned = re.sub(pattern, "", cleaned, flags=re.IGNORECASE).strip()
    return cleaned or raw_name.strip()


def _build_us_high_school_course_rows(
    *,
    student_id: str,
    school_year_label: str | None,
    strong_courses_text: str | None,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for course_name in _split_course_tokens(strong_courses_text):
        level_code = _infer_us_high_school_course_level(course_name)
        normalized_name = _strip_us_high_school_course_level(course_name, level_code)
        rows.append(
            {
                "student_id": student_id,
                "school_year_label": school_year_label,
                "course_name_text": normalized_name,
                "course_level_code": level_code,
                "notes": f"原始课程名：{course_name}" if normalized_name != course_name else None,
            }
        )
    return rows


def _build_profile_json(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    answers: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    country_labels = _country_labels(db, answers)
    major_labels = _major_labels(db, answers)
    q5 = _selected_value(answers.get("Q5"))
    q6 = _selected_value(answers.get("Q6"))
    q7 = answers.get("Q7") or {}
    q8 = answers.get("Q8") or {}
    q9 = _selected_value(answers.get("Q9"))
    q10 = answers.get("Q10") or {}
    q11 = _selected_value(answers.get("Q11"))
    q12_values = set(_selected_values(answers.get("Q12")))
    q14 = _selected_value(answers.get("Q14"))
    curriculum_db_code = _curriculum_db_code(q5)
    q2_question = _serialize_question(QUESTION_BY_CODE["Q2"], answers, db)
    q5_question = _serialize_question(QUESTION_BY_CODE["Q5"], answers, db)
    q6_question = _serialize_question(QUESTION_BY_CODE["Q6"], answers, db)

    profile: dict[str, Any] = {
        "student_basic_info": {
            "student_id": student_id,
            "schema_version": "guided_v1",
            "profile_type": "guided_profile",
            "current_grade": _selected_value(answers.get("Q1")),
            "target_entry_term": _option_label(q2_question, _selected_value(answers.get("Q2"))),
            "CTRY_CODE_VAL": _resolve_primary_country_code(db, country_labels),
            "MAJ_CODE_VAL": _resolve_primary_major_code(db, major_labels),
            "MAJ_INTEREST_TEXT": major_labels[0][1] if major_labels else None,
        },
        "student_basic_info_curriculum_system": [],
        "student_academic": {
            "student_id": student_id,
            "grade_size": _grade_size(_selected_value(q7), q7.get("custom_text")),
            "rank_percentile": _rank_percentile(q6),
            "rank_scope_code": "GRADE",
            "rank_evidence_level_code": "SELF_REPORTED",
            "rank_evidence_notes": _option_label(q6_question, q6),
        },
        "student_academic_a_level_profile": {},
        "student_academic_ap_profile": {},
        "student_academic_ib_profile": {},
        "student_academic_chinese_high_school_profile": {},
        "student_academic_chinese_high_school_subject": [],
        "student_academic_us_high_school_profile": {},
        "student_academic_us_high_school_course": [],
        "student_academic_other_curriculum_profile": {},
        "student_language": {},
        "student_standardized_tests": {"student_id": student_id, "is_applicable": 1},
        "student_competitions": {"student_id": student_id},
        "student_activities": {"student_id": student_id},
        "student_projects_experience": {"student_id": student_id},
    }

    if curriculum_db_code:
        profile["student_basic_info_curriculum_system"].append(
            {
                "student_id": student_id,
                "curriculum_system_code": curriculum_db_code,
                "is_primary": 1,
                "notes": _option_label(q5_question, q5),
            }
        )
    if q5 == "CHINESE_HIGH_SCHOOL":
        profile["student_academic_chinese_high_school_profile"] = {
            "student_id": student_id,
            "grading_system_code": _infer_chinese_high_school_grading_system_code(q8),
            "average_score_100": _safe_float(q8.get("average_score_100")),
            "gpa": _safe_float(q8.get("gpa")),
            "gpa_scale": _safe_float(q8.get("gpa_scale")),
        }
        profile["student_academic_chinese_high_school_subject"] = _build_chinese_high_school_subject_rows(
            student_id=student_id,
            q8=q8,
        )
    elif q5 == "AP":
        profile["student_academic_ap_profile"] = {
            "student_id": student_id,
            **_school_gpa_profile_payload(q8),
        }
    elif q5 == "IB":
        profile["student_academic_ib_profile"] = {
            "student_id": student_id,
            **_school_gpa_profile_payload(q8),
        }
    elif q5 == "A_LEVEL":
        profile["student_academic_a_level_profile"] = {
            "student_id": student_id,
            **_school_gpa_profile_payload(q8),
        }
    elif q5 == "US_HIGH_SCHOOL":
        profile["student_academic_us_high_school_profile"] = {
            "student_id": student_id,
            **_school_gpa_profile_payload(q8),
            "course_load_rigor_notes": str(q8.get("strong_courses") or "").strip() or None,
        }
        profile["student_academic_us_high_school_course"] = _build_us_high_school_course_rows(
            student_id=student_id,
            school_year_label=_selected_value(answers.get("Q1")),
            strong_courses_text=q8.get("strong_courses"),
        )
    elif q5 in {"INTERNATIONAL_OTHER", "OTHER"}:
        profile["student_academic_other_curriculum_profile"] = {
            "student_id": student_id,
            "curriculum_scope_code": q5,
            **_school_gpa_profile_payload(q8),
            "notes": _other_curriculum_notes(q8),
        }
        profile["student_academic"]["other_curriculum_notes"] = _other_curriculum_notes(q8)

    profile.update(_build_language_profile(student_id=student_id, language_type=q9, q10=q10, q11=q11))
    standardized_profile = _build_standardized_profile(
        student_id=student_id,
        selected_tests=q12_values,
        answers=answers,
        q14=q14,
    )
    ib_profile_update = standardized_profile.pop("student_academic_ib_profile", {})
    if ib_profile_update:
        profile.setdefault("student_academic_ib_profile", {}).update(ib_profile_update)
    profile.update(standardized_profile)
    profile.update(_build_experience_profile(student_id=student_id, answers=answers, session_id=session_id))
    return profile


def _coerce_language_detail_value(field_name: str, value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, str):
        value = value.strip()
    if value == "":
        return None
    if field_name in GUIDED_LANGUAGE_NUMERIC_FIELDS:
        return _safe_float(value)
    if field_name == "is_best_score":
        return 1 if str(value).strip() in {"1", "true", "TRUE", "yes", "YES"} else 0
    if field_name == "score_breakdown_json":
        if isinstance(value, (dict, list)):
            return _json_dump(value)
        parsed = _json_load(value)
        if parsed is not None:
            return _json_dump(parsed)
        return _json_dump({"summary": str(value)})
    return value


def _language_detail_row_from_guided_answer(
    *,
    student_id: str,
    table_name: str,
    q10: dict[str, Any],
) -> dict[str, Any]:
    normalized_answer = dict(q10 or {})

    # 兼容旧标准问卷已经保存过的字段名，新提交会直接使用正式档案字段名。
    legacy_aliases = {
        "overall": "overall_score",
        "total": "total_score",
        "score": "total_score",
        "listening": "listening_score",
        "reading": "reading_score",
        "writing": "writing_score",
        "speaking": "speaking_score",
        "score_total": "score_total",
    }
    for old_name, new_name in legacy_aliases.items():
        if old_name in normalized_answer and new_name not in normalized_answer:
            normalized_answer[new_name] = normalized_answer.get(old_name)

    if "breakdown" in normalized_answer and "score_breakdown_json" not in normalized_answer:
        normalized_answer["score_breakdown_json"] = normalized_answer.get("breakdown")
    if "breakdown" in normalized_answer and "notes" not in normalized_answer:
        normalized_answer["notes"] = normalized_answer.get("breakdown")

    allowed_fields = GUIDED_LANGUAGE_DETAIL_ALLOWED_FIELDS.get(table_name) or set()
    row: dict[str, Any] = {"student_id": student_id, "is_best_score": 1}
    for field_name in allowed_fields:
        if field_name not in normalized_answer:
            continue
        row[field_name] = _coerce_language_detail_value(field_name, normalized_answer.get(field_name))

    if not row.get("status_code") and _has_any_value(row, *GUIDED_LANGUAGE_NUMERIC_FIELDS):
        row["status_code"] = "SCORED"
    return row


def _build_language_profile(*, student_id: str, language_type: str | None, q10: dict[str, Any], q11: str | None) -> dict[str, Any]:
    q10_status = str((q10 or {}).get("status_code") or "").strip() or None
    payload: dict[str, Any] = {
        "student_language": {
            "student_id": student_id,
            "best_test_type_code": language_type if language_type and language_type != "NO_SCORE" else None,
            "best_score_status_code": q10_status if language_type and language_type != "NO_SCORE" else "ESTIMATED",
        },
        "student_language_ielts": [],
        "student_language_toefl_ibt": [],
        "student_language_toefl_essentials": [],
        "student_language_det": [],
        "student_language_pte": [],
        "student_language_languagecert": [],
        "student_language_cambridge": [],
        "student_language_other": [],
    }
    table_name = GUIDED_LANGUAGE_TEST_DETAIL_TABLES.get(language_type or "")
    if table_name:
        has_answer_content = any(
            value is not None and str(value).strip() != ""
            for key, value in (q10 or {}).items()
            if key != "skipped"
        )
        if has_answer_content and not q10.get("skipped"):
            row = _language_detail_row_from_guided_answer(
                student_id=student_id,
                table_name=table_name,
                q10=q10,
            )
            if table_name == "student_language_other" and not row.get("test_name"):
                row["test_name"] = "其他语言考试"
            payload[table_name].append(row)
    elif (language_type == "NO_SCORE" or not language_type) and q11:
        if q11.startswith("IELTS"):
            payload["student_language"]["best_test_type_code"] = "IELTS"
            payload["student_language_ielts"].append(
                {
                    "student_id": student_id,
                    "status_code": "ESTIMATED",
                    "estimated_total": _estimated_language_score(q11),
                    "is_best_score": 1,
                }
            )
        elif q11.startswith("TOEFL"):
            payload["student_language"]["best_test_type_code"] = "TOEFL_IBT"
            payload["student_language_toefl_ibt"].append(
                {
                    "student_id": student_id,
                    "status_code": "ESTIMATED",
                    "estimated_total": _estimated_language_score(q11),
                    "is_best_score": 1,
                }
            )
    return payload


def _estimated_language_score(value: str) -> float | None:
    return {
        "IELTS_6": 6,
        "IELTS_6_5": 6.5,
        "IELTS_7": 7,
        "IELTS_7_5_PLUS": 7.5,
        "TOEFL_90": 90,
        "TOEFL_100": 100,
        "TOEFL_105_PLUS": 105,
    }.get(value)


def _has_any_value(source: dict[str, Any], *field_names: str) -> bool:
    return any(str(source.get(field_name) or "").strip() for field_name in field_names)


def _is_predicted_status(status: str | None) -> int:
    return 0 if str(status or "").strip() == "SCORED" else 1


def _repeatable_answer_rows(answer: dict[str, Any]) -> list[dict[str, Any]]:
    rows = answer.get("rows") if isinstance(answer, dict) else None
    return [row for row in rows if isinstance(row, dict)] if isinstance(rows, list) else []


def _build_a_level_subject_rows_for_answer(
    *,
    student_id: str,
    answer: dict[str, Any],
    default_stage: str,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    board_code = str(answer.get("a_level_board") or "").strip() or None
    for row in _repeatable_answer_rows(answer):
        subject_id = str(row.get("subject") or "").strip()
        if not subject_id:
            continue
        status = str(row.get("status") or "").strip() or None
        rows.append(
            {
                "student_id": student_id,
                "al_subject_id": subject_id,
                "stage_code": default_stage,
                "grade_code": str(row.get("grade") or "").strip() or None,
                "is_predicted": _is_predicted_status(status),
                "board_code": board_code,
            }
        )
    return rows


def _build_ap_course_rows(
    *,
    student_id: str,
    answer: dict[str, Any],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for row in _repeatable_answer_rows(answer):
        course_id = str(row.get("subject") or "").strip()
        if not course_id:
            continue
        status = str(row.get("status") or "").strip() or None
        rows.append(
            {
                "student_id": student_id,
                "ap_course_id": course_id,
                "score": _safe_int(row.get("score")),
                "is_predicted": _is_predicted_status(status),
            }
        )
    return rows


def _build_ib_subject_rows(
    *,
    student_id: str,
    answer: dict[str, Any],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for row in _repeatable_answer_rows(answer):
        subject_id = str(row.get("subject") or "").strip()
        if not subject_id:
            continue
        status = str(row.get("status") or "").strip() or None
        rows.append(
            {
                "student_id": student_id,
                "ib_subject_id": subject_id,
                "level_code": str(row.get("level") or "").strip() or None,
                "score": _safe_int(row.get("score")),
                "is_predicted": _is_predicted_status(status),
            }
        )
    return rows


def _has_meaningful_standardized_record(record: dict[str, Any]) -> bool:
    ignored = {"student_id", "test_type", "is_best_score"}
    return any(value not in (None, "") for key, value in record.items() if key not in ignored)


def _build_standardized_profile(
    *,
    student_id: str,
    selected_tests: set[str],
    answers: dict[str, dict[str, Any]],
    q14: str | None,
) -> dict[str, Any]:
    records: list[dict[str, Any]] = []
    a_level_answer = answers.get("Q13_A_LEVEL") or {}
    as_answer = answers.get("Q13_AS") or {}
    ap_answer = answers.get("Q13_AP") or {}
    ib_answer = answers.get("Q13_IB") or {}
    sat_answer = answers.get("Q13_SAT") or {}
    act_answer = answers.get("Q13_ACT") or {}

    a_level_subject_rows: list[dict[str, Any]] = []
    if "A_LEVEL" in selected_tests:
        a_level_subject_rows.extend(
            _build_a_level_subject_rows_for_answer(
                student_id=student_id,
                answer=a_level_answer,
                default_stage="FULL_A_LEVEL",
            )
        )
    if "AS" in selected_tests:
        a_level_subject_rows.extend(
            _build_a_level_subject_rows_for_answer(
                student_id=student_id,
                answer=as_answer,
                default_stage="AS",
            )
        )
    ap_course_rows = _build_ap_course_rows(student_id=student_id, answer=ap_answer) if "AP" in selected_tests else []
    ib_subject_rows = _build_ib_subject_rows(student_id=student_id, answer=ib_answer) if "IB" in selected_tests else []
    ib_total = _safe_int(ib_answer.get("ib_total"))

    if "SAT" in selected_tests:
        sat_record = {
            "student_id": student_id,
            "test_type": "SAT",
            "status": sat_answer.get("sat_status")
            or ("SCORED" if _has_any_value(sat_answer, "sat_total", "sat_ebrw", "sat_math") else "PLANNED"),
            "total_score": _safe_int(sat_answer.get("sat_total")),
            "sat_erw": _safe_int(sat_answer.get("sat_ebrw")),
            "sat_math": _safe_int(sat_answer.get("sat_math")),
            "is_best_score": 1,
        }
        if _has_meaningful_standardized_record(sat_record):
            records.append(sat_record)

    if "ACT" in selected_tests:
        act_record = {
            "student_id": student_id,
            "test_type": "ACT",
            "status": act_answer.get("act_status")
            or (
                "SCORED"
                if _has_any_value(act_answer, "act_total", "act_english", "act_math", "act_reading", "act_science")
                else "PLANNED"
            ),
            "total_score": _safe_int(act_answer.get("act_total")),
            "act_english": _safe_int(act_answer.get("act_english")),
            "act_math": _safe_int(act_answer.get("act_math")),
            "act_reading": _safe_int(act_answer.get("act_reading")),
            "act_science": _safe_int(act_answer.get("act_science")),
            "is_best_score": 1,
        }
        if _has_meaningful_standardized_record(act_record):
            records.append(act_record)

    if q14:
        estimated_type = _estimated_standardized_test_type(q14)
        records.append(
            {
                "student_id": student_id,
                "test_type": estimated_type,
                "status": "ESTIMATED",
                "estimated_total_score": _estimated_standardized_score(q14),
                "is_best_score": 1,
            }
        )

    return {
        "student_academic_a_level_subject": a_level_subject_rows,
        "student_academic_ap_course": ap_course_rows,
        "student_academic_ib_profile": {
            "student_id": student_id,
            "ib_total_predicted": ib_total,
        }
        if ib_total is not None
        else {},
        "student_academic_ib_subject": ib_subject_rows,
        "student_standardized_tests": {
            "student_id": student_id,
            "is_applicable": 0 if selected_tests == {"NONE"} else 1,
            "best_test_type": records[0]["test_type"] if records else None,
            "best_total_score": records[0].get("total_score") if records else None,
        },
        "student_standardized_test_records": records,
    }


def _estimated_standardized_test_type(value: str) -> str:
    if value.startswith("SAT"):
        return "SAT"
    if value.startswith("ACT"):
        return "ACT"
    if value.startswith("A_LEVEL"):
        return "A_LEVEL"
    if value.startswith("AP"):
        return "AP"
    if value.startswith("IB"):
        return "IB"
    return "OTHER"


def _estimated_standardized_score(value: str) -> int | None:
    return {
        "SAT_1450_1500": 1450,
        "SAT_1500_1550": 1500,
        "ACT_32_33": 32,
        "ACT_34_36": 34,
        "IB_40_PLUS": 40,
    }.get(value)


def _build_experience_profile(
    *,
    student_id: str,
    answers: dict[str, dict[str, Any]],
    session_id: str,
) -> dict[str, Any]:
    return {
        "student_competition_entries": _experience_row(student_id, answers.get("Q15"), "competition", session_id),
        "student_activity_entries": _experience_row(student_id, answers.get("Q16"), "activity", session_id),
        "student_project_entries": _experience_row(student_id, answers.get("Q17"), "project", session_id),
        "student_project_outputs": [],
    }


def _experience_row(student_id: str, answer: dict[str, Any] | None, kind: str, session_id: str) -> list[dict[str, Any]]:
    if not answer or answer.get("has_experience") != "yes":
        return []
    base = {
        "student_id": student_id,
        "sort_order": 1,
    }
    if kind == "competition":
        return [
            {
                **base,
                "competition_name": answer.get("name"),
                "competition_field": answer.get("competition_field") or "OTHER",
                "competition_tier": answer.get("competition_tier") or "UNKNOWN",
                "competition_level": answer.get("competition_level") or "INTERNATIONAL",
                "result_text": answer.get("result_or_output"),
                "evidence_type": answer.get("evidence_type") or "NONE",
                "evidence_link_or_note": answer.get("evidence"),
            }
        ]
    if kind == "activity":
        return [
            {
                **base,
                "activity_name": answer.get("name"),
                "activity_category": answer.get("activity_category") or "OTHER",
                "activity_role": answer.get("activity_role") or "OTHER",
                "duration_months": _safe_int(answer.get("duration_months")),
                "weekly_hours": _safe_float(answer.get("weekly_hours")),
                "awards_or_media": answer.get("result_or_output"),
                "evidence_type": answer.get("evidence_type") or "NONE",
                "evidence_link_or_note": answer.get("evidence"),
            }
        ]
    return [
        {
            **base,
            "project_name": answer.get("name"),
            "project_type": answer.get("project_type") or "OTHER",
            "project_field": answer.get("project_field") or "OTHER",
            "relevance_to_major": answer.get("relevance_to_major") or "MEDIUM",
            "hours_total": _safe_float(answer.get("hours_total")),
            "evidence_type": answer.get("evidence_type") or "NONE",
            "evidence_link_or_note": answer.get("evidence"),
        }
    ]


def _sync_target_entries(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    answers: dict[str, dict[str, Any]],
) -> None:
    db.execute(
        text(
            """
            DELETE FROM student_basic_info_target_country_entries
            WHERE student_id = :student_id AND source_flow = 'guided_profile'
            """
        ),
        {"student_id": student_id},
    )
    for index, (fallback_code, label) in enumerate(_country_labels(db, answers), start=1):
        country_code = fallback_code
        db.execute(
            text(
                """
                INSERT INTO student_basic_info_target_country_entries
                  (student_id, country_code, sort_order, is_primary, source_flow, source_session_id, remark)
                VALUES
                  (:student_id, :country_code, :sort_order, :is_primary, 'guided_profile', :session_id, :remark)
                """
            ),
            {
                "student_id": student_id,
                "country_code": country_code,
                "sort_order": index,
                "is_primary": 1 if index == 1 else 0,
                "session_id": session_id,
                "remark": label,
            },
        )

    db.execute(
        text(
            """
            DELETE FROM student_basic_info_target_major_entries
            WHERE student_id = :student_id AND source_flow = 'guided_profile'
            """
        ),
        {"student_id": student_id},
    )
    for index, (direction_code, label) in enumerate(_major_labels(db, answers), start=1):
        major_code = None if direction_code in {"UNDECIDED", "OTHER", "OTHER_CUSTOM"} else direction_code
        db.execute(
            text(
                """
                INSERT INTO student_basic_info_target_major_entries
                  (student_id, major_direction_code, major_direction_label, major_code,
                   sort_order, is_primary, source_flow, source_session_id)
                VALUES
                  (:student_id, :major_direction_code, :major_direction_label, :major_code,
                   :sort_order, :is_primary, 'guided_profile', :session_id)
                """
            ),
            {
                "student_id": student_id,
                "major_direction_code": direction_code,
                "major_direction_label": label,
                "major_code": major_code,
                "sort_order": index,
                "is_primary": 1 if index == 1 else 0,
                "session_id": session_id,
            },
        )


def _sync_guided_business_snapshot(db: Session, *, student_id: str, session_id: str) -> None:
    try:
        answers = _load_answers(db, session_id=session_id)
        profile_json = _build_profile_json(db, student_id=student_id, session_id=session_id, answers=answers)
        db_payload = build_db_payload_from_profile_json(profile_json, student_id=student_id)
        persist_business_profile_snapshot(db, db_payload=db_payload, student_id=student_id)
        _sync_target_entries(db, student_id=student_id, session_id=session_id, answers=answers)
        db.commit()
    except Exception:
        db.rollback()
        logger.exception("固定问卷同步正式业务表失败：student_id=%s session_id=%s", student_id, session_id)


def _fallback_radar_scores(answers: dict[str, dict[str, Any]]) -> dict[str, dict[str, Any]]:
    scores = {
        "academic": min(100, 35 + 10 * int(bool(answers.get("Q5"))) + 15 * int(bool(answers.get("Q6"))) + 20 * int(bool(answers.get("Q8")))),
        "language": min(100, 30 + 20 * int(bool(answers.get("Q9"))) + 30 * int(bool(answers.get("Q10") or answers.get("Q11")))),
        "standardized": min(100, 30 + 20 * int(bool(answers.get("Q12"))) + 30 * int(bool(answers.get("Q13") or answers.get("Q14")))),
        "competition": 75 if (answers.get("Q15") or {}).get("has_experience") == "yes" else 40,
        "activity": 75 if (answers.get("Q16") or {}).get("has_experience") == "yes" else 40,
        "project": 75 if (answers.get("Q17") or {}).get("has_experience") == "yes" else 40,
    }
    reasons = {
        "academic": "基于课程体系、校内排名和校内成绩完整度的本地兜底评分。",
        "language": "基于语言考试类型和正式/预估成绩完整度的本地兜底评分。",
        "standardized": "基于 SAT/ACT 或其他外部考试信息完整度的本地兜底评分。",
        "competition": "基于是否已有代表性竞赛经历的本地兜底评分。",
        "activity": "基于是否已有长期活动或领导力经历的本地兜底评分。",
        "project": "基于是否已有研究、实习或项目经历的本地兜底评分。",
    }
    return {key: {"score": value, "reason": reasons[key]} for key, value in scores.items()}


def _normalize_radar_scores(raw_scores: dict[str, Any]) -> dict[str, dict[str, Any]]:
    normalized: dict[str, dict[str, Any]] = {}
    default_reasons = {
        "academic": "系统已根据当前问卷中的学术信息生成评分。",
        "language": "系统已根据当前问卷中的语言信息生成评分。",
        "standardized": "系统已根据当前问卷中的标化信息生成评分。",
        "competition": "系统已根据当前问卷中的竞赛信息生成评分。",
        "activity": "系统已根据当前问卷中的活动信息生成评分。",
        "project": "系统已根据当前问卷中的项目信息生成评分。",
    }
    for key in ("academic", "language", "standardized", "competition", "activity", "project"):
        value = raw_scores.get(key) if isinstance(raw_scores, dict) else None
        if isinstance(value, dict):
            score = value.get("score")
            reason = value.get("reason") or default_reasons[key]
        else:
            score = value
            reason = default_reasons[key]
        try:
            numeric_score = max(0, min(100, int(float(score or 0))))
        except (TypeError, ValueError):
            numeric_score = 0
        normalized[key] = {"score": numeric_score, "reason": reason}
    return normalized


def _parse_model_json(raw_text: str) -> dict[str, Any]:
    cleaned = (raw_text or "").strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.removeprefix("```json").removeprefix("```JSON").removeprefix("```")
        cleaned = cleaned.removesuffix("```").strip()
    parsed = json.loads(cleaned)
    return parsed if isinstance(parsed, dict) else {}


def _load_latest_result(db: Session, *, student_id: str, session_id: str) -> dict[str, Any] | None:
    row = db.execute(
        text(
            """
            SELECT *
            FROM student_profile_guided_results
            WHERE student_id = :student_id AND session_id = :session_id AND delete_flag = '1'
            ORDER BY source_version_no DESC, id DESC
            LIMIT 1
            """
        ),
        {"student_id": student_id, "session_id": session_id},
    ).first()
    result = _row_to_dict(row)
    if result is None:
        return None
    result["profile_json"] = _json_load(result.get("profile_json"), {})
    result["db_payload_json"] = _json_load(result.get("db_payload_json"), {})
    result["radar_scores_json"] = _json_load(result.get("radar_scores_json"), {})
    return result


def get_latest_guided_result_for_student(db: Session, *, student_id: str) -> dict[str, Any] | None:
    row = db.execute(
        text(
            """
            SELECT *
            FROM student_profile_guided_results
            WHERE student_id = :student_id AND delete_flag = '1'
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """
        ),
        {"student_id": student_id},
    ).first()
    result = _row_to_dict(row)
    if result is None:
        return None
    result["profile_json"] = _json_load(result.get("profile_json"), {})
    result["db_payload_json"] = _json_load(result.get("db_payload_json"), {})
    result["radar_scores_json"] = _json_load(result.get("radar_scores_json"), {})
    return result


def generate_guided_result(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    trigger_reason: str,
) -> dict[str, Any] | None:
    session = _get_session_by_id(db, session_id=session_id, student_id=student_id)
    version_no = int(session.get("version_no") or 0)
    if version_no <= int(session.get("last_generated_version_no") or 0):
        return _load_latest_result(db, student_id=student_id, session_id=session_id)

    answers = _load_answers(db, session_id=session_id)
    profile_json = _build_profile_json(db, student_id=student_id, session_id=session_id, answers=answers)
    db_payload = build_db_payload_from_profile_json(profile_json, student_id=student_id)

    scoring_json: dict[str, Any]
    try:
        runtime_result = execute_prompt_with_context(
            db,
            prompt_key=SCORING_PROMPT_KEY,
            context={
                "student_id": student_id,
                "session_id": session_id,
                "profile_json": profile_json,
                "answers": answers,
            },
        )
        scoring_json = _parse_model_json(runtime_result.content)
    except Exception:
        logger.exception("固定问卷六维评分调用失败，使用本地兜底分")
        scoring_json = {
            "radar_scores_json": _fallback_radar_scores(answers),
            "summary_text": "系统已根据固定问卷答案生成当前建档结果。后续可继续补充更细信息以提升评估准确度。",
        }

    result_status = "saved"
    save_error_message: str | None = None
    try:
        persist_business_profile_snapshot(db, db_payload=db_payload, student_id=student_id)
        _sync_target_entries(db, student_id=student_id, session_id=session_id, answers=answers)
        db.commit()
    except Exception as exc:
        db.rollback()
        result_status = "failed"
        save_error_message = str(exc)

    radar_scores = _normalize_radar_scores(
        scoring_json.get("radar_scores_json") or scoring_json.get("radar_scores") or {}
    )
    summary_text = scoring_json.get("summary_text") or "固定问卷建档结果已生成。"
    db.execute(
        text(
            """
            INSERT INTO student_profile_guided_results
              (session_id, student_id, source_version_no, result_status, trigger_reason,
               profile_json, db_payload_json, radar_scores_json, summary_text, save_error_message)
            VALUES
              (:session_id, :student_id, :source_version_no, :result_status, :trigger_reason,
               :profile_json, :db_payload_json, :radar_scores_json, :summary_text, :save_error_message)
            ON DUPLICATE KEY UPDATE
              result_status = VALUES(result_status),
              trigger_reason = VALUES(trigger_reason),
              profile_json = VALUES(profile_json),
              db_payload_json = VALUES(db_payload_json),
              radar_scores_json = VALUES(radar_scores_json),
              summary_text = VALUES(summary_text),
              save_error_message = VALUES(save_error_message),
              update_time = CURRENT_TIMESTAMP,
              delete_flag = '1'
            """
        ),
        {
            "session_id": session_id,
            "student_id": student_id,
            "source_version_no": version_no,
            "result_status": result_status,
            "trigger_reason": trigger_reason,
            "profile_json": _json_dump(profile_json),
            "db_payload_json": _json_dump(db_payload),
            "radar_scores_json": _json_dump(radar_scores),
            "summary_text": summary_text,
            "save_error_message": save_error_message,
        },
    )
    db.execute(
        text(
            """
            UPDATE student_profile_guided_sessions
            SET current_stage = :current_stage,
                last_generated_version_no = :version_no
            WHERE session_id = :session_id
            """
        ),
        {
            "session_id": session_id,
            "version_no": version_no,
            "current_stage": "generated" if result_status == "saved" else "failed",
        },
    )
    db.commit()
    return _load_latest_result(db, student_id=student_id, session_id=session_id)
