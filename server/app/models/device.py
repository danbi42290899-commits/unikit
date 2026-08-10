from datetime import datetime
from typing import Literal

from pydantic import BaseModel

ConnectionState = Literal["CONNECTED", "DISCONNECTED"]

DeviceName = Literal[
    "UNI_HUB",
    "GLASS",
    "BLOOD_PRESSURE",
    "SPO2",
    "TEMPERATURE",
    "ECG",
    "STETHOSCOPE",
    "ENDOSCOPE",
]


class DeviceStatus(BaseModel):
    device: DeviceName
    state: ConnectionState = "DISCONNECTED"
    lastSeen: datetime | None = None
    quality: str | None = None
    simulated: bool = True
