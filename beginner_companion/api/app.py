"""Run with: python -m uvicorn beginner_companion.api.app:app --host 127.0.0.1 --port 8765."""
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from .models import ErrorResponse, EvaluationRequest, EvaluationResponse, Requirement
from .goals import GoalsRequest, GoalsResponse, CatalogResponse
from ..domain.catalog import load_catalog, evaluate_goals
from ..domain.recommendations import recommend

app = FastAPI(title="Beginner companion bridge", version="1.0.0")
catalog = load_catalog()


@app.get('/v1/catalog', response_model=CatalogResponse)
def goal_catalog():
    return dict(dataset_version=catalog['version'], goals=[
        {key: value for key, value in goal.items() if key not in ('requirements', 'completion', 'matching')}
        for goal in catalog['goals']])


@app.post('/v1/goals/evaluate', response_model=GoalsResponse,
          responses={409: {'model': ErrorResponse}, 422: {'model': ErrorResponse}})
def goal_evaluation(payload: GoalsRequest):
    if payload.protocol_version != 1:
        return error(409, 'version_mismatch', 'This service supports protocol version 1.')
    account = payload.account.model_dump()
    recommendations = recommend(catalog, evaluate_goals(catalog, account),
                                payload.preferences.model_dump(), account,
                                [event.model_dump() for event in payload.completion_history],
                                feedback={key: value.model_dump() for key, value in payload.recommendation_feedback.items()})
    return GoalsResponse(dataset_version=catalog['version'], session_id=payload.session_id,
                         revision=payload.revision,
                         **recommendations,
                         notes=[('Live character observations from RuneLite; unverified milestones remain unknown.'
                                 if payload.source == 'live' else 'Fictional demo account; no live account observations.'),
                                'Milestone counts describe tracked checks, not time or effort.',
                                'Missing observations stay unknown. Preparation is not automatically verified.'])


def error(status: int, code: str, message: str):
    body = ErrorResponse(error={"code": code, "message": message})
    return JSONResponse(status_code=status, content=body.model_dump())


@app.exception_handler(RequestValidationError)
async def invalid_request(request: Request, exc: RequestValidationError):
    # Do not echo arbitrary input or account observations in error messages.
    return error(422, "invalid_request", "Request does not match the v1 message format.")


@app.exception_handler(HTTPException)
async def http_error(request: Request, exc: HTTPException):
    return error(exc.status_code, "http_error", "Endpoint or HTTP method is not supported.")


@app.get("/v1/health")
def health():
    return {"protocol_version": 1, "status": "ok", "dataset_version": "bridge-demo-1"}


@app.post("/v1/evaluate", response_model=EvaluationResponse,
          responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse},
                     422: {"model": ErrorResponse}})
def evaluate(payload: EvaluationRequest):
    if payload.protocol_version != 1:
        return error(409, "version_mismatch", "This service supports protocol version 1.")
    if payload.goal_id != "wear_rune_platebody":
        return error(404, "unknown_goal", "This bridge demo supports wear_rune_platebody only.")
    level = payload.account.skill_levels.defence
    quest = payload.account.quest_progress.dragon_slayer_i
    return EvaluationResponse(
        session_id=payload.session_id, revision=payload.revision, goal_id=payload.goal_id,
        requirements=[
            Requirement(id="defence_40", label="Reach 40 Defence", observed=level,
                        required=40, status="unknown" if level is None else
                        "satisfied" if level >= 40 else "missing"),
            Requirement(id="dragon_slayer_i", label="Complete Dragon Slayer I",
                        observed=quest, required="completed", status="unknown" if quest == "unknown"
                        else "satisfied" if quest == "completed" else "missing"),
        ],
        notes=["Equipment requirements only. Item ownership, equipment state and quest preparation are not checked."],
    )
