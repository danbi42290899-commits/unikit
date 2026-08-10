def test_create_and_get_patient(client):
    resp = client.post(
        "/patients",
        json={"patientId": "P100", "displayName": "Test Patient", "age": 30, "sex": "F"},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["patientId"] == "P100"
    assert body["displayName"] == "Test Patient"

    resp = client.get("/patients/P100")
    assert resp.status_code == 200
    assert resp.json()["patientId"] == "P100"


def test_create_duplicate_patient_conflicts(client):
    client.post("/patients", json={"patientId": "P101", "displayName": "A"})
    resp = client.post("/patients", json={"patientId": "P101", "displayName": "A"})
    assert resp.status_code == 409


def test_get_missing_patient_404(client):
    resp = client.get("/patients/DOES_NOT_EXIST")
    assert resp.status_code == 404
