# Running UNI-HUB on the Raspberry Pi itself

This is the deployment path where the Pi **is** the Wi-Fi network --
Phone and Glass join a hotspot the Pi creates, and both talk to the
UNI-HUB server running on the Pi. This only needs to happen on the Pi
directly; nothing here can be driven remotely from a dev machine that
isn't on the Pi's network (see main repo `README.md` for the mock-only
dev workflow, which doesn't need any of this).

**Not what's running today.** The current prototype instead has the Pi,
Phone, and Glass all joining an existing personal mobile hotspot, with
the Pi reachable at `10.18.168.235:8000` (a hotspot-assigned address,
not the `10.42.0.1` this doc's `setup_ap.sh` flow produces). Phone/Glass
`UNI_HUB_HOST` already defaults to `10.18.168.235`. This whole doc
describes the AP-hosted mode for when the Pi becomes its own network
later -- don't run `setup_ap.sh` against the current hotspot setup.

## 1. Get the code onto the Pi

```bash
git clone https://github.com/<your-account>/unikit.git
cd unikit
```

If the repo is private, `git clone` will prompt for GitHub credentials
(a Personal Access Token as the password), or install `gh` and run
`gh auth login` first, then `gh repo clone <your-account>/unikit`.

## 2. Install the server

Raspberry Pi OS Bookworm ships a Python new enough for a plain venv (no
conda workaround needed here, unlike the dev server -- see main
`README.md`'s Setup section for why that one's different).

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r server/requirements.txt
```

## 3. Turn the Pi into the Wi-Fi AP

```bash
sudo UNIKIT_AP_SSID=UNIKIT-HUB UNIKIT_AP_PASSWORD=your-password ./pi/setup_ap.sh
```

See `pi/setup_ap.sh`'s header comment for what this does and why it
assumes NetworkManager. Confirm it worked:

```bash
nmcli -f IP4.ADDRESS device show wlan0   # expect 10.42.0.1/24
```

## 4. Run the server on boot

```bash
sudo cp pi/unikit-server.service /etc/systemd/system/
# Edit User=/WorkingDirectory=/ExecStart= in the copied file first if this
# repo isn't at /home/pi/unikit under user "pi".
sudo systemctl daemon-reload
sudo systemctl enable --now unikit-server
sudo systemctl status unikit-server   # should be active (running)
```

Or run it in the foreground for a one-off test instead of installing the
service:

```bash
cd server && uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## 5. Connect Phone and Glass

Join the `UNIKIT-HUB` Wi-Fi from both devices, then either:

- Install APKs built with the default `UNI_HUB_HOST` (`10.42.0.1`,
  matching NetworkManager's shared-mode gateway from step 3), or
- Override at launch without rebuilding:
  ```bash
  adb shell am start -n com.unikit.glass/.ui.GlassHudActivity \
      --es uni_hub_host 10.42.0.1 --ei uni_hub_port 8000
  adb shell am start -n com.unikit.phone/.ui.MainActivity \
      --es uni_hub_host 10.42.0.1 --ei uni_hub_port 8000
  ```

Verify the server sees them: `http://10.42.0.1:8000/monitor/` from any
device on the hotspot, or `curl http://10.42.0.1:8000/devices`.

## Known limitations

- No internet access for devices on this hotspot unless the Pi is also
  bridged to another uplink -- NetworkManager's `shared` mode NATs
  through whatever uplink exists, but this setup doesn't assume one.
- `pi/setup_ap.sh` is destructive-but-idempotent: re-running it deletes
  and recreates the `UniKitHotspot` NetworkManager connection.
