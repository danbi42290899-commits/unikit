def test_camera_frame_relayed_to_glass_viewer(client):
    with client.websocket_connect("/ws/camera?role=GLASS") as viewer:
        with client.websocket_connect("/ws/camera?role=PHONE") as phone:
            phone.send_bytes(b"\xff\xd8fake-jpeg-bytes\xff\xd9")
            received = viewer.receive_bytes()
            assert received == b"\xff\xd8fake-jpeg-bytes\xff\xd9"

    devices = {d["device"]: d for d in client.get("/devices").json()}
    assert devices["PHONE_CAMERA"]["state"] == "DISCONNECTED"


def test_camera_rejects_unknown_role(client):
    try:
        with client.websocket_connect("/ws/camera?role=NOPE"):
            pass
        assert False, "expected connection to be rejected"
    except Exception:
        pass


def test_camera_marks_phone_connected_while_streaming(client):
    with client.websocket_connect("/ws/camera?role=PHONE") as phone:
        phone.send_bytes(b"frame1")
        devices = {d["device"]: d for d in client.get("/devices").json()}
        assert devices["PHONE_CAMERA"]["state"] == "CONNECTED"
