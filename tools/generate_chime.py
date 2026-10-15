"""Regenerate Agnihotra's original two-note chime using only Python's standard library.

Optional asset-maintenance tool; Android builds and playback do not require Python.
No third-party recordings or samples are used.
"""

from array import array
import math
from pathlib import Path
import sys
import wave


SAMPLE_RATE = 44_100
DURATION_SECONDS = 2.8
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/agnihotra_chime.wav"


def note(t: float, frequency: float) -> float:
    if t < 0:
        return 0.0
    attack = min(1.0, t / 0.012)
    attack = math.sin(attack * math.pi / 2) ** 2
    partials = ((1.0, 1.0, 0.65), (2.0, 0.20, 0.35), (3.01, 0.065, 0.22))
    return attack * sum(
        amplitude * math.exp(-t / decay) * math.sin(2 * math.pi * frequency * ratio * t)
        for ratio, amplitude, decay in partials
    )


def main() -> None:
    samples = []
    for index in range(round(SAMPLE_RATE * DURATION_SECONDS)):
        t = index / SAMPLE_RATE
        # A4 then E5: two rounded strikes with a gently overlapping decay.
        value = note(t, 440.0) * 0.82 + note(t - 0.46, 659.255) * 0.88
        tail = min(1.0, (DURATION_SECONDS - t) / 0.15)
        samples.append(value * math.sin(tail * math.pi / 2) ** 2)
    peak = max(abs(value) for value in samples)
    pcm = array("h", (round(value / peak * 0.55 * 32767) for value in samples))
    if sys.byteorder != "little":
        pcm.byteswap()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(SAMPLE_RATE)
        audio.writeframes(pcm.tobytes())
    print(f"Generated {OUTPUT.name}: {DURATION_SECONDS}s, mono PCM16, peak 55%")


if __name__ == "__main__":
    main()
