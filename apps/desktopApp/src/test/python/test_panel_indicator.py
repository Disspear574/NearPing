import contextlib
import io
import runpy
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[2] / "main/resources/nearping-indicator.py"


class PanelTest(unittest.TestCase):
    def scenario(self, initially_embedded, exercise):
        state = {"embedded": initially_embedded, "callbacks": {}, "timers": {}, "items": []}
        output = io.StringIO()

        class Tray:
            @staticmethod
            def new_from_file(_):
                state["tray"] = Tray()
                return state["tray"]
            def set_title(self, _): pass
            def set_tooltip_text(self, _): pass
            def connect(self, name, callback): state["callbacks"][name] = callback
            def set_visible(self, visible): state["visible"] = visible
            def is_embedded(self): return state["embedded"]

        class Item:
            def __init__(self, label): self.label = label
            def connect(self, _, callback): self.callback = callback

        class Menu:
            def append(self, item): state["items"].append(item)
            def show_all(self): pass

        class Indicator:
            @staticmethod
            def new(*_): return Indicator()
            def set_title(self, _): pass
            def set_menu(self, _): pass
            def connect(self, _, callback): state["fallback_callback"] = callback
            def set_status(self, value): state["fallback_status"] = value

        def timer(_, callback):
            state["timers"][1] = callback
            return 1

        Gtk = types.SimpleNamespace(StatusIcon=Tray, Menu=Menu, MenuItem=Item,
                                    main=lambda: exercise(state), main_quit=lambda: state.update(quit=True))
        GLib = types.SimpleNamespace(IO_IN=1, IO_HUP=2, IO_ERR=4,
            timeout_add_seconds=timer, source_remove=lambda key: state["timers"].pop(key),
            io_add_watch=lambda source, flags, callback: state.update(stdin=callback))
        App = types.SimpleNamespace(Indicator=Indicator,
            IndicatorCategory=types.SimpleNamespace(APPLICATION_STATUS=1),
            IndicatorStatus=types.SimpleNamespace(ACTIVE=1, PASSIVE=0))
        gi = types.ModuleType("gi")
        gi.require_version = lambda *_: None
        repo = types.ModuleType("gi.repository")
        repo.Gtk, repo.GLib, repo.AyatanaAppIndicator3 = Gtk, GLib, App
        with patch.dict(sys.modules, {"gi": gi, "gi.repository": repo}), \
             patch.object(sys, "argv", [str(SCRIPT), "icon.png", "Open", "Pause", "Resume", "Quit"]), \
             contextlib.redirect_stdout(output):
            runpy.run_path(str(SCRIPT), run_name="__main__")
        return state, output.getvalue().splitlines()

    def test_primary_click_opens_app_without_menu(self):
        def exercise(s):
            self.assertEqual(s["items"], [])
            s["callbacks"]["activate"](s["tray"])
            s["callbacks"]["popup-menu"](s["tray"], 3, 0)
            self.assertFalse(s["stdin"](None, 2))
        state, events = self.scenario(True, exercise)
        self.assertEqual(events, ["READY", "OPEN", "OPEN"])
        self.assertFalse(state["visible"])
        self.assertTrue(state["quit"])

    def test_missing_host_falls_back_and_parent_cleanup(self):
        def exercise(s):
            s["timers"].pop(1)()
            s["fallback_callback"](None, True)
            for item in s["items"]: item.callback(item)
            s["fallback_callback"](None, False)
            s["stdin"](None, 2)
        state, events = self.scenario(False, exercise)
        self.assertEqual(events, ["READY", "OPEN", "QUIT", "UNAVAILABLE"])
        self.assertEqual(state["fallback_status"], 0)
        self.assertTrue(state["quit"])

    def test_host_loss_restores_app_and_reembedding_cancels_fallback(self):
        def exercise(s):
            s["embedded"] = False
            s["callbacks"]["notify::embedded"]()
            self.assertTrue(s["timers"])
            s["embedded"] = True
            s["callbacks"]["notify::embedded"]()
            self.assertFalse(s["timers"])
        _, events = self.scenario(True, exercise)
        self.assertEqual(events, ["READY", "UNAVAILABLE", "READY"])


if __name__ == "__main__":
    unittest.main()
