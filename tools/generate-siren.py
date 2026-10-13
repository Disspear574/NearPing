"""Generate the original NearPing siren, shared by macOS and Linux."""
from pathlib import Path
import math
import struct
import wave

root = Path(__file__).resolve().parents[1]
rate = 44100
phase = 0.0
frames = bytearray()
for i in range(rate * 2):
    t = i / rate
    frequency = 700 - 220 * math.cos(math.pi * t)
    phase += 2 * math.pi * frequency / rate
    fade = max(0, min(1, t / .06, (2 - t) / .10))
    value = .28 * fade * (math.sin(phase) + .12 * math.sin(2 * phase)) / 1.12
    frames.extend(struct.pack("<h", round(value * 32767)))
for target in ["apps/appleApp/App/Resources/urgent-siren.wav", "apps/desktopApp/src/main/resources/urgent-siren.wav"]:
    path = root / target
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(rate)
        audio.writeframes(frames)
