from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel
from pydantic import Field


class TeacherStudentSummary(BaseModel):
    user_id: str = Field(..., description="学生用户ID")
    mobile: str | None = Field(default=None, description="学生手机号")
    nickname: str | None = Field(default=None, description="学生昵称")
    status: str = Field(..., description="学生账号状态")


class TeacherStudentArchiveLookupResponse(BaseModel):
    student: TeacherStudentSummary = Field(..., description="学生基本信息")
    session_id: str | None = Field(default=None, description="最近一次六维图结果对应会话ID")
    archive_form: dict[str, Any] = Field(default_factory=dict, description="正式档案快照")
    form_meta: dict[str, Any] = Field(default_factory=dict, description="正式档案表单元数据")
    result_status: str | None = Field(default=None, description="最近一次六维图结果状态")
    summary_text: str | None = Field(default=None, description="最近一次六维图综合总结")
    radar_scores_json: dict[str, Any] = Field(default_factory=dict, description="最近一次六维图分数")
    save_error_message: str | None = Field(default=None, description="最近一次保存异常信息")
    create_time: datetime | None = Field(default=None, description="最近一次结果创建时间")
    update_time: datetime | None = Field(default=None, description="最近一次结果更新时间")
