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


# 中文注释：零宽字符经常被用于绕过敏感词检测，这里统一在检测前剔除。
ZERO_WIDTH_RE = re.compile(r"[\u200b-\u200f\u2060\ufeff]")

# 中文注释：把空白、横线、点号、分隔符等统一去掉，减少“微 信”“v-x”这类绕过。
SEPARATOR_RE = re.compile(r"[\s\-_.,·|/\\]+")


@dataclass(slots=True)
class NicknameWordRuleRecord:
    """
    词条规则运行时结构。

    说明：
    1. 数据库模型适合落库，不适合直接在匹配逻辑里反复读取 ORM 对象。
    2. 这里把真正匹配要用到的字段压平成轻量结构，便于后续排序和命中。
    """

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
    """
    联系方式规则运行时结构。

    说明：
    1. 联系方式规则和词条规则拆开，是为了避免“词条 contains”与“正则 pattern”混在一起。
    2. 命中时需要带上 pattern_id，方便运营回查是哪一条正则生效。
    """

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
    """
    昵称风控命中结果。

    说明：
    1. 这是昵称风控服务和上层业务之间的统一返回结构。
    2. 上层只关心是否通过、给用户什么提示、命中了哪条规则。
    3. 审核日志也复用这个结构，避免接口返回和日志记录出现两套判断标准。
    """

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


def _normalize_rule_word(word: str) -> str:
    """
    词条入库时沿用昵称检测串的归一化规则，避免同义变体重复建规则。
    """
    return normalize_nickname_for_check(word)


def normalize_nickname_for_check(nickname: str) -> str:
    """
    为昵称风控生成标准检测串。

    处理顺序：
    1. 先做 Unicode NFKC 归一，收敛全角半角和部分兼容字符。
    2. 再 trim 并转小写，统一英文匹配口径。
    3. 去掉零宽字符，防止隐形绕过。
    4. 去掉空格和常见分隔符，提升“拆字导流”命中率。
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
    """
    判断规则当前是否生效。

    规则必须同时满足：
    1. delete_flag = 1
    2. status = active
    3. 如果配置了生效开始时间，则当前时间必须已开始
    4. 如果配置了生效结束时间，则当前时间必须未结束
    """
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

    说明：
    1. 这里同时读取规则组和规则明细，因为最终命中结果里需要 group_code / group_type。
    2. 先在 SQL 层过滤 delete_flag，再在 Python 层补充生效时间判断。
    3. 排序遵循“规则优先级 -> 分组优先级 -> 规则ID”，保证命中稳定。
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
        # 中文注释：分组失效意味着整个规则包都不应参与本次检测。
        if not _is_rule_effective(
            status=group.status,
            delete_flag=group.delete_flag,
            effective_start_time=group.effective_start_time,
            effective_end_time=group.effective_end_time,
            now=now,
        ):
            continue

        # 中文注释：即便分组有效，单条规则也可能因为停用或过期而失效。
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

    说明：
    1. 联系方式规则允许独立存在，也允许挂在某个 contact 分组下统一管理。
    2. 这里使用 outer join，是为了兼容未来个别 pattern 不挂分组的场景。
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
        # 中文注释：如果 pattern 归属于某个分组，则分组失效时整条 pattern 也应失效。
        if group is not None and not _is_rule_effective(
            status=group.status,
            delete_flag=group.delete_flag,
            effective_start_time=group.effective_start_time,
            effective_end_time=group.effective_end_time,
            now=now,
        ):
            continue

        # 中文注释：再校验 pattern 本身是否启用、生效。
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
    """
    按规则定义的 match_type 执行词条匹配。

    当前支持：
    1. exact   完全匹配
    2. prefix  前缀匹配
    3. suffix  后缀匹配
    4. regex   正则匹配
    5. contains 包含匹配（默认）
    """
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
    """
    把内部决策转换成给前端返回的提示文案。

    说明：
    1. reserved 类型和普通 reject 都属于拦截，但文案上要区分“受保护内容”。
    2. review 当前阶段仍按拒绝返回，只是语义上保留“风险”概念，便于后续升级成人审。
    """
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
    # 中文注释：词条命中时，把命中的规则ID、规则组编码和命中词一并带上，便于日志回查。
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
    # 中文注释：联系方式命中通常来自正则，hit_content 记录实际命中的片段更有排查价值。
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
    # 中文注释：所有规则都围绕“标准检测串”运行，避免原始昵称的格式差异影响命中。
    normalized_nickname = normalize_nickname_for_check(nickname)
    if not normalized_nickname:
        return NicknameGuardHit(
            decision="reject",
            message="请输入昵称",
            normalized_nickname=normalized_nickname,
            hit_source="system",
        )

    # 中文注释：词条规则和联系方式规则分开加载，后续按固定优先级顺序执行。
    word_rules = get_active_nickname_word_rules(db)
    contact_patterns = get_active_nickname_contact_patterns(db)

    # 中文注释：白名单只用于后续降低普通 review 词的误伤，不直接短路整套检测。
    has_whitelist_hit = any(
        rule.group_type == "whitelist" and _match_word_rule(normalized_nickname, rule)
        for rule in word_rules
    )

    # 中文注释：先拦截强规则 reject，包括受保护词和黑名单词，优先级最高。
    for rule in word_rules:
        if rule.group_type == "whitelist":
            continue
        if rule.decision != "reject":
            continue
        if not _match_word_rule(normalized_nickname, rule):
            continue
        return _build_hit_from_word_rule(normalized_nickname, rule)

    # 中文注释：联系方式规则放在强词条之后执行，因为其风险通常高于普通 review 词。
    for pattern in contact_patterns:
        matched = re.search(pattern.pattern_regex, normalized_nickname, re.IGNORECASE)
        if matched:
            return _build_hit_from_contact_pattern(
                normalized_nickname=normalized_nickname,
                pattern=pattern,
                match_content=matched.group(0),
            )

    # 中文注释：最后再处理 review 规则；如果已有白名单命中，则允许跳过部分普通 review 词。
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

    # 中文注释：执行到这里说明没有命中任何拦截或风险规则，视为通过。
    return NicknameGuardHit(
        decision="pass",
        message="昵称可使用",
        normalized_nickname=normalized_nickname,
    )


def get_current_nickname_rule_batch(db: Session) -> str | None:
    """
    获取当前最新规则发布批次。

    说明：
    1. 审核日志记录规则批次后，后面误杀排查时才知道当时跑的是哪一版词库。
    2. 这里默认取最新一条发布记录，后续如需灰度可再扩展按环境或人群取批次。
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


def list_nickname_rule_groups(
    db: Session,
    *,
    status: str | None = None,
    group_type: str | None = None,
) -> list[NicknameRuleGroup]:
    """
    查询昵称规则分组列表。
    """
    query = db.query(NicknameRuleGroup).filter(NicknameRuleGroup.delete_flag == "1")

    if status:
        query = query.filter(NicknameRuleGroup.status == status)
    if group_type:
        query = query.filter(NicknameRuleGroup.group_type == group_type)

    return query.order_by(
        NicknameRuleGroup.priority.asc(),
        NicknameRuleGroup.id.asc(),
    ).all()


def create_nickname_rule_group(
    db: Session,
    *,
    group_code: str,
    group_name: str,
    group_type: str,
    scope: str = "nickname",
    status: str = "draft",
    priority: int = 100,
    description: str | None = None,
) -> NicknameRuleGroup:
    """
    创建昵称规则分组。
    """
    normalized_code = group_code.strip()
    if not normalized_code:
        raise ValueError("group_code 不能为空")

    exists = (
        db.query(NicknameRuleGroup)
        .filter(
            NicknameRuleGroup.group_code == normalized_code,
            NicknameRuleGroup.delete_flag == "1",
        )
        .first()
    )
    if exists:
        raise ValueError("该规则分组编码已存在")

    row = NicknameRuleGroup(
        group_code=normalized_code,
        group_name=group_name.strip(),
        group_type=group_type.strip(),
        scope=scope.strip() or "nickname",
        status=status.strip() or "draft",
        priority=priority,
        description=description.strip() if description else None,
        delete_flag="1",
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def create_nickname_word_rule(
    db: Session,
    *,
    group_id: int,
    word: str,
    match_type: str = "contains",
    decision: str = "reject",
    status: str = "draft",
    priority: int = 100,
    risk_level: str = "medium",
    source: str = "manual",
    note: str | None = None,
) -> NicknameWordRule:
    """
    创建昵称词条规则。
    """
    group = (
        db.query(NicknameRuleGroup)
        .filter(
            NicknameRuleGroup.id == group_id,
            NicknameRuleGroup.delete_flag == "1",
        )
        .first()
    )
    if not group:
        raise ValueError("规则分组不存在")

    normalized_word = _normalize_rule_word(word)
    if not normalized_word:
        raise ValueError("词条不能为空")

    exists = (
        db.query(NicknameWordRule)
        .filter(
            NicknameWordRule.group_id == group_id,
            NicknameWordRule.normalized_word == normalized_word,
            NicknameWordRule.match_type == match_type,
            NicknameWordRule.delete_flag == "1",
        )
        .first()
    )
    if exists:
        raise ValueError("该词条规则已存在")

    row = NicknameWordRule(
        group_id=group_id,
        word=word.strip(),
        normalized_word=normalized_word,
        match_type=match_type.strip(),
        decision=decision.strip(),
        status=status.strip(),
        priority=priority,
        risk_level=risk_level.strip(),
        source=source.strip(),
        note=note.strip() if note else None,
        delete_flag="1",
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def list_nickname_word_rules(
    db: Session,
    *,
    group_id: int | None = None,
    status: str | None = None,
    decision: str | None = None,
    keyword: str | None = None,
    limit: int = 50,
) -> tuple[list[NicknameWordRule], int]:
    """
    查询昵称词条规则列表。
    """
    safe_limit = max(1, min(limit, 200))
    query = db.query(NicknameWordRule).filter(NicknameWordRule.delete_flag == "1")

    if group_id is not None:
        query = query.filter(NicknameWordRule.group_id == group_id)
    if status:
        query = query.filter(NicknameWordRule.status == status)
    if decision:
        query = query.filter(NicknameWordRule.decision == decision)
    if keyword:
        normalized_keyword = keyword.strip()
        query = query.filter(
            (NicknameWordRule.word.ilike(f"%{normalized_keyword}%"))
            | (NicknameWordRule.normalized_word.ilike(f"%{normalize_nickname_for_check(normalized_keyword)}%"))
        )

    total = query.count()
    rows = (
        query.order_by(
            NicknameWordRule.priority.asc(),
            NicknameWordRule.id.asc(),
        )
        .limit(safe_limit)
        .all()
    )
    return rows, total


def create_nickname_contact_pattern(
    db: Session,
    *,
    pattern_name: str,
    pattern_type: str,
    pattern_regex: str,
    group_id: int | None = None,
    decision: str = "reject",
    status: str = "draft",
    priority: int = 100,
    risk_level: str = "high",
    normalized_hint: str | None = None,
    note: str | None = None,
) -> NicknameContactPattern:
    """
    创建联系方式规则。
    """
    if group_id is not None:
        group = (
            db.query(NicknameRuleGroup)
            .filter(
                NicknameRuleGroup.id == group_id,
                NicknameRuleGroup.delete_flag == "1",
            )
            .first()
        )
        if not group:
            raise ValueError("规则分组不存在")

    normalized_name = pattern_name.strip()
    if not normalized_name:
        raise ValueError("pattern_name 不能为空")

    if not pattern_regex.strip():
        raise ValueError("pattern_regex 不能为空")

    exists = (
        db.query(NicknameContactPattern)
        .filter(
            NicknameContactPattern.pattern_name == normalized_name,
            NicknameContactPattern.pattern_type == pattern_type.strip(),
            NicknameContactPattern.delete_flag == "1",
        )
        .first()
    )
    if exists:
        raise ValueError("该联系方式规则已存在")

    row = NicknameContactPattern(
        group_id=group_id,
        pattern_name=normalized_name,
        pattern_type=pattern_type.strip(),
        pattern_regex=pattern_regex.strip(),
        decision=decision.strip(),
        status=status.strip(),
        priority=priority,
        risk_level=risk_level.strip(),
        normalized_hint=normalized_hint.strip() if normalized_hint else None,
        note=note.strip() if note else None,
        delete_flag="1",
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def list_nickname_contact_patterns(
    db: Session,
    *,
    group_id: int | None = None,
    status: str | None = None,
    pattern_type: str | None = None,
    keyword: str | None = None,
    limit: int = 50,
) -> tuple[list[NicknameContactPattern], int]:
    """
    查询昵称联系方式规则列表。
    """
    safe_limit = max(1, min(limit, 200))
    query = db.query(NicknameContactPattern).filter(NicknameContactPattern.delete_flag == "1")

    if group_id is not None:
        query = query.filter(NicknameContactPattern.group_id == group_id)
    if status:
        query = query.filter(NicknameContactPattern.status == status)
    if pattern_type:
        query = query.filter(NicknameContactPattern.pattern_type == pattern_type)
    if keyword:
        normalized_keyword = keyword.strip()
        query = query.filter(
            (NicknameContactPattern.pattern_name.ilike(f"%{normalized_keyword}%"))
            | (NicknameContactPattern.pattern_regex.ilike(f"%{normalized_keyword}%"))
        )

    total = query.count()
    rows = (
        query.order_by(
            NicknameContactPattern.priority.asc(),
            NicknameContactPattern.id.asc(),
        )
        .limit(safe_limit)
        .all()
    )
    return rows, total


def update_nickname_rule_target_status(
    db: Session,
    *,
    target_type: str,
    target_id: int,
    status: str,
):
    """
    统一更新分组、词条、联系方式规则状态。
    """
    model_map = {
        "group": NicknameRuleGroup,
        "word": NicknameWordRule,
        "pattern": NicknameContactPattern,
    }
    model = model_map.get(target_type)
    if not model:
        raise ValueError("target_type 仅支持 group / word / pattern")

    row = (
        db.query(model)
        .filter(
            model.id == target_id,
            model.delete_flag == "1",
        )
        .first()
    )
    if not row:
        raise ValueError("目标规则不存在")

    row.status = status.strip()
    db.commit()
    db.refresh(row)
    return row


def list_nickname_audit_logs(
    db: Session,
    *,
    decision: str | None = None,
    scene: str | None = None,
    hit_group_code: str | None = None,
    limit: int = 50,
) -> tuple[list[NicknameAuditLog], int]:
    """
    查询昵称审核日志。
    """
    safe_limit = max(1, min(limit, 200))
    query = db.query(NicknameAuditLog).filter(NicknameAuditLog.delete_flag == "1")

    if decision:
        query = query.filter(NicknameAuditLog.decision == decision)
    if scene:
        query = query.filter(NicknameAuditLog.scene == scene)
    if hit_group_code:
        query = query.filter(NicknameAuditLog.hit_group_code == hit_group_code)

    total = query.count()
    rows = (
        query.order_by(
            NicknameAuditLog.create_time.desc(),
            NicknameAuditLog.id.desc(),
        )
        .limit(safe_limit)
        .all()
    )
    return rows, total


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

    说明：
    1. 日志表记录的是“最终判定结果”，不是中间过程。
    2. scene 用于区分“检查昵称”和“真正修改昵称”。
    3. rule_version_batch 会在写日志时一并带上，方便后续做误杀回放。
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
