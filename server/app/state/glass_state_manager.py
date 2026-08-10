import asyncio
from datetime import datetime, timezone
from typing import Any, Awaitable, Callable

from app.models.glass_state import GlassState

BroadcastFn = Callable[[dict], Awaitable[None]]


class GlassStateManager:
    """
    Holds the single, server-side GlassState instance. Phone and Glass are
    both expected to render from this same state rather than mirroring one
    another (see docs/architecture.md).
    """

    def __init__(self) -> None:
        self._state = GlassState()
        self._lock = asyncio.Lock()
        self._broadcast: BroadcastFn | None = None

    def set_broadcast_fn(self, fn: BroadcastFn) -> None:
        self._broadcast = fn

    @property
    def state(self) -> GlassState:
        return self._state

    async def update(self, **fields: Any) -> GlassState:
        async with self._lock:
            data = self._state.model_dump()
            data.update(fields)
            data["lastSeen"] = datetime.now(timezone.utc)
            self._state = GlassState(**data)
            snapshot = self._state
        await self._notify(snapshot)
        return snapshot

    async def reset_patient_fields(self) -> GlassState:
        return await self.update(
            patientId=None,
            examId=None,
            patientDisplayName=None,
            age=None,
            sex=None,
            examMode="NONE",
            laterality="NONE",
            recording=False,
            frozen=False,
            bpSys=None,
            bpDia=None,
            spo2=None,
            heartRate=None,
            temperature=None,
        )

    async def _notify(self, snapshot: GlassState) -> None:
        if self._broadcast is None:
            return
        payload = {"type": "glass_state", **snapshot.model_dump(mode="json")}
        await self._broadcast(payload)


glass_state_manager = GlassStateManager()
