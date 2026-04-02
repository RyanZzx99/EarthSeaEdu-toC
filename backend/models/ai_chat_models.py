"""
AI 六维建档相关 ORM 模型定义。

设计说明：
1. 这一组模型对应 AI 会话链路新增的 4 张表：
   - ai_prompt_configs
   - ai_chat_sessions
   - ai_chat_messages
   - ai_chat_profile_results
2. 这些模型的职责不是直接承载“学生正式档案”，而是承载：
   - Prompt 配置
   - AI 对话过程
   - AI 最终结构化结果
   - 结果入库前后的审计信息
3. student_id 统一直接复用 users.id，因此类型统一为 CHAR(36) 的 UUID 字符串。
4. 当前项目仍使用 Base.metadata.create_all 启动建表，因此需要把模型注册到 backend.models.__init__ 中。
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger
from sqlalchemy import CHAR
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


class AiPromptConfig(Base):
    """
    AI Prompt 配置表。

    作用：
    1. 统一存储不同业务阶段使用的 Prompt 文本。
    2. 支持版本管理、启停控制、模型参数配置。
    3. 后续服务端在真正调用模型前，可根据 prompt_key / prompt_stage 读取当前 active 版本。
    """

    __tablename__ = "ai_prompt_configs"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID；表内唯一标识",
    )
    prompt_key: Mapped[str] = mapped_column(
        String(150),
        nullable=False,
        comment="Prompt 唯一业务键；建议使用 业务域.阶段 的命名方式",
    )
    prompt_name: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
        comment="Prompt 展示名称；主要给后台管理页使用",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="业务域；当前主要是 student_profile_build",
    )
    prompt_role: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
        default="system",
        comment="Prompt 角色类型；常见值为 system / developer / user_template",
    )
    prompt_stage: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="Prompt 所处阶段；例如 conversation / extraction / scoring",
    )
    prompt_content: Mapped[str] = mapped_column(
        Text,
        nullable=False,
        comment="Prompt 正文内容",
    )
    prompt_version: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="Prompt 版本号",
    )
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="draft",
        comment="Prompt 状态；常见值为 draft / active / disabled / archived",
    )
    model_name: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="建议使用的模型名称；允许为空表示走系统默认模型",
    )
    temperature: Mapped[float | None] = mapped_column(
        nullable=True,
        comment="采样温度；控制输出稳定性与发散度",
    )
    top_p: Mapped[float | None] = mapped_column(
        nullable=True,
        comment="Top-p 采样参数",
    )
    max_tokens: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="单次调用最大输出 Token 限制",
    )
    output_format: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
        default="text",
        comment="期望输出格式；例如 text / json",
    )
    variables_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="Prompt 渲染所需的上下文字段清单",
    )
    remark: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="备注；用于记录版本说明、适用范围等",
    )
    created_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="创建人",
    )
    updated_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="最后更新人",
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
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记；1=有效，0=逻辑删除",
    )


class AiChatSession(Base):
    """
    AI 对话会话主表。

    作用：
    1. 记录某个学生一次完整 AI 建档会话的生命周期。
    2. 承载当前阶段、当前轮次、缺失维度等会话状态。
    3. 后续 WebSocket 连接初始化、恢复历史会话、结果归档都会依赖这张表。
    """

    __tablename__ = "ai_chat_sessions"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        comment="会话ID；前后端交互时使用的唯一会话标识",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="学生ID；直接复用 users.id",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="业务域；当前主要为 student_profile_build",
    )
    session_status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="active",
        comment="会话状态；例如 active / completed / cancelled / expired",
    )
    current_stage: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        default="conversation",
        comment="当前阶段；例如 conversation / extraction / scoring",
    )
    current_round: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="当前轮次；通常每次用户消息驱动一轮对话",
    )
    collected_slots_json: Mapped[dict | None] = mapped_column(
        JSON,
        nullable=True,
        comment="已采集字段槽位 JSON；用于会话进度管理",
    )
    missing_dimensions_json: Mapped[list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="缺失维度 JSON；用于驱动下一轮追问",
    )
    session_summary: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="当前会话摘要；用于长对话压缩上下文",
    )
    last_message_at: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="最后一条消息时间",
    )
    completed_at: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="会话完成时间",
    )
    expired_at: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="会话过期时间",
    )
    final_profile_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="最终建档结果ID；回填 ai_chat_profile_results.id",
    )
    remark: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="备注；用于人工标记或异常说明",
    )
    created_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="创建人",
    )
    updated_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="最后更新人",
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
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记；1=有效，0=逻辑删除",
    )

    messages: Mapped[list["AiChatMessage"]] = relationship(
        back_populates="session",
        lazy="select",
    )
    profile_result: Mapped["AiChatProfileResult | None"] = relationship(
        back_populates="session",
        lazy="select",
        uselist=False,
    )


class AiChatMessage(Base):
    """
    AI 对话消息表。

    作用：
    1. 保存用户消息、助手消息、系统消息。
    2. 用 sequence_no 保证会话内顺序稳定。
    3. content_json 预留给后续存 token 统计、事件元数据、结构化补充信息。
    """

    __tablename__ = "ai_chat_messages"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        ForeignKey("ai_chat_sessions.session_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="会话ID；关联 ai_chat_sessions.session_id",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="学生ID；直接复用 users.id",
    )
    message_role: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="消息角色；例如 user / assistant / system",
    )
    message_type: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
        default="visible_text",
        comment="消息类型；例如 visible_text / hidden_summary / internal_state",
    )
    sequence_no: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="会话内顺序号；保证消息顺序可恢复",
    )
    parent_message_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="父消息ID；用于标记某条回复关联的上一条消息",
    )
    content: Mapped[str] = mapped_column(
        Text,
        nullable=False,
        comment="消息正文",
    )
    content_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="结构化消息补充内容",
    )
    is_visible: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="前端是否可见；1=可见，0=隐藏",
    )
    stream_chunk_count: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="流式分片数量；后续接模型流式输出时使用",
    )
    token_count: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="Token 估算数；用于后续成本统计",
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
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记；1=有效，0=逻辑删除",
    )

    session: Mapped["AiChatSession"] = relationship(
        back_populates="messages",
        lazy="select",
    )


class AiChatProfileResult(Base):
    """
    AI 建档结果表。

    作用：
    1. 存最终结构化档案 JSON。
    2. 存六维图分数和中文总结。
    3. 存服务端转换后的 db_payload_json 与入库审计信息。
    """

    __tablename__ = "ai_chat_profile_results"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        ForeignKey("ai_chat_sessions.session_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="会话ID；一份结果对应一次完整建档会话",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="学生ID；直接复用 users.id",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="业务域；当前主要为 student_profile_build",
    )
    result_status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="generated",
        comment="结果状态；例如 generated / saved / failed",
    )
    profile_json: Mapped[dict] = mapped_column(
        JSON,
        nullable=False,
        comment="最终结构化建档 JSON",
    )
    radar_scores_json: Mapped[dict | None] = mapped_column(
        JSON,
        nullable=True,
        comment="六维图分数 JSON",
    )
    summary_text: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="给前端展示的中文总结",
    )
    db_payload_json: Mapped[dict | None] = mapped_column(
        JSON,
        nullable=True,
        comment="服务端转换后的标准入库 payload",
    )
    insert_sql_text: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="最终生成的 Insert SQL 文本；仅供后台审计",
    )
    save_error_message: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="入库失败原因",
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
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记；1=有效，0=逻辑删除",
    )

    session: Mapped["AiChatSession"] = relationship(
        back_populates="profile_result",
        lazy="select",
    )


class AiChatProfileDraft(Base):
    """
    【草稿建档实验】AI 对话阶段的结构化草稿表。

    作用：
    1. 承载每个 session 的最新 draft_json。
    2. 记录最近一次 patch，便于调试实验链路。
    3. 与正式业务表分离，避免污染当前正式档案流程。
    """

    __tablename__ = "ai_chat_profile_drafts"

    draft_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="草稿主键ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        ForeignKey("ai_chat_sessions.session_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        unique=True,
        comment="会话ID；一条会话只保留一份最新草稿",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="学生ID",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="业务域",
    )
    draft_json: Mapped[dict] = mapped_column(
        JSON,
        nullable=False,
        comment="【草稿建档实验】当前最新结构化草稿",
    )
    last_patch_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="【草稿建档实验】最近一次增量 patch",
    )
    source_round: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="草稿更新到的会话轮次",
    )
    version_no: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="草稿版本号；每次更新递增",
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
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记；1=有效，0=已删除",
    )
class AiProfileRadarFieldImpactRule(Base):
    """
    【六维图差量重算】正式档案字段与六维维度影响规则表。

    作用：
    1. 把“哪个表的哪个字段会影响哪些维度”从代码中抽离到数据库。
    2. 档案页保存与 AI 对话 patch 都共用这张表计算 affected_dimensions。
    3. 支持精确字段匹配和 `*` 整表通配。
    """

    __tablename__ = "ai_profile_radar_field_impact_rules"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        comment="业务域；例如 student_profile_build",
    )
    table_name: Mapped[str] = mapped_column(
        String(128),
        nullable=False,
        comment="正式档案表名",
    )
    field_name: Mapped[str] = mapped_column(
        String(128),
        nullable=False,
        comment="字段名；支持 * 作为整表通配",
    )
    affects_radar: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="是否影响六维图；1=影响 0=不影响",
    )
    affected_dimensions_json: Mapped[list | dict] = mapped_column(
        JSON,
        nullable=False,
        comment="受影响维度数组",
    )
    remark: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="规则说明",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=100,
        comment="排序号；值越小优先级越高",
    )
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="active",
        comment="状态；active / disabled",
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
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记；1=有效 0=删除",
    )


class AiProfileRadarPendingChange(Base):
    """
    【六维图差量重算】待处理字段改动与受影响维度状态表。

    作用：
    1. 只记录“自上一版六维图生成以后”待处理的字段改动。
    2. 只在用户已经有旧六维图结果时启用。
    3. 供首页生成 / 档案页更新六维图时判断走 full 还是 partial。
    """

    __tablename__ = "ai_profile_radar_pending_changes"

    pending_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        ForeignKey("ai_chat_sessions.session_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="会话ID",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="学生ID",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="业务域",
    )
    last_profile_result_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("ai_chat_profile_results.id", ondelete="SET NULL", onupdate="CASCADE"),
        nullable=True,
        comment="上一版六维图结果ID；作为差量重算基线",
    )
    pending_changed_fields_json: Mapped[list | None] = mapped_column(
        JSON,
        nullable=False,
        default=list,
        comment="待处理字段路径列表",
    )
    pending_affected_dimensions_json: Mapped[list | None] = mapped_column(
        JSON,
        nullable=False,
        default=list,
        comment="待处理受影响维度列表",
    )
    last_change_source: Mapped[str | None] = mapped_column(
        String(50),
        nullable=True,
        comment="最近一次改动来源；例如 archive_form / ai_dialogue_patch",
    )
    last_change_remark: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="最近一次改动备注",
    )
    version_no: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="版本号；每次累计改动递增",
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
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记；1=有效 0=删除",
    )
