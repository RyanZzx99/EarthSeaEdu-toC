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
