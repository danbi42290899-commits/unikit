"""
Synthetic data source used only to exercise the UNI-HUB architecture and
drive UI development before real hardware/software is integrated.

Everything produced here is clearly fake:
- Vitals are the documented MOCK_VITALS baseline plus small jitter.
- PPG/ECG waveforms are simple synthetic curves (sine + noise), not
  physiologically derived signals.
- sampleRate values come from app.config's SIMULATION_* constants, which
  are arbitrary UI-development choices, not real hardware specs.

Every message this module produces sets simulated=True.
"""
import asyncio
import math
import random
from datetime import datetime, timezone
from typing import AsyncIterator

from app.config import (
    MOCK_VITALS,
    SIMULATION_ECG_SAMPLE_RATE_HZ,
    SIMULATION_PPG_SAMPLE_RATE_HZ,
    SIMULATION_SIGNAL_CHUNK_SEC,
)
from app.models.vitals import RawSignalMessage, VitalsData
from app.providers.base import ECGProvider, PPGProvider, VitalsProvider


def _jitter(base: float, spread: float) -> float:
    return base + random.uniform(-spread, spread)


class MockVitalsProvider(VitalsProvider):
    def __init__(self) -> None:
        self._connected = False

    async def connect(self) -> None:
        self._connected = True

    async def disconnect(self) -> None:
        self._connected = False

    @property
    def is_connected(self) -> bool:
        return self._connected

    async def get_vitals(self) -> VitalsData:
        return VitalsData(
            bpSys=round(_jitter(MOCK_VITALS["bpSys"], 2), 0),
            bpDia=round(_jitter(MOCK_VITALS["bpDia"], 2), 0),
            spo2=round(min(100, _jitter(MOCK_VITALS["spo2"], 1)), 0),
            heartRate=round(_jitter(MOCK_VITALS["heartRate"], 3), 0),
            temperature=round(_jitter(MOCK_VITALS["temperature"], 0.2), 1),
        )


class MockPPGProvider(PPGProvider):
    """Yields alternating PPG_IR / PPG_RED chunks."""

    def __init__(self) -> None:
        self._connected = False
        self._t = 0.0

    async def connect(self) -> None:
        self._connected = True

    async def disconnect(self) -> None:
        self._connected = False

    @property
    def is_connected(self) -> bool:
        return self._connected

    @property
    def sample_rate_hz(self) -> float | None:
        return SIMULATION_PPG_SAMPLE_RATE_HZ

    def _generate_chunk(self, amplitude: float, dc_offset: float) -> list[float]:
        n = int(SIMULATION_PPG_SAMPLE_RATE_HZ * SIMULATION_SIGNAL_CHUNK_SEC)
        heart_rate_hz = MOCK_VITALS["heartRate"] / 60.0
        samples = []
        for i in range(n):
            t = self._t + i / SIMULATION_PPG_SAMPLE_RATE_HZ
            value = dc_offset + amplitude * math.sin(2 * math.pi * heart_rate_hz * t)
            value += random.uniform(-amplitude * 0.05, amplitude * 0.05)
            samples.append(round(value, 2))
        return samples

    async def stream(self) -> AsyncIterator[RawSignalMessage]:
        while self._connected:
            now = datetime.now(timezone.utc)
            ir_samples = self._generate_chunk(amplitude=500.0, dc_offset=50000.0)
            red_samples = self._generate_chunk(amplitude=300.0, dc_offset=40000.0)
            self._t += SIMULATION_SIGNAL_CHUNK_SEC

            yield RawSignalMessage(
                signal="PPG_IR",
                sampleRate=SIMULATION_PPG_SAMPLE_RATE_HZ,
                unit="ADC_COUNT",
                timestamp=now,
                sourceDevice="MOCK_UNI_HUB",
                quality="good",
                simulated=True,
                samples=ir_samples,
            )
            yield RawSignalMessage(
                signal="PPG_RED",
                sampleRate=SIMULATION_PPG_SAMPLE_RATE_HZ,
                unit="ADC_COUNT",
                timestamp=now,
                sourceDevice="MOCK_UNI_HUB",
                quality="good",
                simulated=True,
                samples=red_samples,
            )
            await asyncio.sleep(SIMULATION_SIGNAL_CHUNK_SEC)


class MockECGProvider(ECGProvider):
    """
    Produces a crude synthetic QRS-like waveform for UI development only.
    This is NOT a physiologically accurate ECG simulator.
    """

    def __init__(self) -> None:
        self._connected = False
        self._t = 0.0

    async def connect(self) -> None:
        self._connected = True

    async def disconnect(self) -> None:
        self._connected = False

    @property
    def is_connected(self) -> bool:
        return self._connected

    @property
    def sample_rate_hz(self) -> float | None:
        return SIMULATION_ECG_SAMPLE_RATE_HZ

    def _qrs_like(self, phase: float) -> float:
        # Narrow gaussian spike near phase=0 to loosely resemble a QRS complex.
        spike = math.exp(-((phase) ** 2) / (2 * 0.01)) * 1.0
        baseline_wander = 0.05 * math.sin(2 * math.pi * 0.2 * phase)
        return spike + baseline_wander

    def _generate_chunk(self) -> list[float]:
        n = int(SIMULATION_ECG_SAMPLE_RATE_HZ * SIMULATION_SIGNAL_CHUNK_SEC)
        heart_rate_hz = MOCK_VITALS["heartRate"] / 60.0
        beat_period = 1.0 / heart_rate_hz
        samples = []
        for i in range(n):
            t = self._t + i / SIMULATION_ECG_SAMPLE_RATE_HZ
            phase_in_beat = (t % beat_period) - beat_period / 2
            value = self._qrs_like(phase_in_beat)
            value += random.uniform(-0.02, 0.02)
            samples.append(round(value, 4))
        return samples

    async def stream(self) -> AsyncIterator[RawSignalMessage]:
        while self._connected:
            now = datetime.now(timezone.utc)
            samples = self._generate_chunk()
            self._t += SIMULATION_SIGNAL_CHUNK_SEC

            yield RawSignalMessage(
                signal="ECG",
                sampleRate=SIMULATION_ECG_SAMPLE_RATE_HZ,
                unit="mV",
                timestamp=now,
                sourceDevice="MOCK_UNI_HUB",
                quality="good",
                simulated=True,
                samples=samples,
            )
            await asyncio.sleep(SIMULATION_SIGNAL_CHUNK_SEC)
