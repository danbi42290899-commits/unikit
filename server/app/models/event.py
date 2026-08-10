from datetime import datetime, timezone

from pydantic import BaseModel
from sqlalchemy import Column, DateTime, Integer, String

from app.db import Base


class EventDB(Base):
    __tablename__ = "events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    exam_id = Column(String, nullable=False, index=True)
    patient_id = Column(String, nullable=True, index=True)
    event_type = Column(String, nullable=False)  # e.g. CAPTURE, START_RECORDING
    payload = Column(String, nullable=True)  # JSON-encoded extra detail
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class EventOut(BaseModel):
    id: int
    examId: str
    patientId: str | None = None
    eventType: str
    payload: str | None = None
    createdAt: datetime

    model_config = {"from_attributes": True}

    @classmethod
    def from_db(cls, row: EventDB) -> "EventOut":
        return cls(
            id=row.id,
            examId=row.exam_id,
            patientId=row.patient_id,
            eventType=row.event_type,
            payload=row.payload,
            createdAt=row.created_at,
        )
