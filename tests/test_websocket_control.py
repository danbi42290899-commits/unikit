def test_control_command_updates_glass_state(client):
    client.post("/patients", json={"patientId": "P400", "displayName": "Control Patient"})
    client.post("/exams", json={"patientId": "P400"})

    with client.websocket_connect("/ws/control") as ws:
        ws.send_json({"command": "SET_OTOSCOPE_RIGHT"})
        ack = ws.receive_json()
        assert ack == {"type": "command_ack", "command": "SET_OTOSCOPE_RIGHT", "ok": True}

        ws.send_json({"command": "START_RECORDING"})
        ack = ws.receive_json()
        assert ack["ok"] is True

    state = client.get("/glass/state").json()
    assert state["examMode"] == "OTOSCOPE_RIGHT"
    assert state["laterality"] == "RIGHT"
    assert state["recording"] is True


def test_control_rejects_unknown_command(client):
    with client.websocket_connect("/ws/control") as ws:
        ws.send_json({"command": "NOT_A_REAL_COMMAND"})
        resp = ws.receive_json()
        assert resp["type"] == "error"


def test_telemetry_ws_delivers_a_message(client):
    with client.websocket_connect("/ws/telemetry") as ws:
        msg = ws.receive_json()
        assert msg["type"] in {"vitals", "raw_signal", "device_status", "glass_state", "event"}
        assert msg.get("simulated", True) is True or "simulated" not in msg
