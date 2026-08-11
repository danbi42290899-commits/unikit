import json
import logging
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.config import MEDIA_STORAGE_DIR
from app.db import SessionLocal
from app.models.event import EventDB
from app.models.media import MediaDB
from app.state.glass_state_manager import glass_state_manager
from app.ws.camera_relay import camera_relay
from app.ws.connection_manager import telemetry_manager

logger = logging.getLogger(__name__)
router = APIRouter()

VALID_COMMANDS = {
    "CAPTURE",
    "START_RECORDING",
    "STOP_RECORDING",
    "FREEZE",
    "UNFREEZE",
    "SET_OTOSCOPE_LEFT",
    "SET_OTOSCOPE_RIGHT",
    "SET_COLPOSCOPE",
}


def _capture_mode_and_laterality(exam_mode: str, laterality: str) -> tuple[str, str] | None:
    """Maps GlassState's examMode/laterality onto a media (mode, laterality)
    pair, or None if CAPTURE isn't meaningful in the current mode (e.g. no
    otoscope/colposcope screen is active)."""
    if exam_mode in ("OTOSCOPE_LEFT", "OTOSCOPE_RIGHT"):
        return "OTOSCOPE", laterality
    if exam_mode == "COLPOSCOPE":
        return "COLPOSCOPE", "NONE"
    return None


def _persist_capture() -> dict:
    """Writes the camera relay's last-seen frame to disk and records a
    MediaDB row, or reports why it couldn't (still images only -- see
    docs/development_roadmap.md for why video/audio stay event-only)."""
    state = glass_state_manager.state
    mode_laterality = _capture_mode_and_laterality(state.examMode, state.laterality)
    if state.examId is None or mode_laterality is None:
        return {"ok": False, "reason": "no active exam in an otoscope/colposcope mode"}

    frame = camera_relay.get_last_frame()
    if frame is None:
        return {"ok": False, "reason": "no live camera frame received yet"}

    mode, laterality = mode_laterality
    media_id = f"M{uuid.uuid4().hex[:8].upper()}"
    exam_dir = MEDIA_STORAGE_DIR / state.examId
    exam_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc)
    file_path = exam_dir / f"{mode}_{laterality}_{timestamp.strftime('%Y%m%dT%H%M%S%f')}.jpg"
    file_path.write_bytes(frame)

    db = SessionLocal()
    try:
        db.add(
            MediaDB(
                media_id=media_id,
                exam_id=state.examId,
                patient_id=state.patientId,
                mode=mode,
                laterality=laterality,
                source_device="PHONE_CAMERA",
                file_path=str(file_path),
                created_at=timestamp,
            )
        )
        db.commit()
    finally:
        db.close()

    return {"ok": True, "mediaId": media_id}


async def _apply_command(command: str) -> dict | None:
    if command == "START_RECORDING":
        await glass_state_manager.update(recording=True)
    elif command == "STOP_RECORDING":
        await glass_state_manager.update(recording=False)
    elif command == "FREEZE":
        await glass_state_manager.update(frozen=True)
    elif command == "UNFREEZE":
        await glass_state_manager.update(frozen=False)
    elif command == "SET_OTOSCOPE_LEFT":
        await glass_state_manager.update(examMode="OTOSCOPE_LEFT", laterality="LEFT")
    elif command == "SET_OTOSCOPE_RIGHT":
        await glass_state_manager.update(examMode="OTOSCOPE_RIGHT", laterality="RIGHT")
    elif command == "SET_COLPOSCOPE":
        await glass_state_manager.update(examMode="COLPOSCOPE", laterality="NONE")
    elif command == "CAPTURE":
        return _persist_capture()
    return None


def _log_event(command: str, exam_id: str | None, patient_id: str | None) -> None:
    db = SessionLocal()
    try:
        db.add(EventDB(exam_id=exam_id or "NONE", patient_id=patient_id, event_type=command))
        db.commit()
    finally:
        db.close()


@router.websocket("/ws/control")
async def ws_control(websocket: WebSocket):
    """
    Command channel: Phone (later Glass gesture relay / dev monitor) sends
    JSON {"command": "..."} messages here. Commands mutate the single
    shared GlassState, which is then broadcast to everyone listening on
    /ws/telemetry — this is the "Phone -> UNI-HUB -> GlassState ->
    Phone + Glass" flow, not direct phone-to-glass control.
    """
    await websocket.accept()
    logger.info("control client connected")
    try:
        while True:
            raw = await websocket.receive_text()
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                await websocket.send_json({"type": "error", "message": "invalid JSON"})
                continue

            command = payload.get("command")
            if command not in VALID_COMMANDS:
                await websocket.send_json(
                    {"type": "error", "message": f"unknown command: {command}"}
                )
                continue

            result = await _apply_command(command)
            state = glass_state_manager.state
            _log_event(command, state.examId, state.patientId)

            # Only CAPTURE produces a result dict (media persisted or not);
            # every other command is unconditionally ok once applied.
            command_ok = result is None or result.get("ok", True)
            detail = {k: v for k, v in (result or {}).items() if k != "ok"}

            await telemetry_manager.broadcast(
                {
                    "type": "event",
                    "patientId": state.patientId,
                    "examId": state.examId,
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                    "sourceDevice": "MOCK_UNI_HUB",
                    "eventType": command,
                    "detail": detail,
                }
            )

            ack = {"type": "command_ack", "command": command, "ok": command_ok}
            ack.update(detail)
            await websocket.send_json(ack)
    except WebSocketDisconnect:
        pass
    finally:
        logger.info("control client disconnected")
