from datetime import datetime, timezone

from pydantic import BaseModel, Field
from sqlalchemy import Column, DateTime, Integer, String

from app.db import Base


class PatientDB(Base):
    __tablename__ = "patients"

    patient_id = Column(String, primary_key=True, index=True)
    display_name = Column(String, nullable=False)
    age = Column(Integer, nullable=True)
    sex = Column(String, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class PatientCreate(BaseModel):
    patientId: str
    displayName: str
    age: int | None = None
    sex: str | None = None


class PatientOut(BaseModel):
    patientId: str
    displayName: str
    age: int | None = None
    sex: str | None = None
    createdAt: datetime

    model_config = {"from_attributes": True}

    @classmethod
    def from_db(cls, row: PatientDB) -> "PatientOut":
        return cls(
            patientId=row.patient_id,
            displayName=row.display_name,
            age=row.age,
            sex=row.sex,
            createdAt=row.created_at,
        )
