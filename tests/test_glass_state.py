def test_glass_state_reflects_active_exam(client):
    client.post("/patients", json={"patientId": "P300", "displayName": "Glass Patient", "age": 40, "sex": "M"})
    exam = client.post("/exams", json={"patientId": "P300"}).json()

    state = client.get("/glass/state").json()
    assert state["patientId"] == "P300"
    assert state["examId"] == exam["examId"]
    assert state["patientDisplayName"] == "Glass Patient"
    assert state["connected"] is True
    assert state["examMode"] == "NONE"

    client.post(f"/exams/{exam['examId']}/end")

    state = client.get("/glass/state").json()
    assert state["patientId"] is None
    assert state["examId"] is None
    assert state["connected"] is False


def test_status_and_devices_endpoints(client):
    resp = client.get("/status")
    assert resp.status_code == 200
    assert resp.json()["server"] == "MOCK_UNI_HUB"

    resp = client.get("/devices")
    assert resp.status_code == 200
    devices = resp.json()
    assert any(d["device"] == "UNI_HUB" for d in devices)
