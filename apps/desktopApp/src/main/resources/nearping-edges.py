#!/usr/bin/python3
import sys
import gi
import cairo

gi.require_version("Gtk", "3.0")
gi.require_version("Gdk", "3.0")
from gi.repository import Gtk, Gdk, GLib


def draw(window, cr):
    width, height = window.get_size()
    cr.set_operator(cairo.OPERATOR_SOURCE)
    cr.set_source_rgba(0, 0, 0, 0)
    cr.paint()
    cr.set_operator(cairo.OPERATOR_OVER)
    for x0, y0, x1, y1, rect in [
        (0, 0, 0, 36, (0, 0, width, 36)),
        (0, height, 0, height-36, (0, height-36, width, 36)),
        (0, 0, 36, 0, (0, 0, 36, height)),
        (width, 0, width-36, 0, (width-36, 0, 36, height)),
    ]:
        gradient = cairo.LinearGradient(x0, y0, x1, y1)
        gradient.add_color_stop_rgba(0, .85, .08, .06, .65)
        gradient.add_color_stop_rgba(1, .85, .08, .06, 0)
        cr.set_source(gradient)
        cr.rectangle(*rect)
        cr.fill()
    cr.set_source_rgba(.85, .08, .06, .8)
    cr.set_line_width(2)
    cr.rectangle(1, 1, width-2, height-2)
    cr.stroke()
    return False


def main():
    display = Gdk.Display.get_default()
    if display is None:
        return
    windows = []
    for i in range(display.get_n_monitors()):
        geometry = display.get_monitor(i).get_geometry()
        window = Gtk.Window(type=Gtk.WindowType.TOPLEVEL)
        visual = window.get_screen().get_rgba_visual()
        if visual is None or not window.get_screen().is_composited():
            continue
        window.set_visual(visual)
        window.set_decorated(False)
        window.set_app_paintable(True)
        window.set_accept_focus(False)
        window.set_focus_on_map(False)
        window.set_skip_taskbar_hint(True)
        window.set_skip_pager_hint(True)
        window.set_keep_above(True)
        window.set_type_hint(Gdk.WindowTypeHint.NOTIFICATION)
        window.move(geometry.x, geometry.y)
        window.set_default_size(geometry.width, geometry.height)
        window.connect("draw", draw)
        window.realize()
        # Install click-through BEFORE mapping: never leave an input-blocking overlay.
        window.get_window().input_shape_combine_region(cairo.Region(), 0, 0)
        window.show_all()
        windows.append(window)
    if not windows:
        return
    def close(*args):
        for window in windows:
            window.destroy()
        Gtk.main_quit()
        return False
    GLib.timeout_add(8000, close)
    GLib.io_add_watch(sys.stdin, GLib.IO_HUP | GLib.IO_ERR, close)
    Gtk.main()


if __name__ == "__main__":
    main()
