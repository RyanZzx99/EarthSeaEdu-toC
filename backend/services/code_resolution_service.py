from __future__ import annotations

from dataclasses import dataclass
import re
import unicodedata
from typing import Any

from sqlalchemy import text
from sqlalchemy.orm import Session


@dataclass(slots=True, frozen=True)
class ResolutionConfig:
    """单张码值表的查询配置。"""

    table_name: str
    primary_key_column: str
    code_column: str
    cn_name_column: str
    en_name_column: str


@dataclass(slots=True)
class CodeResolutionResult:
    """单个字段的码值映射结果。"""

    raw_text: str | None
    normalized_text: str | None
    mapped_code: str | None
    mapping_status: str
    matched_source: str | None = None
    matched_label: str | None = None

    @property
    def resolved(self) -> bool:
        return self.mapping_status == "resolved" and self.mapped_code is not None


@dataclass(slots=True, frozen=True)
class SubjectResolutionConfig:
    """学术科目明细表的码值映射配置。"""

    table_name: str
    id_field: str
    name_field: str
    fallback_note_field: str
    dictionary: ResolutionConfig
    alias_map: dict[str, str]


COUNTRY_CONFIG = ResolutionConfig(
    table_name="CTRY_CODE_VAL",
    primary_key_column="CTRY_CODE_VAL_ID",
    code_column="CTRY_CODE_VAL",
    cn_name_column="CN_CODE_NAME",
    en_name_column="EN_CODE_NAME",
)

MAJOR_CONFIG = ResolutionConfig(
    table_name="MAJ_CODE_VAL",
    primary_key_column="MAJ_CODE_VAL_ID",
    code_column="MAJ_CODE_VAL",
    cn_name_column="MAJ_CN_CODE_NAME",
    en_name_column="MAJ_EN_CODE_NAME",
)

SCHOOL_CONFIG = ResolutionConfig(
    table_name="SCHOOL_CODE_VAL",
    primary_key_column="SCHOOL_CODE_VAL_ID",
    code_column="SCHOOL_CODE_VAL",
    cn_name_column="SCHOOL_CODE_CN_NAME",
    en_name_column="SCHOOL_CODE_EN_NAME",
)

_MULTI_SPACE_RE = re.compile(r"\s+")


def normalize_resolution_text(value: str | None) -> str | None:
    """
    对输入文本做轻量归一化。

    当前只做：
    1. Unicode 归一化
    2. 去首尾空格
    3. 英文统一小写
    4. 标点 / 符号转空格
    5. 压缩多余空格
    """

    if value is None:
        return None

    normalized = unicodedata.normalize("NFKC", value).strip().lower()
    if not normalized:
        return None

    chars: list[str] = []
    for ch in normalized:
        category = unicodedata.category(ch)
        # 把常见标点和符号统一转成空格，避免 “A-Level” / “A Level” 这类差异影响匹配。
        if category.startswith(("P", "S")):
            chars.append(" ")
        else:
            chars.append(ch)

    cleaned = "".join(chars)
    cleaned = _MULTI_SPACE_RE.sub(" ", cleaned).strip()
    return cleaned or None


def _build_alias_map(raw_alias_map: dict[str, str]) -> dict[str, str]:
    """把别名词典统一归一化成可直接匹配的形式。"""

    normalized_alias_map: dict[str, str] = {}
    for raw_alias, mapped_code in raw_alias_map.items():
        normalized_alias = normalize_resolution_text(raw_alias)
        if normalized_alias:
            normalized_alias_map[normalized_alias] = mapped_code
    return normalized_alias_map


AL_LEVEL_SUBJECT_CONFIG = ResolutionConfig(
    table_name="dict_a_level_subject",
    primary_key_column="al_subject_id",
    code_column="al_subject_id",
    cn_name_column="subject_name_cn",
    en_name_column="subject_name_en",
)

AP_COURSE_CONFIG = ResolutionConfig(
    table_name="dict_ap_course",
    primary_key_column="ap_course_id",
    code_column="ap_course_id",
    cn_name_column="course_name_cn",
    en_name_column="course_name_en",
)

IB_SUBJECT_CONFIG = ResolutionConfig(
    table_name="dict_ib_subject",
    primary_key_column="ib_subject_id",
    code_column="ib_subject_id",
    cn_name_column="subject_name_cn",
    en_name_column="subject_name_en",
)

CHINESE_HIGH_SCHOOL_SUBJECT_CONFIG = ResolutionConfig(
    table_name="dict_chinese_high_school_subject",
    primary_key_column="chs_subject_id",
    code_column="chs_subject_id",
    cn_name_column="subject_name_cn",
    en_name_column="subject_name_en",
)

SUBJECT_RESOLUTION_CONFIGS: dict[str, SubjectResolutionConfig] = {
    "student_academic_a_level_subject": SubjectResolutionConfig(
        table_name="student_academic_a_level_subject",
        id_field="al_subject_id",
        name_field="subject_name_text",
        fallback_note_field="notes",
        dictionary=AL_LEVEL_SUBJECT_CONFIG,
        alias_map=_build_alias_map(
            {
                "math": "AL_MATH",
                "mathematics": "AL_MATH",
                "数学": "AL_MATH",
                "进一步数学": "AL_FURTHER_MATH",
                "进阶数学": "AL_FURTHER_MATH",
                "高数": "AL_FURTHER_MATH",
                "进数": "AL_FURTHER_MATH",
                "further math": "AL_FURTHER_MATH",
                "further mathematics": "AL_FURTHER_MATH",
                "physics": "AL_PHYSICS",
                "物理": "AL_PHYSICS",
                "chemistry": "AL_CHEMISTRY",
                "化学": "AL_CHEMISTRY",
                "biology": "AL_BIOLOGY",
                "生物": "AL_BIOLOGY",
                "economics": "AL_ECONOMICS",
                "经济": "AL_ECONOMICS",
                "history": "AL_HISTORY",
                "历史": "AL_HISTORY",
                "geography": "AL_GEOGRAPHY",
                "地理": "AL_GEOGRAPHY",
                "english language": "AL_ENGLISH_LANGUAGE",
                "英语语言": "AL_ENGLISH_LANGUAGE",
                "english literature": "AL_ENGLISH_LITERATURE",
                "英语文学": "AL_ENGLISH_LITERATURE",
                "chinese": "AL_CHINESE",
                "中文": "AL_CHINESE",
                "计算机": "AL_COMPUTER_SCIENCE",
                "计算机科学": "AL_COMPUTER_SCIENCE",
                "computer science": "AL_COMPUTER_SCIENCE",
                "business": "AL_BUSINESS",
                "商务": "AL_BUSINESS",
                "psychology": "AL_PSYCHOLOGY",
                "心理学": "AL_PSYCHOLOGY",
                "art and design": "AL_ART_DESIGN",
                "艺术与设计": "AL_ART_DESIGN",
            }
        ),
    ),
    "student_academic_ap_course": SubjectResolutionConfig(
        table_name="student_academic_ap_course",
        id_field="ap_course_id",
        name_field="subject_name_text",
        fallback_note_field="notes",
        dictionary=AP_COURSE_CONFIG,
        alias_map=_build_alias_map(
            {
                "ap微积分ab": "AP_CALCULUS_AB",
                "ap calculus ab": "AP_CALCULUS_AB",
                "微积分ab": "AP_CALCULUS_AB",
                "ap微积分bc": "AP_CALCULUS_BC",
                "ap calculus bc": "AP_CALCULUS_BC",
                "微积分bc": "AP_CALCULUS_BC",
                "ap统计": "AP_STATISTICS",
                "ap statistics": "AP_STATISTICS",
                "统计学": "AP_STATISTICS",
                "ap物理1": "AP_PHYSICS_1",
                "ap physics 1": "AP_PHYSICS_1",
                "ap物理c力学": "AP_PHYSICS_C_MECH",
                "ap physics c mechanics": "AP_PHYSICS_C_MECH",
                "ap physics c mechanical": "AP_PHYSICS_C_MECH",
                "物理c力学": "AP_PHYSICS_C_MECH",
                "ap化学": "AP_CHEMISTRY",
                "ap chemistry": "AP_CHEMISTRY",
                "ap生物": "AP_BIOLOGY",
                "ap biology": "AP_BIOLOGY",
                "ap计算机科学a": "AP_COMPUTER_SCIENCE_A",
                "ap computer science a": "AP_COMPUTER_SCIENCE_A",
                "ap微观经济学": "AP_MICROECONOMICS",
                "ap microeconomics": "AP_MICROECONOMICS",
                "ap宏观经济学": "AP_MACROECONOMICS",
                "ap macroeconomics": "AP_MACROECONOMICS",
                "ap世界历史现代": "AP_WORLD_HISTORY",
                "ap world history modern": "AP_WORLD_HISTORY",
                "ap美国历史": "AP_US_HISTORY",
                "ap united states history": "AP_US_HISTORY",
                "ap英语语言与写作": "AP_ENGLISH_LANGUAGE",
                "ap english language and composition": "AP_ENGLISH_LANGUAGE",
                "ap英语文学与写作": "AP_ENGLISH_LITERATURE",
                "ap english literature and composition": "AP_ENGLISH_LITERATURE",
                "ap中文语言与文化": "AP_CHINESE_LANGUAGE_CULTURE",
                "ap chinese language and culture": "AP_CHINESE_LANGUAGE_CULTURE",
                "ap心理学": "AP_PSYCHOLOGY",
                "ap psychology": "AP_PSYCHOLOGY",
                "ap艺术史": "AP_ART_HISTORY",
                "ap art history": "AP_ART_HISTORY",
            }
        ),
    ),
    "student_academic_ib_subject": SubjectResolutionConfig(
        table_name="student_academic_ib_subject",
        id_field="ib_subject_id",
        name_field="subject_name_text",
        fallback_note_field="notes",
        dictionary=IB_SUBJECT_CONFIG,
        alias_map=_build_alias_map(
            {
                "语言a 文学": "IB_LANG_A_LIT",
                "language a literature": "IB_LANG_A_LIT",
                "语言a 语言与文学": "IB_LANG_A_LANG_LIT",
                "language a language and literature": "IB_LANG_A_LANG_LIT",
                "英语b": "IB_ENGLISH_B",
                "english b": "IB_ENGLISH_B",
                "中文b": "IB_CHINESE_B",
                "chinese b": "IB_CHINESE_B",
                "经济学": "IB_ECONOMICS",
                "economics": "IB_ECONOMICS",
                "历史": "IB_HISTORY",
                "history": "IB_HISTORY",
                "地理": "IB_GEOGRAPHY",
                "geography": "IB_GEOGRAPHY",
                "心理学": "IB_PSYCHOLOGY",
                "psychology": "IB_PSYCHOLOGY",
                "商业管理": "IB_BUSINESS_MANAGEMENT",
                "business management": "IB_BUSINESS_MANAGEMENT",
                "物理": "IB_PHYSICS",
                "physics": "IB_PHYSICS",
                "化学": "IB_CHEMISTRY",
                "chemistry": "IB_CHEMISTRY",
                "生物": "IB_BIOLOGY",
                "biology": "IB_BIOLOGY",
                "计算机科学": "IB_COMPUTER_SCIENCE",
                "computer science": "IB_COMPUTER_SCIENCE",
                "数学aa": "IB_MATH_AA",
                "math aa": "IB_MATH_AA",
                "mathematics aa": "IB_MATH_AA",
                "数学 分析与方法": "IB_MATH_AA",
                "mathematics analysis and approaches": "IB_MATH_AA",
                "数学ai": "IB_MATH_AI",
                "math ai": "IB_MATH_AI",
                "mathematics ai": "IB_MATH_AI",
                "数学 应用与诠释": "IB_MATH_AI",
                "mathematics applications and interpretation": "IB_MATH_AI",
                "视觉艺术": "IB_VISUAL_ARTS",
                "visual arts": "IB_VISUAL_ARTS",
                "知识理论": "IB_TOK",
                "theory of knowledge": "IB_TOK",
                "拓展论文": "IB_EE",
                "extended essay": "IB_EE",
            }
        ),
    ),
    "student_academic_chinese_high_school_subject": SubjectResolutionConfig(
        table_name="student_academic_chinese_high_school_subject",
        id_field="chs_subject_id",
        name_field="subject_name_text",
        fallback_note_field="notes",
        dictionary=CHINESE_HIGH_SCHOOL_SUBJECT_CONFIG,
        alias_map=_build_alias_map(
            {
                "语文": "CHS_CHINESE",
                "中文": "CHS_CHINESE",
                "chinese": "CHS_CHINESE",
                "数学": "CHS_MATH",
                "mathematics": "CHS_MATH",
                "math": "CHS_MATH",
                "英语": "CHS_ENGLISH",
                "english": "CHS_ENGLISH",
                "物理": "CHS_PHYSICS",
                "physics": "CHS_PHYSICS",
                "化学": "CHS_CHEMISTRY",
                "chemistry": "CHS_CHEMISTRY",
                "生物": "CHS_BIOLOGY",
                "biology": "CHS_BIOLOGY",
                "历史": "CHS_HISTORY",
                "history": "CHS_HISTORY",
                "地理": "CHS_GEOGRAPHY",
                "geography": "CHS_GEOGRAPHY",
                "政治": "CHS_POLITICS",
                "politics": "CHS_POLITICS",
            }
        ),
    ),
}


def resolve_country_code(db: Session, raw_text: str | None) -> CodeResolutionResult:
    return _resolve_code_by_like(db, COUNTRY_CONFIG, raw_text)


def resolve_major_code(db: Session, raw_text: str | None) -> CodeResolutionResult:
    return _resolve_code_by_like(db, MAJOR_CONFIG, raw_text)


def resolve_school_code(db: Session, raw_text: str | None) -> CodeResolutionResult:
    return _resolve_code_by_like(db, SCHOOL_CONFIG, raw_text)


def apply_code_resolution_to_payload(
    db: Session,
    payload: dict[str, Any],
    *,
    target_country_text: str | None = None,
    major_text: str | None = None,
    school_name_text: str | None = None,
) -> dict[str, CodeResolutionResult]:
    """
    执行国家 / 专业 / 学校的码值映射，并把结果回填进 extraction payload。

    注意：
    1. 当前 extraction schema 已经是按表结构输出，没有额外保留国家 / 专业 raw_text。
       所以这两个原始文本需要由上游调用方显式传入。
    2. 学校名可以直接回退使用 student_academic.school_name 做匹配。
    """

    results: dict[str, CodeResolutionResult] = {}

    student_basic_info = payload.setdefault("student_basic_info", {})
    student_academic = payload.setdefault("student_academic", {})

    # 国家和专业当前只回填最终 code，匹配失败时保留 null，不阻断后续流程。
    country_result = resolve_country_code(db, target_country_text)
    results["CTRY_CODE_VAL"] = country_result
    if country_result.resolved:
        student_basic_info["CTRY_CODE_VAL"] = country_result.mapped_code
    elif "CTRY_CODE_VAL" not in student_basic_info:
        student_basic_info["CTRY_CODE_VAL"] = None

    major_result = resolve_major_code(db, major_text)
    results["MAJ_CODE_VAL"] = major_result
    if major_result.resolved:
        student_basic_info["MAJ_CODE_VAL"] = major_result.mapped_code
    elif "MAJ_CODE_VAL" not in student_basic_info:
        student_basic_info["MAJ_CODE_VAL"] = None

    school_lookup_text = school_name_text or student_academic.get("school_name")
    school_result = resolve_school_code(db, school_lookup_text)
    results["school_code_val"] = school_result
    if school_result.resolved:
        student_academic["school_code_val"] = school_result.mapped_code
    elif "school_code_val" not in student_academic:
        student_academic["school_code_val"] = None

    return results


def apply_subject_code_resolution_to_payload(
    db: Session,
    payload: dict[str, Any],
) -> dict[str, list[dict[str, Any]]]:
    """
    对学术科目明细执行本地码值映射。

    设计目标：
    1. 优先校验 AI 已经给出的 *_subject_id / *_course_id 是否合法。
    2. 如果 AI 没给、给错，或给的与 subject_name_text 冲突，则以后端字典映射结果为准。
    3. 不新增任何模型调用，只做本地查表与别名匹配。
    """

    resolution_summary: dict[str, list[dict[str, Any]]] = {}

    for table_name, config in SUBJECT_RESOLUTION_CONFIGS.items():
        rows = payload.get(table_name)
        if not isinstance(rows, list):
            continue

        table_results: list[dict[str, Any]] = []
        for row_index, row in enumerate(rows, start=1):
            if not isinstance(row, dict):
                continue

            provided_code = _normalize_code_value(row.get(config.id_field))
            raw_text = _extract_subject_lookup_text(row, config)

            provided_code_result = (
                _resolve_code_by_exact_code(db, config.dictionary, provided_code)
                if provided_code
                else None
            )
            name_result = _resolve_subject_name(db, config, raw_text)

            resolution_status = "unresolved"
            matched_source: str | None = None
            matched_label: str | None = None
            resolved_code: str | None = None

            if name_result.resolved:
                resolved_code = name_result.mapped_code
                matched_source = name_result.matched_source
                matched_label = name_result.matched_label
                if provided_code_result and provided_code_result.mapped_code == resolved_code:
                    resolution_status = "resolved_by_name"
                elif provided_code_result and provided_code_result.mapped_code != resolved_code:
                    resolution_status = "corrected_by_name"
                elif provided_code:
                    resolution_status = "corrected_by_name"
                else:
                    resolution_status = "resolved_by_name"
            elif provided_code_result and provided_code_result.resolved:
                resolved_code = provided_code_result.mapped_code
                matched_source = provided_code_result.matched_source
                matched_label = provided_code_result.matched_label
                resolution_status = "resolved_by_provided_code"

            row[config.id_field] = resolved_code
            table_results.append(
                {
                    "row_index": row_index,
                    "id_field": config.id_field,
                    "raw_text": raw_text,
                    "normalized_text": name_result.normalized_text,
                    "provided_code": provided_code,
                    "resolved_code": resolved_code,
                    "resolution_status": resolution_status,
                    "matched_source": matched_source,
                    "matched_label": matched_label,
                }
            )

        if table_results:
            resolution_summary[table_name] = table_results

    return resolution_summary


def _resolve_code_by_like(
    db: Session,
    config: ResolutionConfig,
    raw_text: str | None,
) -> CodeResolutionResult:
    """
    按当前确认的轻量规则执行单字段码值匹配。

    规则：
    1. 不建设别名词典
    2. 只做数据库 LIKE 查询
    3. 只取一条结果
    4. 用固定 ORDER BY 保证结果稳定
    """

    normalized_text = normalize_resolution_text(raw_text)
    if not normalized_text:
        return CodeResolutionResult(
            raw_text=raw_text,
            normalized_text=normalized_text,
            mapped_code=None,
            mapping_status="unresolved",
        )

    statement = text(
        f"""
        SELECT
          {config.code_column} AS mapped_code,
          {config.cn_name_column} AS matched_cn_name,
          {config.en_name_column} AS matched_en_name
        FROM {config.table_name}
        WHERE DELETE_FLAG = '1'
          AND (
            {config.cn_name_column} LIKE CONCAT('%', :lookup_value, '%')
            OR LOWER({config.en_name_column}) LIKE CONCAT('%', :lookup_value, '%')
          )
        ORDER BY
          -- 固定排序规则：
          -- 1. 中文完全相等
          -- 2. 英文完全相等
          -- 3. 中文前缀匹配
          -- 4. 英文前缀匹配
          -- 5. 中文名称更短优先
          -- 6. 主键升序兜底
          CASE
            WHEN {config.cn_name_column} = :lookup_value THEN 1
            WHEN LOWER({config.en_name_column}) = :lookup_value THEN 2
            WHEN {config.cn_name_column} LIKE CONCAT(:lookup_value, '%') THEN 3
            WHEN LOWER({config.en_name_column}) LIKE CONCAT(:lookup_value, '%') THEN 4
            ELSE 5
          END,
          CHAR_LENGTH({config.cn_name_column}) ASC,
          {config.primary_key_column} ASC
        LIMIT 1
        """
    )

    row = db.execute(statement, {"lookup_value": normalized_text}).mappings().first()
    if not row:
        return CodeResolutionResult(
            raw_text=raw_text,
            normalized_text=normalized_text,
            mapped_code=None,
            mapping_status="unresolved",
        )

    matched_cn_name = row["matched_cn_name"]
    matched_en_name = row["matched_en_name"]
    # 这里只是为了记录最终命中的来源列，便于后续审计和排查。
    if matched_cn_name == normalized_text:
        matched_source = f"{config.table_name}.{config.cn_name_column}"
        matched_label = matched_cn_name
    elif (matched_en_name or "").lower() == normalized_text:
        matched_source = f"{config.table_name}.{config.en_name_column}"
        matched_label = matched_en_name
    elif isinstance(matched_cn_name, str) and matched_cn_name.startswith(normalized_text):
        matched_source = f"{config.table_name}.{config.cn_name_column}"
        matched_label = matched_cn_name
    else:
        matched_source = f"{config.table_name}.{config.en_name_column}"
        matched_label = matched_en_name

    return CodeResolutionResult(
        raw_text=raw_text,
        normalized_text=normalized_text,
        mapped_code=row["mapped_code"],
        mapping_status="resolved",
        matched_source=matched_source,
        matched_label=matched_label,
    )


def _resolve_code_by_exact_code(
    db: Session,
    config: ResolutionConfig,
    raw_code: str | None,
) -> CodeResolutionResult:
    """按业务码值本身做精确校验。"""

    normalized_code = _normalize_code_value(raw_code)
    if not normalized_code:
        return CodeResolutionResult(
            raw_text=raw_code,
            normalized_text=None,
            mapped_code=None,
            mapping_status="unresolved",
        )

    statement = text(
        f"""
        SELECT
          {config.code_column} AS mapped_code,
          {config.cn_name_column} AS matched_cn_name,
          {config.en_name_column} AS matched_en_name
        FROM {config.table_name}
        WHERE DELETE_FLAG = '1'
          AND UPPER({config.code_column}) = UPPER(:lookup_code)
        LIMIT 1
        """
    )
    row = db.execute(statement, {"lookup_code": normalized_code}).mappings().first()
    if not row:
        return CodeResolutionResult(
            raw_text=raw_code,
            normalized_text=None,
            mapped_code=None,
            mapping_status="unresolved",
        )

    return CodeResolutionResult(
        raw_text=raw_code,
        normalized_text=None,
        mapped_code=row["mapped_code"],
        mapping_status="resolved",
        matched_source=f"{config.table_name}.{config.code_column}",
        matched_label=row["matched_cn_name"] or row["matched_en_name"] or row["mapped_code"],
    )


def _resolve_subject_name(
    db: Session,
    config: SubjectResolutionConfig,
    raw_text: str | None,
) -> CodeResolutionResult:
    """按 subject_name_text / notes 对学术科目名称做本地映射。"""

    normalized_text = normalize_resolution_text(raw_text)
    if not normalized_text:
        return CodeResolutionResult(
            raw_text=raw_text,
            normalized_text=normalized_text,
            mapped_code=None,
            mapping_status="unresolved",
        )

    alias_mapped_code = config.alias_map.get(normalized_text)
    if alias_mapped_code:
        alias_result = _resolve_code_by_exact_code(
            db,
            config.dictionary,
            alias_mapped_code,
        )
        if alias_result.resolved:
            return CodeResolutionResult(
                raw_text=raw_text,
                normalized_text=normalized_text,
                mapped_code=alias_result.mapped_code,
                mapping_status="resolved",
                matched_source=f"{config.dictionary.table_name}.alias_map",
                matched_label=raw_text,
            )

    like_result = _resolve_code_by_like(
        db,
        config.dictionary,
        raw_text,
    )
    if like_result.resolved:
        return like_result

    return CodeResolutionResult(
        raw_text=raw_text,
        normalized_text=normalized_text,
        mapped_code=None,
        mapping_status="unresolved",
    )


def _extract_subject_lookup_text(
    row: dict[str, Any],
    config: SubjectResolutionConfig,
) -> str | None:
    """优先读取 subject_name_text，旧数据回退读取 notes。"""

    for field_name in (config.name_field, config.fallback_note_field):
        value = row.get(field_name)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def _normalize_code_value(raw_code: Any) -> str | None:
    """统一清洗模型给出的业务码值。"""

    if raw_code is None:
        return None
    if not isinstance(raw_code, str):
        raw_code = str(raw_code)
    normalized_code = raw_code.strip()
    return normalized_code or None
