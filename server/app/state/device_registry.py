import asyncio
from datetime import datetime, timezone

from app.config import DEVICE_STALE_TIMEOUT_SEC
from app.models.device import DeviceName, DeviceStatus

ALL_DEVICES: list[DeviceName] = [
    "UNI_HUB",
    "GLASS",
    "BLOOD_PRESSURE",
    "SPO2",
    "TEMPERATURE",
    "ECG",
    "STETHOSCOPE",
    "ENDOSCOPE",
]


class DeviceRegistry:
    """
    Tracks CONNECTED/DISCONNECTED + lastSeen per device. A device is
    considered stale (and flipped to DISCONNECTED) if it hasn't sent a
    heartbeat within DEVICE_STALE_TIMEOUT_SEC — this is what prevents
    Glass/Phone from displaying frozen stale values as if current
    (see PROJECT spec, "Disconnected sensors must NOT continue displaying
    stale values").
    """

    def __init__(self) -> None:
        self._devices: dict[str, DeviceStatus] = {
            name: DeviceStatus(device=name, state="DISCONNECTED", lastSeen=None)
            for name in ALL_DEVICES
        }
        self._lock = asyncio.Lock()

    async def heartbeat(self, device: DeviceName, quality: str | None = None) -> DeviceStatus:
        async with self._lock:
            status = DeviceStatus(
                device=device,
                state="CONNECTED",
                lastSeen=datetime.now(timezone.utc),
                quality=quality,
            )
            self._devices[device] = status
            return status

    async def mark_disconnected(self, device: DeviceName) -> DeviceStatus:
        async with self._lock:
            existing = self._devices[device]
            status = DeviceStatus(
                device=device,
                state="DISCONNECTED",
                lastSeen=existing.lastSeen,
                quality=None,
            )
            self._devices[device] = status
            return status

    def get(self, device: str) -> DeviceStatus | None:
        return self._devices.get(device)

    def get_all(self) -> list[DeviceStatus]:
        return list(self._devices.values())

    async def sweep_stale(self) -> list[DeviceStatus]:
        now = datetime.now(timezone.utc)
        changed: list[DeviceStatus] = []
        async with self._lock:
            for name, status in list(self._devices.items()):
                if status.state == "CONNECTED" and status.lastSeen:
                    age = (now - status.lastSeen).total_seconds()
                    if age > DEVICE_STALE_TIMEOUT_SEC:
                        updated = DeviceStatus(
                            device=status.device,
                            state="DISCONNECTED",
                            lastSeen=status.lastSeen,
                            quality=None,
                        )
                        self._devices[name] = updated
                        changed.append(updated)
        return changed


device_registry = DeviceRegistry()
