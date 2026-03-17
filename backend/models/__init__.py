"""数据库模型模块。"""

# 导入所有模型，确保 Base.metadata.create_all 能识别这些表
from backend.models.auth_models import User
from backend.models.auth_models import UserAuthIdentity
from backend.models.auth_models import SmsCode
from backend.models.auth_models import WechatLoginState
from backend.models.auth_models import UserLoginLog
from backend.models.auth_models import InviteCode
