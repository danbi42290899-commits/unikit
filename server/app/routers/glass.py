from fastapi import APIRouter

from app.state.glass_state_manager import glass_state_manager

router = APIRouter(tags=["glass"])


@router.get("/glass/state")
def get_glass_state():
    return glass_state_manager.state.model_dump(mode="json")
