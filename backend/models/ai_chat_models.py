"""
AI 鍏淮寤烘。鐩稿叧 ORM 妯″瀷瀹氫箟銆?

璁捐璇存槑锛?
1. 杩欎竴缁勬ā鍨嬪搴?AI 浼氳瘽閾捐矾鏂板鐨?4 寮犺〃锛?
   - ai_prompt_configs
   - ai_chat_sessions
   - ai_chat_messages
   - ai_chat_profile_results
2. 杩欎簺妯″瀷鐨勮亴璐ｄ笉鏄洿鎺ユ壙杞解€滃鐢熸寮忔。妗堚€濓紝鑰屾槸鎵胯浇锛?
   - Prompt 閰嶇疆
   - AI 瀵硅瘽杩囩▼
   - AI 鏈€缁堢粨鏋勫寲缁撴灉
   - 缁撴灉鍏ュ簱鍓嶅悗鐨勫璁′俊鎭?
3. student_id 缁熶竴鐩存帴澶嶇敤 users.id锛屽洜姝ょ被鍨嬬粺涓€涓?CHAR(36) 鐨?UUID 瀛楃涓层€?
4. 褰撳墠椤圭洰浠嶄娇鐢?Base.metadata.create_all 鍚姩寤鸿〃锛屽洜姝ら渶瑕佹妸妯″瀷娉ㄥ唽鍒?backend.models.__init__ 涓€?
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
    AI Prompt 閰嶇疆琛ㄣ€?

    浣滅敤锛?
    1. 缁熶竴瀛樺偍涓嶅悓涓氬姟闃舵浣跨敤鐨?Prompt 鏂囨湰銆?
    2. 鏀寔鐗堟湰绠＄悊銆佸惎鍋滄帶鍒躲€佹ā鍨嬪弬鏁伴厤缃€?
    3. 鍚庣画鏈嶅姟绔湪鐪熸璋冪敤妯″瀷鍓嶏紝鍙牴鎹?prompt_key / prompt_stage 璇诲彇褰撳墠 active 鐗堟湰銆?
    """

    __tablename__ = "ai_prompt_configs"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="涓婚敭ID锛涜〃鍐呭敮涓€鏍囪瘑",
    )
    prompt_key: Mapped[str] = mapped_column(
        String(150),
        nullable=False,
        comment="Prompt 唯一业务键",
    )
    prompt_name: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
        comment="Prompt 显示名称",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="涓氬姟鍩燂紱褰撳墠涓昏鏄?student_profile_build",
    )
    prompt_role: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
        default="system",
        comment="Prompt 瑙掕壊绫诲瀷锛涘父瑙佸€间负 system / developer / user_template",
    )
    prompt_stage: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="Prompt 鎵€澶勯樁娈碉紱渚嬪 conversation / extraction / scoring",
    )
    prompt_content: Mapped[str] = mapped_column(
        Text,
        nullable=False,
        comment="Prompt 姝ｆ枃鍐呭",
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
        comment="Prompt 鐘舵€侊紱甯歌鍊间负 draft / active / disabled / archived",
    )
    model_name: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="建议使用的模型名称，可为空",
    )
    temperature: Mapped[float | None] = mapped_column(
        nullable=True,
        comment="采样温度",
    )
    top_p: Mapped[float | None] = mapped_column(
        nullable=True,
        comment="Top-p 閲囨牱鍙傛暟",
    )
    max_tokens: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="鍗曟璋冪敤鏈€澶ц緭鍑?Token 闄愬埗",
    )
    output_format: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
        default="text",
        comment="鏈熸湜杈撳嚭鏍煎紡锛涗緥濡?text / json",
    )
    variables_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="Prompt 娓叉煋鎵€闇€鐨勪笂涓嬫枃瀛楁娓呭崟",
    )
    remark: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="备注",
    )
    created_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="创建人",
    )
    updated_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="鏈€鍚庢洿鏂颁汉",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="鍒涘缓鏃堕棿",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="鏇存柊鏃堕棿",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="閫昏緫鍒犻櫎鏍囪锛?=鏈夋晥锛?=閫昏緫鍒犻櫎",
    )


class AiRuntimeConfig(Base):
    """
    AI 鏉╂劘顢戦弮鍫曞帳缂冾喛銆冮妴?

    娴ｆ粎鏁ら敍?
    1. 閻劋绨粻锛勬倞閸涙ê婀崥搴″酱缂佸瓨濮?AI_MODEL_* 鏉╂劘顢戦弮鍫曞帳缂冾喓鈧?
    2. 鏉╂劘顢戦弮鏈电喘閸忓牐顕伴弫鐗堝祦鎼存捁顩惄鏍р偓纭风礉娑撹櫣鈹栭弮璺烘礀闁偓 backend/.env 姒涙顓婚崐绗衡偓?
    3. API Key 缁涘鏅遍幇鐔封偓鑲╂暠 is_secret 閺嶅洩顔囬敍灞芥倵閸欐澘鐫嶇粈鐑樻閸欘亣绻戦崶鐐村负閻礁鈧鈧?
    """

    __tablename__ = "ai_runtime_configs"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="娑撳鏁璉D",
    )
    config_group: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        comment="闁板秶鐤嗛崚鍡欑矋閿涙稐绶ユ俊?ai_model",
    )
    config_key: Mapped[str] = mapped_column(
        String(128),
        nullable=False,
        comment="配置键，与环境变量同名",
    )
    config_name: Mapped[str] = mapped_column(
        String(150),
        nullable=False,
        comment="闁板秶鐤嗙仦鏇犮仛閸氬秶袨",
    )
    config_value: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="数据库覆盖值；为空时回退到 .env 默认值",
    )
    value_type: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
        default="string",
        comment="閸婅偐琚崹瀣剁幢string/int/float/url/secret/model_name",
    )
    is_secret: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="是否敏感值，1=敏感",
    )
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="active",
        comment="閻樿埖鈧緤绱盿ctive / disabled",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=100,
        comment="排序值",
    )
    remark: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="备注",
    )
    created_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="创建人",
    )
    updated_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="閺堚偓閸氬孩娲块弬棰佹眽",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="閸掓稑缂撻弮鍫曟？",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="閺囧瓨鏌婇弮鍫曟？",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="闁槒绶崚鐘绘珟閺嶅洩顔囬敍?=閺堝鏅ラ敍?=闁槒绶崚鐘绘珟",
    )


class AiChatSession(Base):
    """
    AI 瀵硅瘽浼氳瘽涓昏〃銆?

    浣滅敤锛?
    1. 璁板綍鏌愪釜瀛︾敓涓€娆″畬鏁?AI 寤烘。浼氳瘽鐨勭敓鍛藉懆鏈熴€?
    2. 鎵胯浇褰撳墠闃舵銆佸綋鍓嶈疆娆°€佺己澶辩淮搴︾瓑浼氳瘽鐘舵€併€?
    3. 鍚庣画 WebSocket 杩炴帴鍒濆鍖栥€佹仮澶嶅巻鍙蹭細璇濄€佺粨鏋滃綊妗ｉ兘浼氫緷璧栬繖寮犺〃銆?
    """

    __tablename__ = "ai_chat_sessions"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="涓婚敭ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        comment="浼氳瘽ID锛涘墠鍚庣浜や簰鏃朵娇鐢ㄧ殑鍞竴浼氳瘽鏍囪瘑",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="瀛︾敓ID锛涚洿鎺ュ鐢?users.id",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="涓氬姟鍩燂紱褰撳墠涓昏涓?student_profile_build",
    )
    session_status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="active",
        comment="浼氳瘽鐘舵€侊紱渚嬪 active / completed / cancelled / expired",
    )
    current_stage: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        default="conversation",
        comment="褰撳墠闃舵锛涗緥濡?conversation / extraction / scoring",
    )
    current_round: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="当前轮次",
    )
    collected_slots_json: Mapped[dict | None] = mapped_column(
        JSON,
        nullable=True,
        comment="已采集槽位 JSON",
    )
    missing_dimensions_json: Mapped[list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="缺失维度 JSON",
    )
    session_summary: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="当前会话摘要",
    )
    last_message_at: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="最后一条消息时间",
    )
    completed_at: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="浼氳瘽瀹屾垚鏃堕棿",
    )
    expired_at: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="浼氳瘽杩囨湡鏃堕棿",
    )
    final_profile_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="鏈€缁堝缓妗ｇ粨鏋淚D锛涘洖濉?ai_chat_profile_results.id",
    )
    remark: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="澶囨敞锛涚敤浜庝汉宸ユ爣璁版垨寮傚父璇存槑",
    )
    created_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="创建人",
    )
    updated_by: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="鏈€鍚庢洿鏂颁汉",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="鍒涘缓鏃堕棿",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="鏇存柊鏃堕棿",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="閫昏緫鍒犻櫎鏍囪锛?=鏈夋晥锛?=閫昏緫鍒犻櫎",
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
    AI 瀵硅瘽娑堟伅琛ㄣ€?

    浣滅敤锛?
    1. 淇濆瓨鐢ㄦ埛娑堟伅銆佸姪鎵嬫秷鎭€佺郴缁熸秷鎭€?
    2. 鐢?sequence_no 淇濊瘉浼氳瘽鍐呴『搴忕ǔ瀹氥€?
    3. content_json 棰勭暀缁欏悗缁瓨 token 缁熻銆佷簨浠跺厓鏁版嵁銆佺粨鏋勫寲琛ュ厖淇℃伅銆?
    """

    __tablename__ = "ai_chat_messages"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="涓婚敭ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        ForeignKey("ai_chat_sessions.session_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="浼氳瘽ID锛涘叧鑱?ai_chat_sessions.session_id",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="瀛︾敓ID锛涚洿鎺ュ鐢?users.id",
    )
    message_role: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="娑堟伅瑙掕壊锛涗緥濡?user / assistant / system",
    )
    message_type: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
        default="visible_text",
        comment="娑堟伅绫诲瀷锛涗緥濡?visible_text / hidden_summary / internal_state",
    )
    sequence_no: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="浼氳瘽鍐呴『搴忓彿锛涗繚璇佹秷鎭『搴忓彲鎭㈠",
    )
    parent_message_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="父消息ID",
    )
    content: Mapped[str] = mapped_column(
        Text,
        nullable=False,
        comment="娑堟伅姝ｆ枃",
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
        comment="鍓嶇鏄惁鍙锛?=鍙锛?=闅愯棌",
    )
    stream_chunk_count: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="流式分片数量",
    )
    token_count: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="Token 浼扮畻鏁帮紱鐢ㄤ簬鍚庣画鎴愭湰缁熻",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="鍒涘缓鏃堕棿",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="鏇存柊鏃堕棿",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="閫昏緫鍒犻櫎鏍囪锛?=鏈夋晥锛?=閫昏緫鍒犻櫎",
    )

    session: Mapped["AiChatSession"] = relationship(
        back_populates="messages",
        lazy="select",
    )


class AiChatProfileResult(Base):
    """
    AI 寤烘。缁撴灉琛ㄣ€?

    浣滅敤锛?
    1. 瀛樻渶缁堢粨鏋勫寲妗ｆ JSON銆?
    2. 瀛樺叚缁村浘鍒嗘暟鍜屼腑鏂囨€荤粨銆?
    3. 瀛樻湇鍔＄杞崲鍚庣殑 db_payload_json 涓庡叆搴撳璁′俊鎭€?
    """

    __tablename__ = "ai_chat_profile_results"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="涓婚敭ID",
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
        comment="瀛︾敓ID锛涚洿鎺ュ鐢?users.id",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="涓氬姟鍩燂紱褰撳墠涓昏涓?student_profile_build",
    )
    result_status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="generated",
        comment="缁撴灉鐘舵€侊紱渚嬪 generated / saved / failed",
    )
    profile_json: Mapped[dict] = mapped_column(
        JSON,
        nullable=False,
        comment="鏈€缁堢粨鏋勫寲寤烘。 JSON",
    )
    radar_scores_json: Mapped[dict | None] = mapped_column(
        JSON,
        nullable=True,
        comment="鍏淮鍥惧垎鏁?JSON",
    )
    summary_text: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="缁欏墠绔睍绀虹殑涓枃鎬荤粨",
    )
    db_payload_json: Mapped[dict | None] = mapped_column(
        JSON,
        nullable=True,
        comment="鏈嶅姟绔浆鎹㈠悗鐨勬爣鍑嗗叆搴?payload",
    )
    insert_sql_text: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="最终生成的 SQL 文本",
    )
    save_error_message: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="鍏ュ簱澶辫触鍘熷洜",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="鍒涘缓鏃堕棿",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="鏇存柊鏃堕棿",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="閫昏緫鍒犻櫎鏍囪锛?=鏈夋晥锛?=閫昏緫鍒犻櫎",
    )

    session: Mapped["AiChatSession"] = relationship(
        back_populates="profile_result",
        lazy="select",
    )


class AiChatProfileDraft(Base):
    """
    鏅鸿兘寤烘。 AI 瀵硅瘽闃舵鐨勭粨鏋勫寲 draft 琛ㄣ€?
    浣滅敤锛?
    1. 鎵胯浇姣忎釜 session 鐨勬渶鏂?draft_json銆?
    2. 璁板綍鏈€杩戜竴娆?patch锛屼究浜庤皟璇曞綋鍓?draft 閾捐矾銆?    3. 涓庢寮忎笟鍔¤〃鍒嗙锛岄伩鍏嶆薄鏌撳綋鍓嶆寮忔。妗堟祦绋嬨€?
    """

    __tablename__ = "ai_chat_profile_drafts"

    draft_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="鑽夌涓婚敭ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        ForeignKey("ai_chat_sessions.session_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        unique=True,
        comment="会话ID",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="瀛︾敓ID",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="业务域",
    )
    draft_json: Mapped[dict] = mapped_column(
        JSON,
        nullable=False,
        comment="鏅鸿兘寤烘。褰撳墠鏈€鏂扮粨鏋勫寲 draft",
    )
    last_patch_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="鏅鸿兘寤烘。鏈€杩戜竴娆″閲?patch",
    )
    source_round: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="鑽夌鏇存柊鍒扮殑浼氳瘽杞",
    )
    version_no: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="鑽夌鐗堟湰鍙凤紱姣忔鏇存柊閫掑",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="鍒涘缓鏃堕棿",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="鏇存柊鏃堕棿",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记",
    )
class AiProfileRadarFieldImpactRule(Base):
    """
    銆愬叚缁村浘宸噺閲嶇畻銆戞寮忔。妗堝瓧娈典笌鍏淮缁村害褰卞搷瑙勫垯琛ㄣ€?

    浣滅敤锛?
    1. 鎶娾€滃摢涓〃鐨勫摢涓瓧娈典細褰卞搷鍝簺缁村害鈥濅粠浠ｇ爜涓娊绂诲埌鏁版嵁搴撱€?
    2. 妗ｆ椤典繚瀛樹笌 AI 瀵硅瘽 patch 閮藉叡鐢ㄨ繖寮犺〃璁＄畻 affected_dimensions銆?
    3. 鏀寔绮剧‘瀛楁鍖归厤鍜?`*` 鏁磋〃閫氶厤銆?
    """

    __tablename__ = "ai_profile_radar_field_impact_rules"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="涓婚敭ID",
    )
    biz_domain: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        comment="涓氬姟鍩燂紱渚嬪 student_profile_build",
    )
    table_name: Mapped[str] = mapped_column(
        String(128),
        nullable=False,
        comment="姝ｅ紡妗ｆ琛ㄥ悕",
    )
    field_name: Mapped[str] = mapped_column(
        String(128),
        nullable=False,
        comment="瀛楁鍚嶏紱鏀寔 * 浣滀负鏁磋〃閫氶厤",
    )
    affects_radar: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="是否影响六维图",
    )
    affected_dimensions_json: Mapped[list | dict] = mapped_column(
        JSON,
        nullable=False,
        comment="受影响维度数组",
    )
    remark: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="瑙勫垯璇存槑",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=100,
        comment="鎺掑簭鍙凤紱鍊艰秺灏忎紭鍏堢骇瓒婇珮",
    )
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="active",
        comment="鐘舵€侊紱active / disabled",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="鍒涘缓鏃堕棿",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="鏇存柊鏃堕棿",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="閫昏緫鍒犻櫎鏍囪锛?=鏈夋晥 0=鍒犻櫎",
    )


class AiProfileRadarPendingChange(Base):
    """
    銆愬叚缁村浘宸噺閲嶇畻銆戝緟澶勭悊瀛楁鏀瑰姩涓庡彈褰卞搷缁村害鐘舵€佽〃銆?

    浣滅敤锛?
    1. 鍙褰曗€滆嚜涓婁竴鐗堝叚缁村浘鐢熸垚浠ュ悗鈥濆緟澶勭悊鐨勫瓧娈垫敼鍔ㄣ€?
    2. 鍙湪鐢ㄦ埛宸茬粡鏈夋棫鍏淮鍥剧粨鏋滄椂鍚敤銆?
    3. 渚涢椤电敓鎴?/ 妗ｆ椤垫洿鏂板叚缁村浘鏃跺垽鏂蛋 full 杩樻槸 partial銆?
    """

    __tablename__ = "ai_profile_radar_pending_changes"

    pending_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="涓婚敭ID",
    )
    session_id: Mapped[str] = mapped_column(
        String(64),
        ForeignKey("ai_chat_sessions.session_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="浼氳瘽ID",
    )
    student_id: Mapped[str] = mapped_column(
        CHAR(36),
        ForeignKey("users.id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="瀛︾敓ID",
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
        comment="上一版六维图结果ID",
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
        comment="寰呭鐞嗗彈褰卞搷缁村害鍒楄〃",
    )
    last_change_source: Mapped[str | None] = mapped_column(
        String(50),
        nullable=True,
        comment="鏈€杩戜竴娆℃敼鍔ㄦ潵婧愶紱渚嬪 archive_form / ai_dialogue_patch",
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
        comment="鐗堟湰鍙凤紱姣忔绱鏀瑰姩閫掑",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="鍒涘缓鏃堕棿",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="鏇存柊鏃堕棿",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="閫昏緫鍒犻櫎鏍囪锛?=鏈夋晥 0=鍒犻櫎",
    )
