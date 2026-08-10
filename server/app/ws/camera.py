import logging
from datetime import datetime, timezone

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.state.device_registry import device_registry
from app.ws.connection_manager import telemetry_manager
from app.ws.camera_relay import camera_relay

logger = logging.getLogger(__name__)
router = APIRouter()


async def _broadcast_device_status(device: str) -> None:
    status = device_registry.get(device)
    if status is not None:
        await telemetry_manager.broadcast(
            {"type": "device_status", **status.model_dump(mode="json")}
        )


@router.websocket("/ws/camera")
async def ws_camera(websocket: WebSocket):
    """
    Binary video relay: Phone connects with ?role=PHONE and sends one
    binary WebSocket message per JPEG frame; Glass (and the dev monitor)
    connect with ?role=GLASS to receive each frame relayed verbatim.
    Kept separate from /ws/telemetry (JSON vitals) so the vitals stream
    never has to share bandwidth/parsing with frame bytes. Same
    Phone -> UNI-HUB -> Glass flow as /ws/control, not a direct link.
    """
    role = websocket.query_params.get("role")
    if role not in ("PHONE", "GLASS"):
        await websocket.close(code=1008)
        return

    await websocket.accept()
    logger.info("camera client connected (role=%s)", role)

    if role == "GLASS":
        await camera_relay.add_viewer(websocket)
        try:
            while True:
                # Viewers don't send anything meaningful; just detect disconnect.
                await websocket.receive_text()
        except WebSocketDisconnect:
            pass
        finally:
            await camera_relay.remove_viewer(websocket)
            logger.info("camera viewer disconnected")
        return

    # role == "PHONE"
    await device_registry.heartbeat("PHONE_CAMERA", quality="good")
    await _broadcast_device_status("PHONE_CAMERA")
    frame_count = 0
    try:
        while True:
            frame = await websocket.receive_bytes()
            frame_count += 1
            await device_registry.heartbeat("PHONE_CAMERA", quality="good")
            await camera_relay.broadcast_frame(frame)
    except WebSocketDisconnect:
        pass
    finally:
        await device_registry.mark_disconnected("PHONE_CAMERA")
        await _broadcast_device_status("PHONE_CAMERA")
        logger.info(
            "camera phone disconnected (frames relayed=%d) at %s",
            frame_count,
            datetime.now(timezone.utc).isoformat(),
        )
