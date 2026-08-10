"""
Common data contract types shared by REST and WebSocket payloads.

Every measurement message carries the base envelope fields required by
docs/data_contracts.md: patientId, examId, timestamp, sourceDevice,
quality, simulated.
"""
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

Quality = Literal["good", "fair", "poor", "unknown"]


class VitalsData(BaseModel):
    bpSys: float | None = None
    bpDia: float | None = None
    spo2: float | None = None
    heartRate: float | None = None
    temperature: float | None = None


class VitalsMessage(BaseModel):
    type: Literal["vitals"] = "vitals"
    patientId: str | None = None
    examId: str | None = None
    timestamp: datetime
    sourceDevice: str
    quality: Quality = "unknown"
    simulated: bool = True
    data: VitalsData


class RawSignalMessage(BaseModel):
    """
    Raw waveform packet. sampleRate is nullable on the wire: it must only be
    populated when the producing device/provider actually knows its real
    sampling rate. In MOCK mode this is filled with the documented
    simulation-only constant from app.config (never presented as a real
    hardware spec).
    """

    type: Literal["raw_signal"] = "raw_signal"
    patientId: str | None = None
    examId: str | None = None
    signal: Literal["PPG_RED", "PPG_IR", "ECG", "BP_CUFF_PRESSURE", "STETHOSCOPE_AUDIO"]
    sampleRate: float | None = None
    unit: str
    channel: str = "default"
    timestamp: datetime
    sourceDevice: str
    quality: Quality = "unknown"
    simulated: bool = True
    samples: list[float] = Field(default_factory=list)


class EventMessage(BaseModel):
    type: Literal["event"] = "event"
    patientId: str | None = None
    examId: str | None = None
    timestamp: datetime
    sourceDevice: str
    eventType: str
    detail: dict = Field(default_factory=dict)
