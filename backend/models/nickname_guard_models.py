"""
昵称风控相关 ORM 模型定义。

说明：
1. 与认证模型拆分，避免 auth_models.py 持续膨胀。
2. 字段命名遵循当前项目约定：create_time / update_time / delete_flag。
3. 这些模型对应长期运营版昵称规则、发布、审计、操作日志表。
"""

from datetime import datetime

from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Integer
from sqlalchemy import JSON
from sqlalchemy import String
from sqlalchemy import Text
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column
from sqlalchemy.orm import relationship

from backend.config.db_conf import Base


class NicknameRuleGroup(Base):
    """
    昵称规则分组表。

    设计意图：
    1. 把规则按业务主题分桶，例如“官方冒充”“微信导流”“教育白名单”。
    2. 运营侧优先管理分组，再管理分组下的词条和正则规则。
    """

    __tablename__ = "nickname_rule_groups"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    group_code: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="分组编码",
    )
    group_name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="分组名称",
    )
    group_type: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="分组类型",
    )
    scope: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="nickname",
        comment="作用范围",
    )
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="draft",
        comment="状态",
    )
    priority: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=100,
        comment="优先级",
    )
    description: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="说明",
    )
    effective_start_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="生效开始时间",
    )
    effective_end_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="生效结束时间",
    )
    version_no: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="版本号",
    )
    created_by: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="创建人",
    )
    updated_by: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="更新人",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标记",
    )

    # 中文注释：一个规则分组下可以挂多条词条规则。
    word_rules: Mapped[list["NicknameWordRule"]] = relationship(
        back_populates="group",
        lazy="select",
    )

    # 中文注释：同一个分组下也可以挂联系方式正则规则，便于统一运营管理。
    contact_patterns: Mapped[list["NicknameContactPattern"]] = relationship(
        back_populates="group",
        lazy="select",
    )


class NicknameWordRule(Base):
    """
    昵称词条规则表。

    设计意图：
    1. 只保存“标准词”规则，变体尽量通过代码归一化吸收。
    2. 通过 decision 区分 pass / reject / review，便于后续升级成人审流。
    """

    __tablename__ = "nickname_word_rules"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    group_id: Mapped[int] = mapped_column(
        Integer,
        ForeignKey("nickname_rule_groups.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        comment="规则分组ID",
    )
    word: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="原始词条",
    )
    normalized_word: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="标准化词条",
    )
    match_type: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="contains",
        comment="匹配方式",
    )
    decision: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="命中决策",
    )
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="draft",
        comment="状态",
    )
    priority: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=100,
        comment="优先级",
    )
    risk_level: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="medium",
        comment="风险等级",
    )
    source: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="manual",
        comment="来源",
    )
    note: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="备注",
    )
    effective_start_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="生效开始时间",
    )
    effective_end_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="生效结束时间",
    )
    version_no: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="版本号",
    )
    created_by: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="创建人",
    )
    updated_by: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="更新人",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标记",
    )

    # 中文注释：词条规则归属于一个明确的分组。
    group: Mapped["NicknameRuleGroup"] = relationship(
        back_populates="word_rules",
        lazy="select",
    )


class NicknameContactPattern(Base):
    """
    昵称联系方式规则表。

    设计意图：
    1. 把手机号、微信号、QQ 号、邮箱等正则规则独立出来。
    2. 避免在词条规则表里混入 regex，降低运营维护成本。
    """

    __tablename__ = "nickname_contact_patterns"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    group_id: Mapped[int | None] = mapped_column(
        Integer,
        ForeignKey("nickname_rule_groups.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=True,
        comment="规则分组ID",
    )
    pattern_name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="规则名称",
    )
    pattern_type: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="规则类型",
    )
    pattern_regex: Mapped[str] = mapped_column(
        String(500),
        nullable=False,
        comment="正则表达式",
    )
    decision: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="reject",
        comment="命中决策",
    )
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="draft",
        comment="状态",
    )
    priority: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=100,
        comment="优先级",
    )
    risk_level: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="high",
        comment="风险等级",
    )
    normalized_hint: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="归一化说明",
    )
    note: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="备注",
    )
    effective_start_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="生效开始时间",
    )
    effective_end_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="生效结束时间",
    )
    version_no: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="版本号",
    )
    created_by: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="创建人",
    )
    updated_by: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="更新人",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标记",
    )

    # 中文注释：联系方式规则通常也会挂在某个 contact 分组下，个别场景允许为空。
    group: Mapped[NicknameRuleGroup | None] = relationship(
        back_populates="contact_patterns",
        lazy="select",
    )


class NicknameRulePublishLog(Base):
    """
    昵称规则发布日志表。

    作用：
    1. 记录每次规则发布批次。
    2. 为误杀排查、回滚、版本对比提供依据。
    """

    __tablename__ = "nickname_rule_publish_logs"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    publish_batch_no: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        comment="发布批次号",
    )
    scope: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="nickname",
        comment="作用范围",
    )
    change_summary: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="变更摘要",
    )
    published_by: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="发布人",
    )
    published_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="发布时间",
    )
    snapshot_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="发布快照",
    )
    rollback_batch_no: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="回滚目标批次",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标记",
    )


class NicknameAuditLog(Base):
    """
    昵称审核日志表。

    作用：
    1. 记录昵称检查和修改时的最终判定结果。
    2. 运营侧可以基于这张表做误杀分析、规则命中统计和追溯。
    """

    __tablename__ = "nickname_audit_logs"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    trace_id: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="链路追踪ID",
    )
    user_id: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="用户ID",
    )
    scene: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="场景",
    )
    raw_nickname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="原始昵称",
    )
    normalized_nickname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="标准化昵称",
    )
    decision: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="结果",
    )
    hit_source: Mapped[str | None] = mapped_column(
        String(20),
        nullable=True,
        comment="命中来源",
    )
    hit_rule_id: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="命中的词条规则ID",
    )
    hit_pattern_id: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="命中的联系方式规则ID",
    )
    hit_group_code: Mapped[str | None] = mapped_column(
        String(50),
        nullable=True,
        comment="命中的规则组编码",
    )
    hit_content: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="命中的词或片段",
    )
    message: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="提示文案",
    )
    client_ip: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="客户端IP",
    )
    user_agent: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="UA",
    )
    app_version: Mapped[str | None] = mapped_column(
        String(50),
        nullable=True,
        comment="应用版本",
    )
    rule_version_batch: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="命中时使用的规则批次",
    )
    extra_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="扩展上下文",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标记",
    )


class NicknameRuleOperationLog(Base):
    """
    昵称规则操作日志表。

    作用：
    1. 记录后台运营对规则的创建、修改、启停、发布、回滚动作。
    2. 便于做审计和责任追踪。
    """

    __tablename__ = "nickname_rule_operation_logs"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    target_type: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="对象类型",
    )
    target_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="对象ID",
    )
    operation_type: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="操作类型",
    )
    before_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="变更前",
    )
    after_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="变更后",
    )
    operator_id: Mapped[str | None] = mapped_column(
        String(36),
        nullable=True,
        comment="操作人",
    )
    operator_name: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="操作人名称",
    )
    remark: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="备注",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标记",
    )
