# PhoneController

Turn your Android phone into a wireless Xbox 360 controller for Windows.

## Features

- **Real virtual gamepad** — uses [vgamepad](https://github.com/ViGEm/vgamepad) + the ViGEmBus driver, so Windows sees an "Xbox 360 Controller for Windows" that works with any XInput game
- **Dynamic floating joysticks** — the stick base appears wherever you place your thumb; left half of the screen = left stick, right half = right stick (both can be used at the same time)
- **Full button set** — A, B, X, Y, LB, RB, LT, RT, Start, Select
- **Button remapping** — map any on-screen button to any Xbox button, keyboard key, or mouse button
- **Auto-discovery** — the PC server broadcasts itself over local Wi-Fi; the app lists nearby controllers automatically (manual IP entry also supported)
- **Keyboard/mouse fallback** — works without a gamepad driver too

## How it works

The phone connects to the PC over your local Wi-Fi via TCP. The PC server converts the phone's input into a virtual Xbox 360 controller:

```
Phone (Android app)  --Wi-Fi/TCP-->  PC server (Python)  --ViGEmBus-->  Virtual Xbox 360 Controller
```

## Setup (PC)

1. Install the ViGEmBus driver (reboot after): https://github.com/ViGEm/ViGEmBus/releases
2. Install vgamepad:
   ```
   pip install vgamepad
   ```
3. Run the server:
   ```
   python server/controller_server.py
   ```

## Setup (phone)

1. Install `PhoneController.apk` (build it from `app/`, or use the debug APK from a Gradle build)
2. Make sure the phone and PC are on the same Wi-Fi network
3. Open the app — it should list your PC automatically. If not, enter the PC's IP manually and tap Connect.
4. Test with `joy.cpl` (Windows Run) — you should see "Xbox 360 Controller for Windows"

## Button remapping

Tap **Remap** on the controller screen to assign any on-screen button to:
- Any Xbox button (including LT/RT triggers)
- A keyboard key (W, A, S, D, E, Q, R, F, Space, Shift, Ctrl, Tab, Esc, Enter, arrows, 1-5, F1-F5)
- A mouse button (left, right, middle)

## Network protocol

Newline-delimited messages over TCP:

```
STICK,L,<x>,<y>      left stick   (-1..1)
STICK,R,<x>,<y>      right stick  (-1..1)
XBOX,<BTN>,<0|1>     xbox button  (A B X Y LB RB START BACK LS RS)
TRIG,<L|R>,<0|1>     trigger button
KEY,<KEY>,<0|1>      keyboard key
MOUSE,<L|R|M>,<0|1>  mouse button
```

The server also broadcasts a discovery beacon (`PHONECONTROLLER;<port>`) via UDP to port 5556 every second so the app can find it automatically.
