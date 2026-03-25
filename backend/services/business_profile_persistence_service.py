"""
正式业务表持久化服务。

这一层的目标是：
1. 把 extraction_prompt 产出的最终 profile_json 转成更稳定的 db_payload。
2. 按固定顺序把 db_payload 写入正式业务表。
3. 让 AI 会话侧和正式业务表入库解耦，便于后续独立维护。

当前设计取向：
1. 先实现“最小可用的正式入库链路”。
2. 主表采用 upsert，保证重复执行时可以覆盖到最新快照。
3. 明细表采用“按 student_id 删除旧快照，再插入新快照”的方式，避免重复堆积。

说明：
1. 这里不是把几十张表都 ORM 化，而是先用 SQLAlchemy text 执行原生 SQL。
2. 这样更适合你当前项目已经明确好的表结构，也能更快落地。
"""

from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
from typing import Any
from uuid import uuid4

from sqlalchemy import text
from sqlalchemy.orm import Session


SINGLE_ROW_TABLES = [
    "student_basic_info",
    "student_academic",
    "student_academic_a_level_profile",
    "student_academic_ap_profile",
    "student_academic_ib_profile",
    "student_academic_chinese_high_school_profile",
    "student_language",
    "student_standardized_tests",
    "student_competitions",
    "student_activities",
    "student_projects_experience",
]

MULTI_ROW_TABLES = [
    "student_basic_info_curriculum_system",
    "student_academic_a_level_subject",
    "student_academic_ap_course",
    "student_academic_ib_subject",
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
    "student_project_outputs",
]

ALL_TABLES = SINGLE_ROW_TABLES + MULTI_ROW_TABLES

# 中文注释：
# 这里维护“程序侧默认值”映射，用来兜底那些数据库层虽然配置了 DEFAULT，
# 但由于当前代码是显式把列和值一起写入 SQL，如果值传了 None，就不会再走数据库默认值。
# 因此像 `use_best_single_test` 这类 NOT NULL 且有默认值的字段，必须在程序层先补齐。
COLUMN_DEFAULTS: dict[str, dict[str, Any]] = {
    "student_basic_info_curriculum_system": {
        "is_primary": 0,
    },
    "student_academic_a_level_subject": {
        "is_predicted": 0,
        "exam_series": "",
    },
    "student_academic_ap_course": {
        "is_predicted": 0,
    },
    "student_academic_ib_subject": {
        "is_predicted": 0,
    },
    "student_language": {
        "use_best_single_test": 1,
        "prefer_scored_over_estimated": 1,
    },
    "student_language_ielts": {
        "is_best_score": 0,
    },
    "student_language_toefl_ibt": {
        "is_best_score": 0,
    },
    "student_language_toefl_essentials": {
        "is_best_score": 0,
    },
    "student_language_det": {
        "is_best_score": 0,
    },
    "student_language_pte": {
        "is_best_score": 0,
    },
    "student_language_languagecert": {
        "is_best_score": 0,
    },
    "student_language_cambridge": {
        "is_best_score": 0,
    },
    "student_language_other": {
        "is_best_score": 0,
    },
    "student_standardized_tests": {
        "is_applicable": 1,
    },
    "student_standardized_test_records": {
        "is_best_score": 0,
    },
    "student_competition_entries": {
        "sort_order": 1,
    },
    "student_activity_entries": {
        "sort_order": 1,
    },
    "student_project_entries": {
        "sort_order": 1,
    },
    "student_project_outputs": {
        "sort_order": 1,
    },
}

# 中文注释：
# 语言明细表的结构虽然不同，但“状态推断”逻辑是一致的：
# 1. 如果已经有正式成绩，则应视为 `SCORED`
# 2. 如果只有预估分，则应视为 `ESTIMATED`
# 3. 如果已有考试类型/日期等计划性信息，但没有分数，则视为 `PLANNED`
# 这套配置把每张语言明细表的“正式分字段 / 预估分字段 / 主表考试类型码值”集中维护，
# 方便后面统一做程序侧兜底，避免因为 prompt 漏填 `status_code` 导致正式入库失败。
LANGUAGE_DETAIL_TABLE_CONFIG: dict[str, dict[str, Any]] = {
    "student_language_ielts": {
        "test_type_code": "IELTS",
        "status_field": "status_code",
        "actual_score_fields": [
            "overall_score",
            "reading_score",
            "listening_score",
            "speaking_score",
            "writing_score",
        ],
        "estimated_score_fields": ["estimated_total"],
    },
    "student_language_toefl_ibt": {
        "test_type_code": "TOEFL_IBT",
        "status_field": "status_code",
        "actual_score_fields": [
            "total_score",
            "reading_score",
            "listening_score",
            "speaking_score",
            "writing_score",
        ],
        "estimated_score_fields": ["estimated_total"],
    },
    "student_language_toefl_essentials": {
        "test_type_code": "TOEFL_ESSENTIALS",
        "status_field": "status_code",
        "actual_score_fields": [
            "core_score_1_12",
            "literacy_1_12",
            "conversation_1_12",
            "comprehension_1_12",
        ],
        "estimated_score_fields": ["estimated_total"],
    },
    "student_language_det": {
        "test_type_code": "DET",
        "status_field": "status_code",
        "actual_score_fields": [
            "total_score",
            "literacy_score",
            "comprehension_score",
            "conversation_score",
            "production_score",
        ],
        "estimated_score_fields": ["estimated_total"],
    },
    "student_language_pte": {
        "test_type_code": "PTE",
        "status_field": "status_code",
        "actual_score_fields": [
            "total_score",
            "reading_score",
            "listening_score",
            "speaking_score",
            "writing_score",
        ],
        "estimated_score_fields": ["estimated_total"],
    },
    "student_language_languagecert": {
        "test_type_code": "LANGUAGECERT",
        "status_field": "status_code",
        "actual_score_fields": [
            "total_score",
            "reading_score",
            "listening_score",
            "speaking_score",
            "writing_score",
        ],
        "estimated_score_fields": ["estimated_total"],
    },
    "student_language_cambridge": {
        "test_type_code": "CAMBRIDGE",
        "status_field": "status_code",
        "actual_score_fields": [
            "total_score",
            "reading_score",
            "use_of_english_score",
            "listening_score",
            "speaking_score",
            "writing_score",
        ],
        "estimated_score_fields": ["estimated_total"],
    },
    "student_language_other": {
        "test_type_code": "OTHER",
        "status_field": "status_code",
        "actual_score_fields": ["score_total"],
        "estimated_score_fields": ["estimated_total"],
    },
}

# 中文注释：
# 标化考试明细表当前只有一张，但同样存在模型只提取了分数、没给状态的问题。
# 因此这里也单独抽成配置，统一走“正式分 -> 预估分 -> 计划中”的推断顺序。
STANDARDIZED_DETAIL_TABLE_CONFIG: dict[str, Any] = {
    "status_field": "status",
    "actual_score_fields": [
        "total_score",
        "sat_erw",
        "sat_math",
        "act_english",
        "act_math",
        "act_reading",
        "act_science",
    ],
    "estimated_score_fields": ["estimated_total_score"],
}

# 中文注释：
# 下面这些字段在“判断一张主表是否真的值得入库”时，应被视为技术字段或程序兜底字段，
# 不能因为它们被自动补齐就误判成“这张表已经有真实业务信息”。
SINGLE_ROW_MEANINGFULNESS_IGNORE_FIELDS: dict[str, set[str]] = {
    "student_basic_info": {
        "student_id",
        "schema_version",
        "profile_type",
    },
    "student_academic": {
        "student_id",
        "student_academic_id",
    },
    "student_academic_a_level_profile": {
        "student_id",
    },
    "student_academic_ap_profile": {
        "student_id",
    },
    "student_academic_ib_profile": {
        "student_id",
    },
    "student_academic_chinese_high_school_profile": {
        "student_id",
    },
    "student_language": {
        "student_id",
        "student_language_id",
        "use_best_single_test",
        "prefer_scored_over_estimated",
    },
    "student_standardized_tests": {
        "student_id",
        "student_standardized_test_id",
    },
    "student_competitions": {
        "student_id",
    },
    "student_activities": {
        "student_id",
    },
    "student_projects_experience": {
        "student_id",
    },
}

# 中文注释：
# 某些主表本身即使业务字段为空，只要子表有数据也必须先落主表，
# 否则后续子表插入时会失去父记录依赖。
SINGLE_ROW_CHILD_TABLE_DEPENDENCIES: dict[str, list[str]] = {
    "student_basic_info": [
        "student_basic_info_curriculum_system",
    ],
    "student_academic": [
        "student_academic_a_level_profile",
        "student_academic_ap_profile",
        "student_academic_ib_profile",
        "student_academic_chinese_high_school_profile",
        "student_academic_a_level_subject",
        "student_academic_ap_course",
        "student_academic_ib_subject",
        "student_academic_chinese_high_school_subject",
    ],
    "student_language": [
        "student_language_ielts",
        "student_language_toefl_ibt",
        "student_language_toefl_essentials",
        "student_language_det",
        "student_language_pte",
        "student_language_languagecert",
        "student_language_cambridge",
        "student_language_other",
    ],
    "student_standardized_tests": [
        "student_standardized_test_records",
    ],
    "student_competitions": [
        "student_competition_entries",
    ],
    "student_activities": [
        "student_activity_entries",
    ],
    "student_projects_experience": [
        "student_project_entries",
        "student_project_outputs",
    ],
}


@dataclass(slots=True)
class BusinessPersistenceResult:
    """
    正式业务表入库结果。

    用途：
    1. 返回给上层 pipeline，方便写入 ai_chat_profile_results。
    2. 让上层知道最终写了哪些表。
    """

    db_payload: dict[str, Any]
    persisted_tables: list[str]


def build_db_payload_from_profile_json(
    profile_json: dict[str, Any],
    *,
    student_id: str,
) -> dict[str, Any]:
    """
    从最终 profile_json 构建 db_payload。

    当前这一步做 4 件事：
    1. 深拷贝，避免直接污染原始 profile_json。
    2. 统一把 student_id 回填到所有对象表、明细表。
    3. 补齐程序生成字段：
       - student_academic_id
       - student_language_id
       - student_standardized_test_id
    4. 为 project_entries / project_outputs 建立稳定的 project_id 映射。
    """

    payload = deepcopy(profile_json)

    # 中文注释：先保证所有顶层 key 都存在。
    # 这样后面无论是 upsert 主表还是插明细表，逻辑都更稳定。
    for table_name in ALL_TABLES:
        if table_name in SINGLE_ROW_TABLES:
            payload.setdefault(table_name, {})
        else:
            payload.setdefault(table_name, [])

    _inject_student_id(payload, student_id=student_id)
    _fill_program_generated_ids(payload, student_id=student_id)
    _normalize_project_id_mapping(payload)
    _normalize_payload_for_persistence(payload)
    return payload


def persist_business_profile_snapshot(
    db: Session,
    *,
    db_payload: dict[str, Any],
    student_id: str,
) -> BusinessPersistenceResult:
    """
    把 db_payload 正式写入业务表。

    写入策略：
    1. 主表使用 upsert。
    2. 明细表先清理旧快照，再插入新快照。
    3. 整体放在一个事务里，任何一步失败都回滚。
    """

    persisted_tables: list[str] = []

    try:
        # 中文注释：先处理单行主表。
        # 这些表一般是一名学生只保留一条快照，因此直接 upsert 即可。
        for table_name in SINGLE_ROW_TABLES:
            row = db_payload.get(table_name) or {}
            if not isinstance(row, dict):
                continue
            if not row:
                continue
            if not _should_persist_single_row(
                table_name=table_name,
                row=row,
                db_payload=db_payload,
            ):
                continue
            _upsert_single_row(
                db,
                table_name=table_name,
                row=row,
            )
            persisted_tables.append(table_name)

        # 中文注释：明细表先删除旧快照，再插入新快照。
        # 这样可以避免重复执行同一次会话时，竞赛/活动/项目等数组表不断叠加。
        _delete_existing_multi_rows(db, student_id=student_id)

        for table_name in MULTI_ROW_TABLES:
            rows = db_payload.get(table_name) or []
            if not isinstance(rows, list):
                continue
            meaningful_rows = [
                row for row in rows if _is_meaningful_multi_row(table_name=table_name, row=row)
            ]
            if not meaningful_rows:
                continue
            for row in meaningful_rows:
                _insert_multi_row(
                    db,
                    table_name=table_name,
                    row=row,
                )
            persisted_tables.append(table_name)

        db.commit()
    except Exception:
        db.rollback()
        raise

    return BusinessPersistenceResult(
        db_payload=db_payload,
        persisted_tables=persisted_tables,
    )


def _inject_student_id(payload: dict[str, Any], *, student_id: str) -> None:
    """
    把 student_id 统一回填到所有需要的对象和数组元素里。

    原因：
    1. 虽然 extraction_prompt 已要求模型返回 student_id，
       但程序侧仍要兜底，避免模型漏填导致正式入库失败。
    2. 正式 student_id 只能以当前登录用户为准，不能完全信任模型输出。
    """

    for table_name in ALL_TABLES:
        table_value = payload.get(table_name)
        if isinstance(table_value, dict):
            if "student_id" in table_value or table_name in SINGLE_ROW_TABLES:
                table_value["student_id"] = student_id
        elif isinstance(table_value, list):
            for row in table_value:
                if isinstance(row, dict) and "student_id" in row:
                    row["student_id"] = student_id


def _fill_program_generated_ids(payload: dict[str, Any], *, student_id: str) -> None:
    """
    补齐由程序负责生成的主键字段。

    当前补齐：
    1. student_academic.student_academic_id
    2. student_language.student_language_id
    3. student_standardized_tests.student_standardized_test_id

    说明：
    1. 这些字段在 extraction_prompt 里允许为 null。
    2. 正式入库前必须由程序生成。
    """

    academic = payload.get("student_academic") or {}
    if isinstance(academic, dict) and not academic.get("student_academic_id"):
        academic["student_academic_id"] = f"acad_{uuid4()}"

    language = payload.get("student_language") or {}
    if isinstance(language, dict) and not language.get("student_language_id"):
        language["student_language_id"] = f"lang_{uuid4()}"

    standardized = payload.get("student_standardized_tests") or {}
    if isinstance(standardized, dict) and not standardized.get("student_standardized_test_id"):
        standardized["student_standardized_test_id"] = f"std_{uuid4()}"

    basic_info = payload.get("student_basic_info") or {}
    if isinstance(basic_info, dict):
        if not basic_info.get("schema_version"):
            basic_info["schema_version"] = "v1"
        if not basic_info.get("profile_type"):
            basic_info["profile_type"] = "student_profile_build"
        basic_info["student_id"] = student_id


def _normalize_project_id_mapping(payload: dict[str, Any]) -> None:
    """
    规范 project_entries 和 project_outputs 之间的 project_id。

    这里需要程序兜底的原因：
    1. student_project_entries.project_id 是项目主键。
    2. student_project_outputs.project_id 依赖它。
    3. extraction_prompt 允许 project_id 为 null，因此入库前要补齐。

    当前策略：
    1. 如果项目本身没给 project_id，则用负数临时ID补齐。
    2. 如果只有一个项目且 output.project_id 为空，则自动挂到这唯一项目上。
    3. 如果有多个项目且 output.project_id 为空，则该 output 保持为空，后续不会被插入。
    """

    project_entries = payload.get("student_project_entries") or []
    project_outputs = payload.get("student_project_outputs") or []
    if not isinstance(project_entries, list) or not isinstance(project_outputs, list):
        return

    generated_ids: list[int] = []
    for index, row in enumerate(project_entries, start=1):
        if not isinstance(row, dict):
            continue
        if row.get("project_id") is None:
            row["project_id"] = -index
        generated_ids.append(int(row["project_id"]))

    if len(generated_ids) == 1:
        only_project_id = generated_ids[0]
        for row in project_outputs:
            if isinstance(row, dict) and row.get("project_id") is None:
                row["project_id"] = only_project_id


def _normalize_payload_for_persistence(payload: dict[str, Any]) -> None:
    """
    对最终 db_payload 做一次“正式入库前归一化”。

    这一步的作用不是改业务语义，而是补程序侧兜底：
    1. 语言明细如果只有分数、没有 `status_code`，自动推断为 `SCORED / ESTIMATED / PLANNED`
    2. 标化明细如果只有分数、没有 `status`，也按同样规则自动推断
    3. 尝试把明细里已经明确的信息回填到语言/标化主表，减少主表空快照

    这样做的原因：
    1. prompt 目前已经尽量输出完整 schema，但仍可能漏掉少量必填字段
    2. 正式业务表存在 NOT NULL 约束，必须在程序层做最后一道兜底
    """

    _normalize_language_payload(payload)
    _normalize_standardized_payload(payload)


def _normalize_language_payload(payload: dict[str, Any]) -> None:
    """
    归一化语言维度 payload。

    核心职责：
    1. 为各语言明细表补 `status_code`
    2. 在没有显式最佳成绩标记时，尽量自动确定一条最佳成绩
    3. 把最佳成绩的关键信息回填到 `student_language` 主表
    """

    language_main = payload.get("student_language")
    if not isinstance(language_main, dict):
        language_main = {}
        payload["student_language"] = language_main

    detail_candidates: list[tuple[str, dict[str, Any]]] = []
    has_explicit_best_score = False

    for table_name, config in LANGUAGE_DETAIL_TABLE_CONFIG.items():
        rows = payload.get(table_name)
        if not isinstance(rows, list):
            continue

        for row in rows:
            if not isinstance(row, dict):
                continue

            inferred_status = _infer_status_from_row(
                row=row,
                status_field=config["status_field"],
                actual_score_fields=config["actual_score_fields"],
                estimated_score_fields=config["estimated_score_fields"],
            )
            if inferred_status and row.get(config["status_field"]) is None:
                row[config["status_field"]] = inferred_status

            # 中文注释：
            # 对于已经有正式分或预估分的记录，如果没有证据等级，就先按“学生自述”兜底。
            # 这不会影响后续人工修正，但能让当前快照更完整。
            if (
                row.get(config["status_field"]) in {"SCORED", "ESTIMATED"}
                and row.get("evidence_level_code") is None
            ):
                row["evidence_level_code"] = "SELF_REPORTED"

            if row.get("is_best_score") == 1:
                has_explicit_best_score = True

            if _is_meaningful_multi_row(table_name=table_name, row=row):
                detail_candidates.append((table_name, row))

    best_candidate = _pick_best_language_candidate(detail_candidates)
    if best_candidate and not has_explicit_best_score:
        best_candidate[1]["is_best_score"] = 1

    if not best_candidate:
        return

    best_table_name, best_row = best_candidate
    best_config = LANGUAGE_DETAIL_TABLE_CONFIG[best_table_name]

    if not language_main.get("best_test_type_code"):
        language_main["best_test_type_code"] = best_config["test_type_code"]
    if not language_main.get("best_score_status_code"):
        language_main["best_score_status_code"] = best_row.get(best_config["status_field"])
    if not language_main.get("best_test_date"):
        language_main["best_test_date"] = best_row.get("test_date")
    if not language_main.get("best_language_ability_index_100"):
        language_main["best_language_ability_index_100"] = best_row.get("normalized_index_100")
    if not language_main.get("overall_cefr_level_code"):
        language_main["overall_cefr_level_code"] = best_row.get("cefr_level_code")


def _normalize_standardized_payload(payload: dict[str, Any]) -> None:
    """
    归一化标化考试 payload。

    核心职责：
    1. 给 `student_standardized_test_records` 自动补 `status`
    2. 尽量从明细回填 `student_standardized_tests` 主表
    3. 若已有不适用原因但未明确 `is_applicable`，则自动补成 0
    """

    standardized_main = payload.get("student_standardized_tests")
    if not isinstance(standardized_main, dict):
        standardized_main = {}
        payload["student_standardized_tests"] = standardized_main

    if (
        standardized_main.get("is_applicable") is None
        and standardized_main.get("not_applicable_reason")
    ):
        standardized_main["is_applicable"] = 0

    rows = payload.get("student_standardized_test_records")
    if not isinstance(rows, list):
        return

    candidates: list[dict[str, Any]] = []
    has_explicit_best_score = False
    for row in rows:
        if not isinstance(row, dict):
            continue

        inferred_status = _infer_status_from_row(
            row=row,
            status_field=STANDARDIZED_DETAIL_TABLE_CONFIG["status_field"],
            actual_score_fields=STANDARDIZED_DETAIL_TABLE_CONFIG["actual_score_fields"],
            estimated_score_fields=STANDARDIZED_DETAIL_TABLE_CONFIG["estimated_score_fields"],
        )
        if inferred_status and row.get("status") is None:
            row["status"] = inferred_status

        if row.get("is_best_score") == 1:
            has_explicit_best_score = True

        if _is_meaningful_multi_row(table_name="student_standardized_test_records", row=row):
            candidates.append(row)

    best_row = _pick_best_standardized_candidate(candidates)
    if best_row and not has_explicit_best_score:
        best_row["is_best_score"] = 1

    if standardized_main.get("is_applicable") is None and candidates:
        standardized_main["is_applicable"] = 1

    if not best_row:
        return

    if not standardized_main.get("best_test_type"):
        standardized_main["best_test_type"] = best_row.get("test_type")
    if not standardized_main.get("best_total_score"):
        standardized_main["best_total_score"] = (
            best_row.get("total_score") or best_row.get("estimated_total_score")
        )
    if not standardized_main.get("best_test_date"):
        standardized_main["best_test_date"] = best_row.get("test_date")


def _infer_status_from_row(
    *,
    row: dict[str, Any],
    status_field: str,
    actual_score_fields: list[str],
    estimated_score_fields: list[str],
) -> str | None:
    """
    从一条考试记录里推断状态字段。

    推断优先级固定为：
    1. 已有正式分 -> `SCORED`
    2. 只有预估分 -> `ESTIMATED`
    3. 还有其他有效业务信息 -> `PLANNED`

    这里不直接覆盖模型已给出的状态，只在原状态为空时作为兜底。
    """

    if row.get(status_field):
        return row[status_field]

    if any(_has_meaningful_scalar(row.get(field_name)) for field_name in actual_score_fields):
        return "SCORED"

    if any(_has_meaningful_scalar(row.get(field_name)) for field_name in estimated_score_fields):
        return "ESTIMATED"

    for field_name, value in row.items():
        if field_name in {
            "student_id",
            status_field,
            "is_best_score",
            "evidence_level_code",
            "normalized_index_100",
            "notes",
        }:
            continue
        if _has_meaningful_scalar(value):
            return "PLANNED"
    return None


def _pick_best_language_candidate(
    candidates: list[tuple[str, dict[str, Any]]],
) -> tuple[str, dict[str, Any]] | None:
    """
    选择语言维度里的最佳候选记录。

    当前策略是轻量但稳定的：
    1. 优先使用 `is_best_score = 1`
    2. 否则按状态优先级选择：SCORED > ESTIMATED > PLANNED
    3. 再按原始顺序兜底
    """

    if not candidates:
        return None

    explicit_best = next(
        ((table_name, row) for table_name, row in candidates if row.get("is_best_score") == 1),
        None,
    )
    if explicit_best:
        return explicit_best

    status_priority = {
        "SCORED": 1,
        "ESTIMATED": 2,
        "PLANNED": 3,
        None: 9,
    }
    return min(
        candidates,
        key=lambda item: status_priority.get(item[1].get("status_code"), 9),
    )


def _pick_best_standardized_candidate(
    candidates: list[dict[str, Any]],
) -> dict[str, Any] | None:
    """
    选择标化维度里的最佳候选记录。

    当前策略与语言维度保持一致，确保同类问题使用同一套优先级。
    """

    if not candidates:
        return None

    explicit_best = next((row for row in candidates if row.get("is_best_score") == 1), None)
    if explicit_best:
        return explicit_best

    status_priority = {
        "SCORED": 1,
        "ESTIMATED": 2,
        "PLANNED": 3,
        None: 9,
    }
    return min(
        candidates,
        key=lambda row: status_priority.get(row.get("status"), 9),
    )


def _has_meaningful_scalar(value: Any) -> bool:
    """
    判断单个标量值是否可视为“有业务含义”。

    这里特意把 0 也视为有效值，
    因为部分字段虽然很少出现 0，但从语义上它仍然是用户明确给出的信息。
    """

    if value is None:
        return False
    if isinstance(value, str):
        return value.strip() != ""
    return True


def _upsert_single_row(
    db: Session,
    *,
    table_name: str,
    row: dict[str, Any],
) -> None:
    """
    对单行主表执行 upsert。

    设计原因：
    1. 这些表是一名学生一条快照，不适合重复 insert。
    2. 使用 ON DUPLICATE KEY UPDATE 可以自然覆盖到最新 AI 结果。
    """

    normalized_row = _apply_table_column_defaults(
        table_name=table_name,
        row=row,
    )
    columns = list(normalized_row.keys())
    escaped_columns = ", ".join(f"`{column}`" for column in columns)
    value_placeholders = ", ".join(f":{column}" for column in columns)
    update_assignments = ", ".join(
        f"`{column}` = VALUES(`{column}`)" for column in columns
    )
    sql = text(
        f"""
        INSERT INTO `{table_name}` ({escaped_columns})
        VALUES ({value_placeholders})
        ON DUPLICATE KEY UPDATE {update_assignments}
        """
    )
    db.execute(sql, normalized_row)


def _insert_multi_row(
    db: Session,
    *,
    table_name: str,
    row: dict[str, Any],
) -> None:
    """
    插入一条明细表记录。

    注意：
    1. 这里只插入 payload 中实际带出来的列。
    2. 对于 auto_increment 主键，如果 payload 里没有该字段，就不会参与 insert。
    """

    normalized_row = _apply_table_column_defaults(
        table_name=table_name,
        row=row,
    )
    columns = list(normalized_row.keys())
    escaped_columns = ", ".join(f"`{column}`" for column in columns)
    value_placeholders = ", ".join(f":{column}" for column in columns)
    sql = text(
        f"""
        INSERT INTO `{table_name}` ({escaped_columns})
        VALUES ({value_placeholders})
        """
    )
    db.execute(sql, normalized_row)


def _delete_existing_multi_rows(db: Session, *, student_id: str) -> None:
    """
    清理当前 student_id 在明细表中的旧快照数据。

    删除顺序必须注意外键方向：
    1. 先删最深层的 student_project_outputs。
    2. 再删 project_entries。
    3. 其余表基本都可以直接按 student_id 删除。
    """

    db.execute(
        text(
            """
            DELETE spo
            FROM `student_project_outputs` spo
            INNER JOIN `student_project_entries` spe
              ON spo.project_id = spe.project_id
            WHERE spe.student_id = :student_id
            """
        ),
        {"student_id": student_id},
    )

    delete_sql_by_student_id = [
        "DELETE FROM `student_project_entries` WHERE student_id = :student_id",
        "DELETE FROM `student_activity_entries` WHERE student_id = :student_id",
        "DELETE FROM `student_competition_entries` WHERE student_id = :student_id",
        "DELETE FROM `student_standardized_test_records` WHERE student_id = :student_id",
        "DELETE FROM `student_language_other` WHERE student_id = :student_id",
        "DELETE FROM `student_language_cambridge` WHERE student_id = :student_id",
        "DELETE FROM `student_language_languagecert` WHERE student_id = :student_id",
        "DELETE FROM `student_language_pte` WHERE student_id = :student_id",
        "DELETE FROM `student_language_det` WHERE student_id = :student_id",
        "DELETE FROM `student_language_toefl_essentials` WHERE student_id = :student_id",
        "DELETE FROM `student_language_toefl_ibt` WHERE student_id = :student_id",
        "DELETE FROM `student_language_ielts` WHERE student_id = :student_id",
        "DELETE FROM `student_academic_chinese_high_school_subject` WHERE student_id = :student_id",
        "DELETE FROM `student_academic_ib_subject` WHERE student_id = :student_id",
        "DELETE FROM `student_academic_ap_course` WHERE student_id = :student_id",
        "DELETE FROM `student_academic_a_level_subject` WHERE student_id = :student_id",
        "DELETE FROM `student_basic_info_curriculum_system` WHERE student_id = :student_id",
    ]
    for sql in delete_sql_by_student_id:
        db.execute(text(sql), {"student_id": student_id})


def _apply_table_column_defaults(
    *,
    table_name: str,
    row: dict[str, Any],
) -> dict[str, Any]:
    """
    为单表行数据补齐程序侧默认值。

    设计原因：
    1. 当前 SQL 写入是“显式列 + 显式值”，传了 None 就不会触发数据库 DEFAULT。
    2. 因此必须在程序层把常见的 NOT NULL 默认值先补进去，避免保存阶段才抛完整性错误。
    """

    normalized_row = dict(row)
    default_values = COLUMN_DEFAULTS.get(table_name) or {}
    for field_name, default_value in default_values.items():
        if normalized_row.get(field_name) is None:
            normalized_row[field_name] = default_value
    return normalized_row


def _should_persist_single_row(
    *,
    table_name: str,
    row: dict[str, Any],
    db_payload: dict[str, Any],
) -> bool:
    """
    判断单行主表是否真的值得入库。

    规则说明：
    1. `student_basic_info` 是整个档案的基础主表，始终保留。
    2. 其余主表如果只有 student_id、程序生成 ID、程序默认值，而没有真实业务信息，
       则不应该硬插一条“空快照”。
    3. 但如果它的子表已经有数据，则父表仍然必须保留，避免后续子表失去父级承接。
    """

    if table_name == "student_basic_info":
        return True

    ignored_fields = SINGLE_ROW_MEANINGFULNESS_IGNORE_FIELDS.get(table_name) or {"student_id"}
    for key, value in row.items():
        if key in ignored_fields:
            continue
        if value is None:
            continue
        if isinstance(value, str) and value == "":
            continue
        if isinstance(value, (list, dict)) and not value:
            continue
        return True

    for child_table_name in SINGLE_ROW_CHILD_TABLE_DEPENDENCIES.get(table_name, []):
        child_value = db_payload.get(child_table_name)

        # 中文注释：
        # 这里的“子表依赖”既可能是数组明细表，也可能是单行 profile 子表。
        # 之前这里只判断 list，导致像 `student_academic_ap_profile` 这种单行子表
        # 即使已经有 notes 或其它内容，也不会触发父表 `student_academic` 保留，
        # 最终就会出现“先插子表、父表却被跳过”，从而撞外键约束。
        if isinstance(child_value, list):
            if any(
                _is_meaningful_multi_row(table_name=child_table_name, row=child_row)
                for child_row in child_value
            ):
                return True
            continue

        if isinstance(child_value, dict) and child_value:
            if _has_meaningful_single_row_content(
                table_name=child_table_name,
                row=child_value,
            ):
                return True
    return False


def _has_meaningful_single_row_content(
    *,
    table_name: str,
    row: dict[str, Any],
) -> bool:
    """
    判断单行对象表自身是否已经包含真实业务内容。

    这个函数和 `_should_persist_single_row()` 的区别是：
    1. 这里只看“当前这一行本身”有没有内容
    2. 不再继续递归检查它的子依赖

    这样做的原因：
    1. 父表判断子表是否有内容时，只需要知道“子表本身是否值得入库”
    2. 避免出现父表 -> 子表 -> 再递归回父级依赖的复杂判断
    """

    ignored_fields = SINGLE_ROW_MEANINGFULNESS_IGNORE_FIELDS.get(table_name) or {"student_id"}
    for key, value in row.items():
        if key in ignored_fields:
            continue
        if value is None:
            continue
        if isinstance(value, str) and value == "":
            continue
        if isinstance(value, (list, dict)) and not value:
            continue
        return True
    return False


def _is_meaningful_multi_row(*, table_name: str, row: Any) -> bool:
    """
    判断一条明细行是否真的有内容。

    目的：
    1. 避免把 prompt schema 里的“全 null 占位行”插进数据库。
    2. 只要除 student_id 之外还有一个有效值，就认为这行值得入库。
    """

    if not isinstance(row, dict):
        return False

    for key, value in row.items():
        if key == "student_id":
            continue
        if key in (COLUMN_DEFAULTS.get(table_name) or {}) and value == (COLUMN_DEFAULTS.get(table_name) or {}).get(key):
            # 中文注释：
            # 例如 sort_order=1、is_best_score=0 这类程序兜底值，
            # 不能单独证明一条明细行真的有业务内容，因此在“是否值得入库”判断里忽略。
            continue
        if value is None:
            continue
        if isinstance(value, str) and value == "":
            continue
        if isinstance(value, (list, dict)) and not value:
            continue
        return True
    return False
