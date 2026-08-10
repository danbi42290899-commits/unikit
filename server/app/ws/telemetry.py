import asyncio
import logging
from datetime import datetime, timezone

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.config import SIMULATION_TELEMETRY_INTERVAL_SEC
from app.models.vitals import RawSignalMessage, VitalsMessage
from app.providers.mock_provider import MockECGProvider, MockPPGProvider, MockVitalsProvider
from app.state.device_registry import device_registry
from app.state.glass_state_manager import glass_state_manager
from app.ws.connection_manager import telemetry_manager

logger = logging.getLogger(__name__)
router = APIRouter()

HEARTBEAT_DEVICES = ["UNI_HUB", "BLOOD_PRESSURE", "SPO2", "TEMPERATURE", "ECG"]


@router.websocket("/ws/telemetry")
async def ws_telemetry(websocket: WebSocket):
    """
    Broadcast-only channel: Phone, Glass, and the developer monitor all
    connect here to receive vitals / raw_signal / glass_state /
    device_status / event messages. Pass ?client=GLASS to have this
    connection's presence tracked in the device registry.
    """
    client_role = websocket.query_params.get("client")
    await telemetry_manager.connect(websocket)
    if client_role == "GLASS":
        await device_registry.heartbeat("GLASS", quality="good")
        await telemetry_manager.broadcast(
            {"type": "device_status", **device_registry.get("GLASS").model_dump(mode="json")}
        )
    logger.info("telemetry client connected (client=%s)", client_role)

    try:
        while True:
            try:
                await asyncio.wait_for(websocket.receive_text(), timeout=2.0)
            except asyncio.TimeoutError:
                pass
            if client_role == "GLASS":
                await device_registry.heartbeat("GLASS", quality="good")
    except WebSocketDisconnect:
        pass
    finally:
        await telemetry_manager.disconnect(websocket)
        if client_role == "GLASS":
            status = await device_registry.mark_disconnected("GLASS")
            await telemetry_manager.broadcast(
                {"type": "device_status", **status.model_dump(mode="json")}
            )
        logger.info("telemetry client disconnected (client=%s)", client_role)


async def _attach_exam_context(msg: RawSignalMessage) -> RawSignalMessage:
    state = glass_state_manager.state
    msg.patientId = state.patientId
    msg.examId = state.examId
    return msg


async def _consume_signal_stream(provider, label: str) -> None:
    async for msg in provider.stream():
        msg = await _attach_exam_context(msg)
        await telemetry_manager.broadcast(msg.model_dump(mode="json"))


async def _vitals_and_device_loop(vitals_provider: MockVitalsProvider) -> None:
    while True:
        vitals = await vitals_provider.get_vitals()
        state = glass_state_manager.state
        message = VitalsMessage(
            patientId=state.patientId,
            examId=state.examId,
            timestamp=datetime.now(timezone.utc),
            sourceDevice="MOCK_UNI_HUB",
            quality="good",
            simulated=True,
            data=vitals,
        )
        await telemetry_manager.broadcast(message.model_dump(mode="json"))

        if state.examId:
            await glass_state_manager.update(
                bpSys=vitals.bpSys,
                bpDia=vitals.bpDia,
                spo2=vitals.spo2,
                heartRate=vitals.heartRate,
                temperature=vitals.temperature,
            )

        for device in HEARTBEAT_DEVICES:
            status = await device_registry.heartbeat(device, quality="good")
            await telemetry_manager.broadcast(
                {"type": "device_status", **status.model_dump(mode="json")}
            )

        for status in await device_registry.sweep_stale():
            await telemetry_manager.broadcast(
                {"type": "device_status", **status.model_dump(mode="json")}
            )

        await asyncio.sleep(SIMULATION_TELEMETRY_INTERVAL_SEC)


async def telemetry_loop(
    vitals_provider: MockVitalsProvider,
    ppg_provider: MockPPGProvider,
    ecg_provider: MockECGProvider,
) -> None:
    """
    Background task started at app startup. Runs for the lifetime of the
    process, continuously broadcasting simulated vitals/PPG/ECG and
    device heartbeats over /ws/telemetry.
    """
    await vitals_provider.connect()
    await ppg_provider.connect()
    await ecg_provider.connect()

    await asyncio.gather(
        _vitals_and_device_loop(vitals_provider),
        _consume_signal_stream(ppg_provider, "PPG"),
        _consume_signal_stream(ecg_provider, "ECG"),
    )
