import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db import get_db
from app.models.exam import ExamCreate, ExamDB, ExamOut
from app.models.patient import PatientDB
from app.state.glass_state_manager import glass_state_manager

router = APIRouter(tags=["exams"])


@router.post("/exams", response_model=ExamOut, status_code=201)
async def create_exam(payload: ExamCreate, db: Session = Depends(get_db)):
    patient = db.get(PatientDB, payload.patientId)
    if not patient:
        raise HTTPException(status_code=404, detail="patient not found")

    exam_id = f"E{uuid.uuid4().hex[:8].upper()}"
    row = ExamDB(exam_id=exam_id, patient_id=payload.patientId, status="ACTIVE")
    db.add(row)
    db.commit()
    db.refresh(row)

    await glass_state_manager.update(
        connected=True,
        patientId=patient.patient_id,
        examId=exam_id,
        patientDisplayName=patient.display_name,
        age=patient.age,
        sex=patient.sex,
        examMode="NONE",
        laterality="NONE",
        recording=False,
        frozen=False,
    )
    return ExamOut.from_db(row)


@router.get("/exams/{exam_id}", response_model=ExamOut)
def get_exam(exam_id: str, db: Session = Depends(get_db)):
    row = db.get(ExamDB, exam_id)
    if not row:
        raise HTTPException(status_code=404, detail="exam not found")
    return ExamOut.from_db(row)


@router.post("/exams/{exam_id}/end", response_model=ExamOut)
async def end_exam(exam_id: str, db: Session = Depends(get_db)):
    row = db.get(ExamDB, exam_id)
    if not row:
        raise HTTPException(status_code=404, detail="exam not found")

    if row.status != "ENDED":
        row.status = "ENDED"
        row.ended_at = datetime.now(timezone.utc)
        db.commit()
        db.refresh(row)

        if glass_state_manager.state.examId == exam_id:
            await glass_state_manager.reset_patient_fields()
            await glass_state_manager.update(connected=False)

    return ExamOut.from_db(row)
