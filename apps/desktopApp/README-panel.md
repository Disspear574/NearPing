# Ubuntu panel

The primary panel icon uses GTK StatusIcon (XEmbed) in an isolated helper with `GDK_BACKEND=x11`. A click opens the compact NearPing window near the pointer: contacts, prominent Ping, urgent action under the contact menu, and an internal Settings screen. Escape, close, or outside focus hides it when the icon is available. Closing without a usable panel minimizes the app; Quit exits.

Ubuntu needs an enabled AppIndicator/legacy-tray extension and XWayland. No extension or desktop setting is changed automatically. If XEmbed is unavailable after four seconds, the helper falls back to Ayatana AppIndicator with Open NearPing and Quit. That host still needs the Open menu action; single-click activation cannot be guaranteed across shells. A missing host or helper failure restores the window.

Dependencies: python3, python3-gi, gir1.2-gtk-3.0, gir1.2-ayatanaappindicator3-0.1, libnotify-bin; xwayland and gnome-shell-extension-appindicator are recommended.

`python3 apps/desktopApp/src/test/python/test_panel_indicator.py` checks activation, fallback, host loss/recovery, and parent-pipe cleanup with GTK doubles. Real Ubuntu/XWayland focus and panel placement need device validation.
