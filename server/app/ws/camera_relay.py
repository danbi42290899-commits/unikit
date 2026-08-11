import asyncio
import logging

from fastapi import WebSocket

logger = logging.getLogger(__name__)


class CameraRelayManager:
    """
    Broadcast hub for /ws/camera. Separate from ConnectionManager
    (which broadcasts JSON telemetry) because this fans out raw JPEG
    frame bytes to every connected Glass/monitor viewer.
    """

    def __init__(self) -> None:
        self._viewers: set[WebSocket] = set()
        self._lock = asyncio.Lock()
        # Last frame relayed, kept only so CAPTURE (a REST-less /ws/control
        # command) has something to persist -- the live relay itself stays
        # stateless/non-buffering for viewers, see broadcast_frame().
        self._last_frame: bytes | None = None

    async def add_viewer(self, websocket: WebSocket) -> None:
        async with self._lock:
            self._viewers.add(websocket)

    async def remove_viewer(self, websocket: WebSocket) -> None:
        async with self._lock:
            self._viewers.discard(websocket)

    async def broadcast_frame(self, frame: bytes) -> None:
        self._last_frame = frame
        async with self._lock:
            viewers = list(self._viewers)
        stale = []
        for ws in viewers:
            try:
                await ws.send_bytes(frame)
            except Exception:
                stale.append(ws)
        if stale:
            async with self._lock:
                for ws in stale:
                    self._viewers.discard(ws)

    @property
    def viewer_count(self) -> int:
        return len(self._viewers)

    def get_last_frame(self) -> bytes | None:
        return self._last_frame


camera_relay = CameraRelayManager()
