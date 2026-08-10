from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db import get_db
from app.models.patient import PatientCreate, PatientDB, PatientOut

router = APIRouter(tags=["patients"])


@router.post("/patients", response_model=PatientOut, status_code=201)
def create_patient(payload: PatientCreate, db: Session = Depends(get_db)):
    existing = db.get(PatientDB, payload.patientId)
    if existing:
        raise HTTPException(status_code=409, detail="patientId already exists")

    row = PatientDB(
        patient_id=payload.patientId,
        display_name=payload.displayName,
        age=payload.age,
        sex=payload.sex,
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return PatientOut.from_db(row)


@router.get("/patients/{patient_id}", response_model=PatientOut)
def get_patient(patient_id: str, db: Session = Depends(get_db)):
    row = db.get(PatientDB, patient_id)
    if not row:
        raise HTTPException(status_code=404, detail="patient not found")
    return PatientOut.from_db(row)
