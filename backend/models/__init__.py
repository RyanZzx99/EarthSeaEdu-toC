"""数据库模型模块。"""

# 导入所有模型，确保 Base.metadata.create_all 能识别这些表
from backend.models.auth_models import User
from backend.models.auth_models import UserAuthIdentity
from backend.models.auth_models import SmsCode
from backend.models.auth_models import WechatLoginState
from backend.models.auth_models import UserLoginLog
from backend.models.auth_models import InviteCode
from backend.models.ai_chat_models import AiPromptConfig
from backend.models.ai_chat_models import AiRuntimeConfig
from backend.models.ai_chat_models import AiChatSession
from backend.models.ai_chat_models import AiChatMessage
from backend.models.ai_chat_models import AiChatProfileResult
from backend.models.ai_chat_models import AiChatProfileDraft
from backend.models.ai_chat_models import AiProfileRadarFieldImpactRule
from backend.models.ai_chat_models import AiProfileRadarPendingChange
from backend.models.nickname_guard_models import NicknameRuleGroup
from backend.models.nickname_guard_models import NicknameWordRule
from backend.models.nickname_guard_models import NicknameContactPattern
from backend.models.nickname_guard_models import NicknameRulePublishLog
from backend.models.nickname_guard_models import NicknameAuditLog
from backend.models.nickname_guard_models import NicknameRuleOperationLog
