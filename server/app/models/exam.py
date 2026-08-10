from datetime import datetime, timezone

from pydantic import BaseModel
from sqlalchemy import Column, DateTime, String

from app.db import Base


class ExamDB(Base):
    __tablename__ = "exams"

    exam_id = Column(String, primary_key=True, index=True)
    patient_id = Column(String, nullable=False, index=True)
    started_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    ended_at = Column(DateTime, nullable=True)
    status = Column(String, default="ACTIVE")  # ACTIVE | ENDED


class ExamCreate(BaseModel):
    patientId: str


class ExamOut(BaseModel):
    examId: str
    patientId: str
    startedAt: datetime
    endedAt: datetime | None = None
    status: str

    model_config = {"from_attributes": True}

    @classmethod
    def from_db(cls, row: ExamDB) -> "ExamOut":
        return cls(
            examId=row.exam_id,
            patientId=row.patient_id,
            startedAt=row.started_at,
            endedAt=row.ended_at,
            status=row.status,
        )
