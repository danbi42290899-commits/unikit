import json
import logging
from datetime import datetime, timezone

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.db import SessionLocal
from app.models.event import EventDB
from app.state.glass_state_manager import glass_state_manager
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


async def _apply_command(command: str) -> None:
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
        # Phase 1 has no real media pipeline yet; recorded as an event only.
        pass


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

            await _apply_command(command)
            state = glass_state_manager.state
            _log_event(command, state.examId, state.patientId)

            await telemetry_manager.broadcast(
                {
                    "type": "event",
                    "patientId": state.patientId,
                    "examId": state.examId,
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                    "sourceDevice": "MOCK_UNI_HUB",
                    "eventType": command,
                    "detail": {},
                }
            )

            await websocket.send_json({"type": "command_ack", "command": command, "ok": True})
    except WebSocketDisconnect:
        pass
    finally:
        logger.info("control client disconnected")
