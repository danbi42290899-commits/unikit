from datetime import datetime, timezone
from typing import Literal

from pydantic import BaseModel
from sqlalchemy import Column, DateTime, String

from app.db import Base

MediaMode = Literal["OTOSCOPE", "COLPOSCOPE"]
MediaLaterality = Literal["LEFT", "RIGHT", "NONE"]


class MediaDB(Base):
    __tablename__ = "media"

    media_id = Column(String, primary_key=True, index=True)
    exam_id = Column(String, nullable=False, index=True)
    patient_id = Column(String, nullable=True)
    mode = Column(String, nullable=False)
    laterality = Column(String, nullable=False, default="NONE")
    source_device = Column(String, nullable=False)
    file_path = Column(String, nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class MediaOut(BaseModel):
    mediaId: str
    examId: str
    patientId: str | None = None
    mode: MediaMode
    laterality: MediaLaterality
    sourceDevice: str
    timestamp: datetime
    fileUrl: str

    model_config = {"from_attributes": True}

    @classmethod
    def from_db(cls, row: MediaDB) -> "MediaOut":
        return cls(
            mediaId=row.media_id,
            examId=row.exam_id,
            patientId=row.patient_id,
            mode=row.mode,
            laterality=row.laterality,
            sourceDevice=row.source_device,
            timestamp=row.created_at,
            fileUrl=f"/media/{row.media_id}/file",
        )
