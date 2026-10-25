"""Goal-browser contract. Kept separate from the original bridge demo contract."""
from typing import Literal, Annotated
from pydantic import Field, StrictInt, field_validator
from .models import WireModel, EvaluationRequest

QuestState = Literal['not_started', 'in_progress', 'completed', 'unknown']
TaskId = Literal['essence_mine', 'cake', 'silk', 'altar', 'trawler', 'training_camp',
                 'rusty_sword', 'wilderness_lever', 'hunter_shop', 'pet_check']


SkillId = Literal['attack', 'defence', 'strength', 'hitpoints', 'ranged', 'prayer', 'magic', 'cooking', 'woodcutting', 'fletching', 'fishing', 'firemaking', 'crafting', 'smithing', 'mining', 'herblore', 'agility', 'thieving', 'slayer', 'farming', 'runecraft', 'hunter', 'construction', 'sailing']


class GoalSkills(WireModel):
    attack: int | None = Field(default=None, ge=1, le=99)
    defence: int | None = Field(default=None, ge=1, le=99)
    strength: int | None = Field(default=None, ge=1, le=99)
    hitpoints: int | None = Field(default=None, ge=1, le=99)
    ranged: int | None = Field(default=None, ge=1, le=99)
    prayer: int | None = Field(default=None, ge=1, le=99)
    magic: int | None = Field(default=None, ge=1, le=99)
    cooking: int | None = Field(default=None, ge=1, le=99)
    woodcutting: int | None = Field(default=None, ge=1, le=99)
    fletching: int | None = Field(default=None, ge=1, le=99)
    fishing: int | None = Field(default=None, ge=1, le=99)
    firemaking: int | None = Field(default=None, ge=1, le=99)
    crafting: int | None = Field(default=None, ge=1, le=99)
    smithing: int | None = Field(default=None, ge=1, le=99)
    mining: int | None = Field(default=None, ge=1, le=99)
    herblore: int | None = Field(default=None, ge=1, le=99)
    agility: int | None = Field(default=None, ge=1, le=99)
    thieving: int | None = Field(default=None, ge=1, le=99)
    slayer: int | None = Field(default=None, ge=1, le=99)
    farming: int | None = Field(default=None, ge=1, le=99)
    runecraft: int | None = Field(default=None, ge=1, le=99)
    hunter: int | None = Field(default=None, ge=1, le=99)
    construction: int | None = Field(default=None, ge=1, le=99)
    sailing: int | None = Field(default=None, ge=1, le=99)


class GoalQuests(WireModel):
    dragon_slayer_i: QuestState = 'unknown'
    prince_ali_rescue: QuestState = 'unknown'
    plague_city: QuestState = 'unknown'
    biohazard: QuestState = 'unknown'
    rune_mysteries: QuestState = 'unknown'
    cooks_assistant: QuestState = 'unknown'
    the_restless_ghost: QuestState = 'unknown'
    sheep_shearer: QuestState = 'unknown'
    dorics_quest: QuestState = 'unknown'
    tree_gnome_village: QuestState = 'unknown'
    the_grand_tree: QuestState = 'unknown'


class GoalFlags(WireModel):
    rune_platebody_equipped: bool | None = None
    ardougne_cloak_1_claimed: bool | None = None
    chronicle_obtained: bool | None = None


class GoalAccount(WireModel):
    skill_xp: dict[SkillId, Annotated[StrictInt, Field(ge=0, le=200000000)]] = Field(default_factory=dict)
    skill_levels: GoalSkills = Field(default_factory=GoalSkills)
    quest_progress: GoalQuests = Field(default_factory=GoalQuests)
    members: bool | None = None
    quest_points: int | None = Field(default=None, ge=0)
    ardougne_easy_tasks: dict[TaskId, bool | None] = Field(default_factory=dict)
    milestones: GoalFlags = Field(default_factory=GoalFlags)


class Preferences(WireModel):
    interests: list[Literal['combat', 'quests', 'exploration', 'utility']] = Field(default_factory=list, max_length=4)
    commitment: Literal['any', 'short', 'few_sessions', 'long'] = 'any'

    @field_validator('interests')
    @classmethod
    def unique_interests(cls, interests):
        if len(interests) != len(set(interests)):
            raise ValueError('Choose each interest once')
        return interests


class CompletionEvent(WireModel):
    goal_id: str = Field(min_length=1, max_length=128, pattern=r'^[a-z0-9_]+$')
    completed_at: StrictInt = Field(ge=0, le=253402300799)


class GoalFeedback(WireModel):
    kind: Literal['not_interested', 'maybe_later', 'too_much_work']
    recorded_at: StrictInt = Field(ge=0, le=253402300799)


class GoalsRequest(EvaluationRequest):
    # No selected goal needed: one snapshot evaluates the entire small catalogue.
    goal_id: Literal['all'] = 'all'
    account: GoalAccount
    preferences: Preferences = Field(default_factory=Preferences)
    source: Literal['demo', 'live'] = 'demo'
    completion_history: list[CompletionEvent] = Field(default_factory=list, max_length=50)
    recommendation_feedback: dict[Annotated[str, Field(pattern=r'^[a-z0-9_]{1,128}$')], GoalFeedback] = Field(default_factory=dict, max_length=100)


class GoalDescription(WireModel):
    id: str
    title: str
    summary: str
    members_only: bool
    benefits: list[str]
    investment: str
    preparation: list[str]
    sources: list[str]
    reviewed: str


class GoalRequirement(WireModel):
    id: str
    label: str
    status: Literal['satisfied', 'missing', 'unknown']
    observed: bool | int | str | None
    required: bool | int | str
    note: str
    category: Literal['milestone', 'eligibility', 'context']
    children: list['GoalRequirement']


class GoalProgress(WireModel):
    satisfied: int
    missing: int
    unknown: int
    total: int


class Recommendation(WireModel):
    feedback_status: Literal['none', 'not_interested', 'maybe_later', 'too_much_work']
    feedback_reason: str
    rank: int
    recommended: bool
    availability: Literal['available', 'members_required', 'membership_unknown', 'completed']
    reasons: list[str]
    considerations: list[str]


class NextStep(WireModel):
    id: str
    title: str
    reason: str
    kind: Literal['activity', 'check']


class RemainingInvestment(WireModel):
    commitment_band: Literal['short', 'few_sessions', 'long', 'unknown', 'completed', 'blocked']
    commitment_reason: str
    state: Literal['completed', 'access_blocked', 'unknown', 'final_step', 'prerequisites', 'training', 'quest', 'diary']
    summary: str
    details: list[str]
    caveat: str


class GoalResult(WireModel):
    goal: GoalDescription
    status: Literal['completed', 'incomplete', 'unknown']
    progress: GoalProgress
    requirements: list[GoalRequirement]
    recommendation: Recommendation
    next_steps: list[NextStep]
    remaining_investment: RemainingInvestment


class GoalsResponse(WireModel):
    protocol_version: Literal[1] = 1
    dataset_version: str
    session_id: str
    revision: int
    goals: list[GoalResult]
    notes: list[str]
    recommended_goal_id: str | None
    recommendation_summary: str


class CatalogResponse(WireModel):
    protocol_version: Literal[1] = 1
    dataset_version: str
    goals: list[GoalDescription]
