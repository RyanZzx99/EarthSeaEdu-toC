"""
昵称风控服务。

职责：
1. 统一加载昵称规则、联系方式规则。
2. 对昵称做标准化处理。
3. 产出统一的风控命中结果。
4. 记录昵称审核日志。
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import re
import unicodedata

from sqlalchemy.orm import Session

from backend.models.nickname_guard_models import NicknameAuditLog
from backend.models.nickname_guard_models import NicknameContactPattern
from backend.models.nickname_guard_models import NicknameRuleGroup
from backend.models.nickname_guard_models import NicknameRulePublishLog
from backend.models.nickname_guard_models import NicknameWordRule
from backend.utils.common import now_utc


ZERO_WIDTH_RE = re.compile(r"[\u200b-\u200f\u2060\ufeff]")
SEPARATOR_RE = re.compile(r"[\s\-_.,·|/\\]+")


@dataclass(slots=True)
class NicknameWordRuleRecord:
    """词条规则运行时结构。"""

    rule_id: int
    group_code: str
    group_type: str
    word: str
    normalized_word: str
    match_type: str
    decision: str
    priority: int
    risk_level: str


@dataclass(slots=True)
class NicknameContactPatternRecord:
    """联系方式规则运行时结构。"""

    pattern_id: int
    group_code: str | None
    pattern_name: str
    pattern_type: str
    pattern_regex: str
    decision: str
    priority: int
    risk_level: str


@dataclass(slots=True)
class NicknameGuardHit:
    """昵称风控命中结果。"""

    decision: str
    message: str
    normalized_nickname: str
    hit_source: str | None = None
    hit_rule_id: int | None = None
    hit_pattern_id: int | None = None
    hit_group_code: str | None = None
    hit_content: str | None = None

    @property
    def passed(self) -> bool:
        return self.decision == "pass"


def normalize_nickname_for_check(nickname: str) -> str:
    """
    为昵称风控生成标准检测串。
    """
    value = unicodedata.normalize("NFKC", nickname or "").strip().lower()
    value = ZERO_WIDTH_RE.sub("", value)
    value = SEPARATOR_RE.sub("", value)
    return value


def _is_rule_effective(
    status: str,
    delete_flag: str,
    effective_start_time: datetime | None,
    effective_end_time: datetime | None,
    now: datetime,
) -> bool:
    if delete_flag != "1" or status != "active":
        return False
    if effective_start_time and effective_start_time > now:
        return False
    if effective_end_time and effective_end_time < now:
        return False
    return True


def get_active_nickname_word_rules(db: Session) -> list[NicknameWordRuleRecord]:
    """
    加载当前生效的昵称词条规则。
    """
    now = now_utc()
    rows = (
        db.query(NicknameWordRule, NicknameRuleGroup)
        .join(NicknameRuleGroup, NicknameWordRule.group_id == NicknameRuleGroup.id)
        .filter(
            NicknameWordRule.delete_flag == "1",
            NicknameRuleGroup.delete_flag == "1",
        )
        .order_by(
            NicknameWordRule.priority.asc(),
            NicknameRuleGroup.priority.asc(),
            NicknameWordRule.id.asc(),
        )
        .all()
    )

    records: list[NicknameWordRuleRecord] = []
    for rule, group in rows:
        if not _is_rule_effective(
            status=group.status,
            delete_flag=group.delete_flag,
            effective_start_time=group.effective_start_time,
            effective_end_time=group.effective_end_time,
            now=now,
        ):
            continue

        if not _is_rule_effective(
            status=rule.status,
            delete_flag=rule.delete_flag,
            effective_start_time=rule.effective_start_time,
            effective_end_time=rule.effective_end_time,
            now=now,
        ):
            continue

        records.append(
            NicknameWordRuleRecord(
                rule_id=rule.id,
                group_code=group.group_code,
                group_type=group.group_type,
                word=rule.word,
                normalized_word=rule.normalized_word,
                match_type=rule.match_type,
                decision=rule.decision,
                priority=rule.priority,
                risk_level=rule.risk_level,
            )
        )

    return records


def get_active_nickname_contact_patterns(db: Session) -> list[NicknameContactPatternRecord]:
    """
    加载当前生效的联系方式规则。
    """
    now = now_utc()
    rows = (
        db.query(NicknameContactPattern, NicknameRuleGroup)
        .outerjoin(NicknameRuleGroup, NicknameContactPattern.group_id == NicknameRuleGroup.id)
        .filter(NicknameContactPattern.delete_flag == "1")
        .order_by(
            NicknameContactPattern.priority.asc(),
            NicknameContactPattern.id.asc(),
        )
        .all()
    )

    records: list[NicknameContactPatternRecord] = []
    for pattern, group in rows:
        if group is not None and not _is_rule_effective(
            status=group.status,
            delete_flag=group.delete_flag,
            effective_start_time=group.effective_start_time,
            effective_end_time=group.effective_end_time,
            now=now,
        ):
            continue

        if not _is_rule_effective(
            status=pattern.status,
            delete_flag=pattern.delete_flag,
            effective_start_time=pattern.effective_start_time,
            effective_end_time=pattern.effective_end_time,
            now=now,
        ):
            continue

        records.append(
            NicknameContactPatternRecord(
                pattern_id=pattern.id,
                group_code=group.group_code if group else None,
                pattern_name=pattern.pattern_name,
                pattern_type=pattern.pattern_type,
                pattern_regex=pattern.pattern_regex,
                decision=pattern.decision,
                priority=pattern.priority,
                risk_level=pattern.risk_level,
            )
        )

    return records


def _match_word_rule(normalized_nickname: str, rule: NicknameWordRuleRecord) -> bool:
    if not rule.normalized_word:
        return False

    if rule.match_type == "exact":
        return normalized_nickname == rule.normalized_word
    if rule.match_type == "prefix":
        return normalized_nickname.startswith(rule.normalized_word)
    if rule.match_type == "suffix":
        return normalized_nickname.endswith(rule.normalized_word)
    if rule.match_type == "regex":
        return re.search(rule.normalized_word, normalized_nickname) is not None

    return rule.normalized_word in normalized_nickname


def _get_decision_message(decision: str, group_type: str | None = None) -> str:
    if decision == "reject":
        if group_type == "reserved":
            return "昵称包含受保护内容，请重新输入"
        return "昵称包含违规内容，请重新输入"
    if decision == "review":
        return "昵称存在风险，请重新输入"
    return "昵称可使用"


def _build_hit_from_word_rule(
    normalized_nickname: str,
    rule: NicknameWordRuleRecord,
) -> NicknameGuardHit:
    return NicknameGuardHit(
        decision=rule.decision,
        message=_get_decision_message(rule.decision, rule.group_type),
        normalized_nickname=normalized_nickname,
        hit_source="word_rule",
        hit_rule_id=rule.rule_id,
        hit_group_code=rule.group_code,
        hit_content=rule.word,
    )


def _build_hit_from_contact_pattern(
    normalized_nickname: str,
    pattern: NicknameContactPatternRecord,
    match_content: str,
) -> NicknameGuardHit:
    return NicknameGuardHit(
        decision=pattern.decision,
        message=_get_decision_message(pattern.decision),
        normalized_nickname=normalized_nickname,
        hit_source="contact_pattern",
        hit_pattern_id=pattern.pattern_id,
        hit_group_code=pattern.group_code,
        hit_content=match_content,
    )


def evaluate_nickname_guard(
    db: Session,
    nickname: str,
) -> NicknameGuardHit:
    """
    执行昵称风控检查。

    规则顺序：
    1. 先做基础空值判断。
    2. 先识别白名单，只作为后续普通词规则降误伤参考。
    3. 优先拦截 reserved / reject 词条。
    4. 再跑联系方式规则。
    5. 再处理 review 词条。
    """
    normalized_nickname = normalize_nickname_for_check(nickname)
    if not normalized_nickname:
        return NicknameGuardHit(
            decision="reject",
            message="请输入昵称",
            normalized_nickname=normalized_nickname,
            hit_source="system",
        )

    word_rules = get_active_nickname_word_rules(db)
    contact_patterns = get_active_nickname_contact_patterns(db)

    has_whitelist_hit = any(
        rule.group_type == "whitelist" and _match_word_rule(normalized_nickname, rule)
        for rule in word_rules
    )

    for rule in word_rules:
        if rule.group_type == "whitelist":
            continue
        if rule.decision != "reject":
            continue
        if not _match_word_rule(normalized_nickname, rule):
            continue
        return _build_hit_from_word_rule(normalized_nickname, rule)

    for pattern in contact_patterns:
        matched = re.search(pattern.pattern_regex, normalized_nickname, re.IGNORECASE)
        if matched:
            return _build_hit_from_contact_pattern(
                normalized_nickname=normalized_nickname,
                pattern=pattern,
                match_content=matched.group(0),
            )

    for rule in word_rules:
        if rule.group_type == "whitelist":
            continue
        if rule.decision != "review":
            continue
        if not _match_word_rule(normalized_nickname, rule):
            continue
        if has_whitelist_hit and rule.group_type not in {"reserved", "contact"}:
            continue
        return _build_hit_from_word_rule(normalized_nickname, rule)

    return NicknameGuardHit(
        decision="pass",
        message="昵称可使用",
        normalized_nickname=normalized_nickname,
    )


def get_current_nickname_rule_batch(db: Session) -> str | None:
    """
    获取当前最新规则发布批次。
    """
    row = (
        db.query(NicknameRulePublishLog)
        .filter(NicknameRulePublishLog.delete_flag == "1")
        .order_by(
            NicknameRulePublishLog.published_time.desc(),
            NicknameRulePublishLog.id.desc(),
        )
        .first()
    )
    if not row:
        return None
    return row.publish_batch_no


def create_nickname_audit_log(
    db: Session,
    *,
    scene: str,
    raw_nickname: str,
    guard_hit: NicknameGuardHit,
    user_id: str | None = None,
    trace_id: str | None = None,
    client_ip: str | None = None,
    user_agent: str | None = None,
    app_version: str | None = None,
    extra_json: dict | list | None = None,
) -> NicknameAuditLog:
    """
    写入昵称审核日志。
    """
    row = NicknameAuditLog(
        trace_id=trace_id,
        user_id=user_id,
        scene=scene,
        raw_nickname=raw_nickname,
        normalized_nickname=guard_hit.normalized_nickname,
        decision=guard_hit.decision,
        hit_source=guard_hit.hit_source,
        hit_rule_id=guard_hit.hit_rule_id,
        hit_pattern_id=guard_hit.hit_pattern_id,
        hit_group_code=guard_hit.hit_group_code,
        hit_content=guard_hit.hit_content,
        message=guard_hit.message,
        client_ip=client_ip,
        user_agent=user_agent,
        app_version=app_version,
        rule_version_batch=get_current_nickname_rule_batch(db),
        extra_json=extra_json,
        delete_flag="1",
    )

    db.add(row)
    db.commit()
    db.refresh(row)
    return row
