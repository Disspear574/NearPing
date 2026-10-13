#!/usr/bin/python3
"""One-click XEmbed tray with an AppIndicator fallback when embedding is unavailable."""
import sys
import gi

gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, GLib


def emit(event):
    try:
        print(event, flush=True)
    except BrokenPipeError:
        Gtk.main_quit()


def main():
    icon, open_label, pause_label, resume_label, quit_label = sys.argv[1:]
    state = {"fallback": None, "ready": False, "timer": 0}
    tray = Gtk.StatusIcon.new_from_file(icon)
    tray.set_title("NearPing")
    tray.set_tooltip_text("NearPing")
    tray.connect("activate", lambda _: emit("OPEN"))
    tray.connect("popup-menu", lambda *_: emit("OPEN"))

    def fallback():
        state["timer"] = 0
        if tray.is_embedded() or state["fallback"]:
            return False
        try:
            gi.require_version("AyatanaAppIndicator3", "0.1")
            from gi.repository import AyatanaAppIndicator3 as Indicator
            indicator = Indicator.Indicator.new("nearping", icon, Indicator.IndicatorCategory.APPLICATION_STATUS)
            indicator.set_title("NearPing")
            menu = Gtk.Menu()
            for label, event in [(open_label, "OPEN"), (quit_label, "QUIT")]:
                item = Gtk.MenuItem(label=label)
                item.connect("activate", lambda _, event=event: emit(event))
                menu.append(item)
            menu.show_all()
            indicator.set_menu(menu)
            indicator.connect("connection-changed", lambda _, connected:
                              emit("READY" if connected else "UNAVAILABLE"))
            state["fallback"] = indicator
            indicator.set_status(Indicator.IndicatorStatus.ACTIVE)
            tray.set_visible(False)
        except Exception as error:
            print(f"NearPing indicator fallback: {error}", file=sys.stderr)
            emit("UNAVAILABLE")
        return False

    def embedding_changed(*_):
        if state["fallback"]:
            return
        ready = tray.is_embedded()
        if ready != state["ready"]:
            state["ready"] = ready
            emit("READY" if ready else "UNAVAILABLE")
        if ready and state["timer"]:
            GLib.source_remove(state["timer"])
            state["timer"] = 0
        elif not ready and not state["timer"]:
            state["timer"] = GLib.timeout_add_seconds(4, fallback)

    tray.connect("notify::embedded", embedding_changed)
    tray.set_visible(True)
    embedding_changed()

    def parent_input(source, condition):
        if condition & (GLib.IO_HUP | GLib.IO_ERR) or not sys.stdin.readline():
            tray.set_visible(False)
            if state["fallback"]:
                from gi.repository import AyatanaAppIndicator3 as Indicator
                state["fallback"].set_status(Indicator.IndicatorStatus.PASSIVE)
            Gtk.main_quit()
            return False
        return True
    GLib.io_add_watch(sys.stdin, GLib.IO_IN | GLib.IO_HUP | GLib.IO_ERR, parent_input)
    Gtk.main()


if __name__ == "__main__":
    main()
