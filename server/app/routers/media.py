from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.db import get_db
from app.models.media import MediaDB, MediaOut

router = APIRouter(tags=["media"])


@router.get("/exams/{exam_id}/media", response_model=list[MediaOut])
def list_exam_media(exam_id: str, db: Session = Depends(get_db)):
    rows = (
        db.query(MediaDB)
        .filter(MediaDB.exam_id == exam_id)
        .order_by(MediaDB.created_at.asc())
        .all()
    )
    return [MediaOut.from_db(row) for row in rows]


@router.get("/media/{media_id}/file")
def get_media_file(media_id: str, db: Session = Depends(get_db)):
    row = db.get(MediaDB, media_id)
    if not row:
        raise HTTPException(status_code=404, detail="media not found")
    return FileResponse(row.file_path, media_type="image/jpeg")
