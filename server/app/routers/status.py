from fastapi import APIRouter

from app.state.glass_state_manager import glass_state_manager
from app.ws.connection_manager import telemetry_manager

router = APIRouter(tags=["status"])


@router.get("/status")
def get_status():
    return {
        "server": "MOCK_UNI_HUB",
        "mode": "SIMULATION",
        "telemetryClients": telemetry_manager.connection_count,
        "glassConnected": glass_state_manager.state.connected,
        "activePatientId": glass_state_manager.state.patientId,
        "activeExamId": glass_state_manager.state.examId,
    }
