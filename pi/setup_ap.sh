#!/usr/bin/env bash
# Turns this Raspberry Pi into the UNI-HUB Wi-Fi access point that Phone
# and Glass join directly (see docs/architecture.md's "sensors -> Pi
# UNI-HUB -> local Wi-Fi -> Phone + Glass" diagram).
#
# Assumes Raspberry Pi OS Bookworm (or later) on wlan0, which ships with
# NetworkManager by default -- Pi 5 specifically requires Bookworm+, older
# Bullseye/Buster images with dhcpcd+wpa_supplicant don't support this
# hardware at all, so this script doesn't try to support that older stack.
#
# ipv4.method=shared makes NetworkManager run its own DHCP server for
# clients and fixes the Pi's own wlan0 address at 10.42.0.1 -- this is a
# NetworkManager default, not something this script configures by hand.
# glass-app/phone-app's UNI_HUB_HOST build default is set to match; if
# your NetworkManager version picks a different subnet, override via the
# apps' uni_hub_host intent extra (see docs/glass_app.md) instead of
# editing this script.
#
# Usage:
#   sudo UNIKIT_AP_SSID=MySSID UNIKIT_AP_PASSWORD=MyPassword ./setup_ap.sh
# (both env vars are optional; see defaults below)

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "Run as root (sudo) -- NetworkManager connection changes need it." >&2
    exit 1
fi

SSID="${UNIKIT_AP_SSID:-UNIKIT-HUB}"
PASSWORD="${UNIKIT_AP_PASSWORD:-unikit-exam}"
CON_NAME="UniKitHotspot"
IFACE="wlan0"

if [[ ${#PASSWORD} -lt 8 ]]; then
    echo "UNIKIT_AP_PASSWORD must be at least 8 characters (WPA-PSK requirement)." >&2
    exit 1
fi

if ! command -v nmcli >/dev/null 2>&1; then
    echo "nmcli not found -- this script requires NetworkManager (Raspberry Pi OS Bookworm+)." >&2
    exit 1
fi

if nmcli connection show "$CON_NAME" >/dev/null 2>&1; then
    echo "Connection '$CON_NAME' already exists, deleting it first (idempotent re-run)."
    nmcli connection delete "$CON_NAME"
fi

nmcli connection add type wifi ifname "$IFACE" con-name "$CON_NAME" autoconnect yes ssid "$SSID"
nmcli connection modify "$CON_NAME" 802-11-wireless.mode ap 802-11-wireless.band bg
nmcli connection modify "$CON_NAME" wifi-sec.key-mgmt wpa-psk
nmcli connection modify "$CON_NAME" wifi-sec.psk "$PASSWORD"
nmcli connection modify "$CON_NAME" ipv4.method shared

nmcli connection up "$CON_NAME"

echo
echo "AP '$SSID' is up on $IFACE."
echo "Pi's own address on this network (NetworkManager 'shared' default): 10.42.0.1"
echo "Verify with: nmcli -f IP4.ADDRESS device show $IFACE"
echo "Next: install/start the server (see pi/unikit-server.service and pi/README.md)."
