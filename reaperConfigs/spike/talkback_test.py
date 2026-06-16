#!/usr/bin/env python3
"""
ReaCue talkback test harness.

Validates Components 1 + 2 (ReaCue.eel ingest + Talkback JSFX) without any
phone / BLE / macOS app. It connects to the ReaCue.eel TCP server and injects a
synthetic 16 kHz mono talkback stream (a sine tone), exactly as the macOS
peripheral eventually will.

Protocol (must match ReaCue.eel):
  Header (6 bytes): schemaVersion=0 (u8), messageType (u8), payloadSize (u32 BIG-endian)
  TALKBACK_START (13): payload = talkerId (u8)
  TALKBACK_AUDIO (14): payload = talkerId (u8), sampleCount (u16 BIG-endian),
                       int16 PCM samples (LITTLE-endian)
  TALKBACK_STOP  (15): payload = talkerId (u8)

Usage:
  python3 talkback_test.py [--talker 0] [--freq 440] [--port 9007] [--secs 0]
  (secs=0 runs until Ctrl-C)

Then in REAPER: ReaCue.eel running, "ReaCue Talkback.jsfx" on a track routed to
your output. You should hear a steady tone while this runs.
"""

import argparse
import math
import socket
import struct
import threading
import time

SCHEMA = 0
MSG_TALKBACK_START = 13
MSG_TALKBACK_AUDIO = 14
MSG_TALKBACK_STOP = 15

INPUT_RATE = 16000
FRAME_SAMPLES = 320  # 20 ms at 16 kHz
FRAME_SECONDS = FRAME_SAMPLES / INPUT_RATE


def frame(msg_type: int, payload: bytes) -> bytes:
    return struct.pack(">BBI", SCHEMA, msg_type, len(payload)) + payload


def drain(sock: socket.socket, stop: threading.Event) -> None:
    # ReaCue.eel pushes project state to every client; read and discard so the
    # server's send buffer never backs up during the test.
    sock.settimeout(0.5)
    while not stop.is_set():
        try:
            if not sock.recv(4096):
                break
        except socket.timeout:
            continue
        except OSError:
            break


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=9007)
    ap.add_argument("--talker", type=int, default=0)
    ap.add_argument("--freq", type=float, default=440.0)
    ap.add_argument("--amp", type=float, default=0.3, help="0..1 amplitude")
    ap.add_argument("--secs", type=float, default=0.0, help="0 = until Ctrl-C")
    args = ap.parse_args()

    sock = socket.create_connection((args.host, args.port))
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    stop = threading.Event()
    threading.Thread(target=drain, args=(sock, stop), daemon=True).start()

    sock.sendall(frame(MSG_TALKBACK_START, struct.pack(">B", args.talker)))
    print(f"Streaming {args.freq:.0f} Hz tone as talker {args.talker} "
          f"(Ctrl-C to stop)...")

    phase = 0.0
    step = 2 * math.pi * args.freq / INPUT_RATE
    peak = int(args.amp * 32767)
    next_t = time.perf_counter()
    start = next_t
    try:
        while True:
            samples = bytearray()
            for _ in range(FRAME_SAMPLES):
                samples += struct.pack("<h", int(peak * math.sin(phase)))
                phase += step
            payload = struct.pack(">BH", args.talker, FRAME_SAMPLES) + bytes(samples)
            sock.sendall(frame(MSG_TALKBACK_AUDIO, payload))

            if args.secs and (time.perf_counter() - start) >= args.secs:
                break

            # Pace at real time so we emulate a live mic feed.
            next_t += FRAME_SECONDS
            sleep = next_t - time.perf_counter()
            sleep > 0 and time.sleep(sleep)
    except KeyboardInterrupt:
        pass
    finally:
        try:
            sock.sendall(frame(MSG_TALKBACK_STOP, struct.pack(">B", args.talker)))
        except OSError:
            pass
        stop.set()
        time.sleep(0.1)
        sock.close()
        print("\nStopped.")


if __name__ == "__main__":
    main()
