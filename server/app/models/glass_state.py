from datetime import datetime
from typing import Literal

from pydantic import BaseModel

ExamMode = Literal["OTOSCOPE_LEFT", "OTOSCOPE_RIGHT", "COLPOSCOPE", "NONE"]


class GlassState(BaseModel):
    connected: bool = False

    patientId: str | None = None
    examId: str | None = None
    patientDisplayName: str | None = None
    age: int | None = None
    sex: str | None = None

    bpSys: float | None = None
    bpDia: float | None = None
    spo2: float | None = None
    heartRate: float | None = None
    temperature: float | None = None

    examMode: ExamMode = "NONE"
    laterality: Literal["LEFT", "RIGHT", "NONE"] = "NONE"

    recording: bool = False
    frozen: bool = False

    endoscopeConnected: bool = False

    lastSeen: datetime | None = None
    simulated: bool = True
