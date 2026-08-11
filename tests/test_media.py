def test_capture_without_active_otoscope_mode_fails_cleanly(client):
    client.post("/patients", json={"patientId": "P500", "displayName": "Capture Patient"})
    client.post("/exams", json={"patientId": "P500"})

    with client.websocket_connect("/ws/control") as ws:
        ws.send_json({"command": "CAPTURE"})
        ack = ws.receive_json()
        assert ack["ok"] is False
        assert "reason" in ack


def test_capture_without_camera_frame_fails_cleanly(client):
    client.post("/patients", json={"patientId": "P501", "displayName": "Capture Patient 2"})
    client.post("/exams", json={"patientId": "P501"})

    with client.websocket_connect("/ws/control") as ws:
        ws.send_json({"command": "SET_OTOSCOPE_LEFT"})
        ws.receive_json()

        ws.send_json({"command": "CAPTURE"})
        ack = ws.receive_json()
        assert ack["ok"] is False
        assert "no live camera frame" in ack["reason"]


def test_capture_persists_image_and_is_listed_and_downloadable(client):
    client.post("/patients", json={"patientId": "P502", "displayName": "Capture Patient 3"})
    exam = client.post("/exams", json={"patientId": "P502"}).json()
    exam_id = exam["examId"]

    with client.websocket_connect("/ws/camera?role=PHONE") as phone:
        phone.send_bytes(b"\xff\xd8fake-otoscope-jpeg\xff\xd9")

        with client.websocket_connect("/ws/control") as ws:
            ws.send_json({"command": "SET_OTOSCOPE_RIGHT"})
            ws.receive_json()

            ws.send_json({"command": "CAPTURE"})
            ack = ws.receive_json()
            assert ack["ok"] is True
            media_id = ack["mediaId"]

    media_list = client.get(f"/exams/{exam_id}/media").json()
    assert len(media_list) == 1
    assert media_list[0]["mediaId"] == media_id
    assert media_list[0]["mode"] == "OTOSCOPE"
    assert media_list[0]["laterality"] == "RIGHT"
    assert media_list[0]["examId"] == exam_id

    downloaded = client.get(f"/media/{media_id}/file")
    assert downloaded.status_code == 200
    assert downloaded.content == b"\xff\xd8fake-otoscope-jpeg\xff\xd9"


def test_media_file_not_found_returns_404(client):
    resp = client.get("/media/MDOESNOTEXIST/file")
    assert resp.status_code == 404
