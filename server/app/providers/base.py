"""
Provider interfaces.

Routers, WebSocket handlers, and every downstream client (Phone, Glass)
must depend only on these interfaces and on the shared data-contract types
in app.models — never on a concrete provider class. Today the only
implementation is MockSensorProvider (mock_provider.py). Later phases add
ExistingProgramAdapter (wraps the Windows signal program) and/or a
RaspberryPiSensorProvider; only server/app/main.py's provider wiring
changes (see docs/integration_adapter.md).
"""
from abc import ABC, abstractmethod
from typing import AsyncIterator

from app.models.vitals import RawSignalMessage, VitalsData


class SensorProvider(ABC):
    @abstractmethod
    async def connect(self) -> None: ...

    @abstractmethod
    async def disconnect(self) -> None: ...

    @property
    @abstractmethod
    def is_connected(self) -> bool: ...


class VitalsProvider(SensorProvider):
    @abstractmethod
    async def get_vitals(self) -> VitalsData: ...


class PPGProvider(SensorProvider):
    @property
    @abstractmethod
    def sample_rate_hz(self) -> float | None: ...

    @abstractmethod
    def stream(self) -> AsyncIterator[RawSignalMessage]: ...


class ECGProvider(SensorProvider):
    @property
    @abstractmethod
    def sample_rate_hz(self) -> float | None: ...

    @abstractmethod
    def stream(self) -> AsyncIterator[RawSignalMessage]: ...


class BPProvider(SensorProvider):
    @abstractmethod
    async def get_reading(self) -> VitalsData: ...


class TemperatureProvider(SensorProvider):
    @abstractmethod
    async def get_temperature(self) -> float: ...


class StethoscopeProvider(SensorProvider):
    """Reserved for a later phase. Not implemented by MockSensorProvider yet."""

    @abstractmethod
    def stream_audio(self) -> AsyncIterator[bytes]: ...


class EndoscopeProvider(SensorProvider):
    """Reserved for a later phase (WebRTC). Not implemented yet."""

    @abstractmethod
    async def capture_image(self) -> bytes: ...
