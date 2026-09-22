# Discovery network selection

The profile screen offers Automatic (LAN) or an active IPv4 interface, including container bridges and VPN/tunnel adapters. Names and addresses are shown, with a warning for adapters that do not advertise multicast support. Reopening the menu refreshes the list. The current bound interface is shown below the selector.

Selection is saved by interface name in `network-interface.txt` next to the Linux identity/contacts data, not by a temporary DHCP address. Switching restarts Bonjour/mDNS discovery on the selected interface and discards callbacks from the previous discovery instance. Existing authenticated TCP connections are retained. This is discovery routing, not firewall isolation.

Automatic mode keeps excluding known container/VPN adapters. Manual mode explicitly permits them. If a selected adapter disappears, discovery shows a failure rather than silently switching to a different network; selecting Automatic restores automatic selection. Selecting the same adapter retries startup.

A VPN must carry multicast for mDNS discovery; selecting WireGuard/Tailscale or another tunnel does not add multicast support to that network. Direct IP connection is not implemented here.

Validation: selector tests cover manual container/tunnel selection, automatic exclusion, and missing adapter without fallback; preference tests cover restart persistence, Automatic reset, and rejected invalid values preserving prior settings. Ubuntu UI, actual VPN discovery and adapter switching still require device testing.
