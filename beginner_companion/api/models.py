"""Version-one wire models. Missing observations remain unknown."""
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, StrictInt


class WireModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class SkillLevels(WireModel):
    defence: int | None = Field(default=None, ge=1, le=99)


class QuestProgress(WireModel):
    dragon_slayer_i: Literal["not_started", "in_progress", "completed", "unknown"] = "unknown"


class Account(WireModel):
    skill_levels: SkillLevels = Field(default_factory=SkillLevels)
    quest_progress: QuestProgress = Field(default_factory=QuestProgress)


class EvaluationRequest(WireModel):
    protocol_version: StrictInt
    session_id: str = Field(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9_-]+$")
    revision: int = Field(ge=0, le=2147483647)
    goal_id: str = Field(min_length=1, max_length=128)
    account: Account


class Requirement(WireModel):
    id: str
    label: str
    status: Literal["satisfied", "missing", "unknown"]
    observed: int | str | None
    required: int | str


class EvaluationResponse(WireModel):
    protocol_version: Literal[1] = 1
    dataset_version: str = "bridge-demo-1"
    session_id: str
    revision: int
    goal_id: str
    scope: Literal["equipment_requirements_only"] = "equipment_requirements_only"
    requirements: list[Requirement]
    notes: list[str]


class ErrorDetail(WireModel):
    code: str
    message: str


class ErrorResponse(WireModel):
    protocol_version: Literal[1] = 1
    error: ErrorDetail
