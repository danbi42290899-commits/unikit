import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app.config import STATIC_MONITOR_DIR
from app.db import init_db
from app.logging_config import configure_logging
from app.providers.mock_provider import MockECGProvider, MockPPGProvider, MockVitalsProvider
from app.routers import devices, exams, glass, media, patients, status
from app.state.glass_state_manager import glass_state_manager
from app.ws import camera, control, telemetry
from app.ws.connection_manager import telemetry_manager
from app.ws.telemetry import telemetry_loop

configure_logging()
logger = logging.getLogger("uni_hub")


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    glass_state_manager.set_broadcast_fn(telemetry_manager.broadcast)

    vitals_provider = MockVitalsProvider()
    ppg_provider = MockPPGProvider()
    ecg_provider = MockECGProvider()
    task = asyncio.create_task(telemetry_loop(vitals_provider, ppg_provider, ecg_provider))
    logger.info("UNI-HUB startup complete (SIMULATION mode, mock providers active)")

    try:
        yield
    finally:
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
        logger.info("UNI-HUB shutdown complete")


app = FastAPI(
    title="UNI-KIT Mock UNI-HUB",
    version="0.1.0",
    description="Development-only Mock UNI-HUB. All vitals/signals are simulated.",
    lifespan=lifespan,
)

app.include_router(patients.router)
app.include_router(exams.router)
app.include_router(status.router)
app.include_router(devices.router)
app.include_router(glass.router)
app.include_router(media.router)
app.include_router(telemetry.router)
app.include_router(control.router)
app.include_router(camera.router)

app.mount(
    "/monitor",
    StaticFiles(directory=str(STATIC_MONITOR_DIR), html=True),
    name="monitor",
)


@app.get("/")
def root():
    return {
        "service": "UNI-KIT Mock UNI-HUB",
        "mode": "SIMULATION",
        "monitor": "/monitor/",
        "docs": "/docs",
    }
