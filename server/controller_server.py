#!/usr/bin/env python3
"""
PhoneController - Virtual Xbox 360 Gamepad Server
=================================================
Makes your phone act as a real Xbox 360 controller on Windows.

One-time setup:
    1. Install ViGEmBus driver (reboot after):
       https://github.com/ViGEm/ViGEmBus/releases  (download ViGEmBusSetup.exe)
    2. pip install vgamepad
    3. Run:  python controller_server.py [port]

The server broadcasts itself over local Wi-Fi so the phone app can
auto-discover this PC (UDP port 5556). You can also connect manually by IP.

Protocol (newline-delimited messages from the phone):
    STICK,L,<x>,<y>      left stick   (-1..1)
    STICK,R,<x>,<y>      right stick  (-1..1)
    XBOX,<BTN>,<0|1>     xbox button  (A B X Y LB RB START BACK LS RS)
    TRIG,<L|R>,<0|1>     trigger button
    KEY,<KEY>,<0|1>      keyboard key (W A S D E Q R F SPACE SHIFT CTRL ...)
    MOUSE,<L|R|M>,<0|1>  mouse button
"""

import socket
import sys
import threading
import time

try:
    import vgamepad as vg
    USE_VGAMEPAD = True
except ImportError:
    USE_VGAMEPAD = False

from ctypes import *
from ctypes.wintypes import *

if sys.platform != 'win32':
    print("This server currently only supports Windows.")
    sys.exit(1)

# --------------------------------------------------------------------------
# Virtual Xbox 360 gamepad (vgamepad / ViGEmBus)
# --------------------------------------------------------------------------

if USE_VGAMEPAD:
    XBOX_MAP = {
        'A': vg.XUSB_BUTTON.XUSB_GAMEPAD_A,
        'B': vg.XUSB_BUTTON.XUSB_GAMEPAD_B,
        'X': vg.XUSB_BUTTON.XUSB_GAMEPAD_X,
        'Y': vg.XUSB_BUTTON.XUSB_GAMEPAD_Y,
        'LB': vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,
        'RB': vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,
        'START': vg.XUSB_BUTTON.XUSB_GAMEPAD_START,
        'BACK': vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK,
        'LS': vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,
        'RS': vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB,
    }
    gamepad = vg.VX360Gamepad()
    gamepad.update()

# --------------------------------------------------------------------------
# Keyboard / mouse emulation (Windows SendInput)
# --------------------------------------------------------------------------

INPUT_MOUSE = 0
INPUT_KEYBOARD = 1
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
KEYEVENTF_KEYUP = 0x0002


class MOUSEINPUT(Structure):
    _fields_ = [("dx", c_long), ("dy", c_long),
                ("mouseData", c_ulong), ("dwFlags", c_ulong),
                ("time", c_ulong), ("dwExtraInfo", POINTER(c_ulong))]


class KEYBDINPUT(Structure):
    _fields_ = [("wVk", c_ushort), ("wScan", c_ushort),
                ("dwFlags", c_ulong), ("time", c_ulong),
                ("dwExtraInfo", POINTER(c_ulong))]


class _INPUTUNION(Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT)]


class INPUT(Structure):
    _fields_ = [("type", c_ulong), ("u", _INPUTUNION)]


user32 = windll.user32


def _send(inputs):
    arr = (INPUT * len(inputs))(*inputs)
    user32.SendInput(len(inputs), byref(arr), sizeof(INPUT))


VK = {
    'W': 0x57, 'A': 0x41, 'S': 0x53, 'D': 0x44,
    'E': 0x45, 'Q': 0x51, 'R': 0x52, 'F': 0x46, 'G': 0x47,
    'SPACE': 0x20, 'SHIFT': 0x10, 'CTRL': 0x11, 'TAB': 0x09,
    'ESC': 0x1B, 'ENTER': 0x0D,
    'UP': 0x26, 'DOWN': 0x28, 'LEFT': 0x25, 'RIGHT': 0x27,
    '1': 0x31, '2': 0x32, '3': 0x33, '4': 0x34, '5': 0x35,
    'F1': 0x70, 'F2': 0x71, 'F3': 0x72, 'F4': 0x73, 'F5': 0x74,
}

held_keys = set()


def key_down(vk):
    _send([INPUT(INPUT_KEYBOARD, _INPUTUNION(ki=KEYBDINPUT(vk, 0, 0, 0, None)))])


def key_up(vk):
    _send([INPUT(INPUT_KEYBOARD, _INPUTUNION(ki=KEYBDINPUT(vk, 0, KEYEVENTF_KEYUP, 0, None)))])


def set_key(name, down):
    vk = VK.get(name)
    if vk is None:
        return
    if down and name not in held_keys:
        held_keys.add(name)
        key_down(vk)
    elif not down and name in held_keys:
        held_keys.discard(name)
        key_up(vk)


def mouse_button(button, down):
    flag_map = {
        ('L', True): MOUSEEVENTF_LEFTDOWN, ('L', False): MOUSEEVENTF_LEFTUP,
        ('R', True): MOUSEEVENTF_RIGHTDOWN, ('R', False): MOUSEEVENTF_RIGHTUP,
        ('M', True): MOUSEEVENTF_MIDDLEDOWN, ('M', False): MOUSEEVENTF_MIDDLEUP,
    }
    flag = flag_map.get((button, down))
    if flag is None:
        return
    _send([INPUT(INPUT_MOUSE, _INPUTUNION(mi=MOUSEINPUT(0, 0, 0, flag, 0, None)))])


def mouse_move(dx, dy):
    if dx or dy:
        _send([INPUT(INPUT_MOUSE, _INPUTUNION(mi=MOUSEINPUT(dx, dy, 0, MOUSEEVENTF_MOVE, 0, None)))])


# --------------------------------------------------------------------------
# Message handling
# --------------------------------------------------------------------------

DEADZONE = 0.18
WASD_KEYS = [
    ('W', lambda x, y: y < -DEADZONE),
    ('S', lambda x, y: y > DEADZONE),
    ('A', lambda x, y: x < -DEADZONE),
    ('D', lambda x, y: x > DEADZONE),
]
MOUSE_SENS = 6


def handle_message(line):
    parts = line.strip().split(',')
    if len(parts) < 2:
        return
    cmd = parts[0]
    try:
        if cmd == 'STICK':
            side = parts[1]
            x = max(-1.0, min(1.0, float(parts[2])))
            y = max(-1.0, min(1.0, float(parts[3])))
            if USE_VGAMEPAD:
                if side == 'L':
                    gamepad.left_joystick_float(x, -y)
                else:
                    gamepad.right_joystick_float(x, -y)
                gamepad.update()
            else:
                if side == 'L':
                    for key, pred in WASD_KEYS:
                        set_key(key, pred(x, y))
                else:
                    mouse_move(int(round(x * MOUSE_SENS)), int(round(-y * MOUSE_SENS)))

        elif cmd == 'XBOX':
            name = parts[1].upper()
            state = int(parts[2]) == 1
            if USE_VGAMEPAD:
                btn = XBOX_MAP.get(name)
                if btn is None:
                    return
                if state:
                    gamepad.press_button(btn)
                else:
                    gamepad.release_button(btn)
                gamepad.update()

        elif cmd == 'TRIG':
            side = parts[1].upper()
            state = int(parts[2]) == 1
            if USE_VGAMEPAD:
                val = 1.0 if state else 0.0
                if side == 'L':
                    gamepad.left_trigger_float(val)
                else:
                    gamepad.right_trigger_float(val)
                gamepad.update()

        elif cmd == 'KEY':
            name = parts[1].upper()
            state = int(parts[2]) == 1
            set_key(name, state)

        elif cmd == 'MOUSE':
            button = parts[1].upper()
            state = int(parts[2]) == 1
            mouse_button(button, state)

    except (ValueError, IndexError):
        pass


def reset_sticks():
    if USE_VGAMEPAD:
        gamepad.left_joystick_float(0, 0)
        gamepad.right_joystick_float(0, 0)
        gamepad.left_trigger_float(0)
        gamepad.right_trigger_float(0)
        gamepad.reset()
        gamepad.update()
    for k in list(held_keys):
        set_key(k, False)
    held_keys.clear()


# --------------------------------------------------------------------------
# Networking
# --------------------------------------------------------------------------

DISCOVERY_PORT = 5556


def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        return s.getsockname()[0]
    except Exception:
        return '127.0.0.1'
    finally:
        s.close()


def beacon_loop(port):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        s.bind(('', 0))
        while True:
            try:
                s.sendto(("PHONECONTROLLER;%d" % port).encode(), ('255.255.255.255', DISCOVERY_PORT))
            except Exception:
                pass
            time.sleep(1)
    except Exception:
        pass
    finally:
        s.close()


def handle_client(conn, addr):
    print(f"[+] Connected: {addr[0]}")
    try:
        conn.settimeout(60)
        buf = b''
        while True:
            try:
                data = conn.recv(4096)
            except socket.timeout:
                reset_sticks()
                print(f"[-] {addr[0]}: timed out (no data for 60s)")
                break
            if not data:
                break
            buf += data
            while b'\n' in buf:
                line, buf = buf.split(b'\n', 1)
                msg = line.decode('utf-8', 'replace').strip()
                if msg:
                    if msg not in ('STICK,L,0.000,0.000', 'STICK,R,0.000,0.000'):
                        print(f"  {addr[0]} -> {msg}", flush=True)
                    handle_message(msg)
    except Exception as e:
        print(f"[!] {addr[0]}: {e}")
    finally:
        reset_sticks()
        conn.close()
        print(f"[-] Disconnected: {addr[0]}")


def main():
    port = 5555
    if len(sys.argv) > 1:
        port = int(sys.argv[1])

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(('0.0.0.0', port))
    srv.listen(5)

    threading.Thread(target=beacon_loop, args=(port,), daemon=True).start()

    mode = "Virtual Xbox 360 (vgamepad)" if USE_VGAMEPAD else "Keyboard + Mouse (install vgamepad for gamepad)"
    print("=" * 55)
    print("  PhoneController Server  v2")
    print(f"  Mode: {mode}")
    print(f"  Port: {port}")
    print(f"  PC IP: {get_local_ip()}")
    print("  The phone app auto-discovers this PC over Wi-Fi.")
    print("  Test: Windows Run -> joy.cpl  (Game Controllers)")
    print("  Button presses will print below, e.g. -> XBOX,A,1")
    print("=" * 55)
    if not USE_VGAMEPAD:
        print()
        print("  For a real gamepad: pip install vgamepad")
        print("  + install ViGEmBus: github.com/ViGEm/ViGEmBus/releases")
        print()

    try:
        while True:
            conn, addr = srv.accept()
            threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        reset_sticks()
        srv.close()


if __name__ == '__main__':
    main()
