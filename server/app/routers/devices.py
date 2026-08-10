from fastapi import APIRouter

from app.state.device_registry import device_registry

router = APIRouter(tags=["devices"])


@router.get("/devices")
def get_devices():
    return [d.model_dump(mode="json") for d in device_registry.get_all()]
