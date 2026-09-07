#!/bin/sh
# Restart systemd-networkd if the lima0 (socket_vmnet) interface lost its
# DHCPv4 lease. Common after macOS sleep: the lease silently goes away and
# the host can no longer reach the lxdbr0 subnet through lima0.
set -eu

iface=lima0

[ -d "/sys/class/net/$iface" ] || exit 0

if ip -4 -o addr show dev "$iface" | grep -q 'inet '; then
  exit 0
fi

logger -t lima0-dhcp-recover "lima0 has no IPv4 lease; restarting systemd-networkd"
systemctl restart systemd-networkd
