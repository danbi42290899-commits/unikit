def test_create_exam_for_missing_patient_404(client):
    resp = client.post("/exams", json={"patientId": "NOPE"})
    assert resp.status_code == 404


def test_create_and_end_exam(client):
    client.post("/patients", json={"patientId": "P200", "displayName": "Exam Patient"})

    resp = client.post("/exams", json={"patientId": "P200"})
    assert resp.status_code == 201
    exam = resp.json()
    assert exam["patientId"] == "P200"
    assert exam["status"] == "ACTIVE"
    exam_id = exam["examId"]

    resp = client.get(f"/exams/{exam_id}")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ACTIVE"

    resp = client.post(f"/exams/{exam_id}/end")
    assert resp.status_code == 200
    ended = resp.json()
    assert ended["status"] == "ENDED"
    assert ended["endedAt"] is not None

    # Ending twice is idempotent, not an error.
    resp = client.post(f"/exams/{exam_id}/end")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ENDED"


def test_get_missing_exam_404(client):
    resp = client.get("/exams/DOES_NOT_EXIST")
    assert resp.status_code == 404
