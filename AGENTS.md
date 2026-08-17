# Agent conventions (P-Music)

## On-device verification
- adb is not on PATH: use `"$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`.
- Test device: moto g32, serial `ZD222BXJW2` (re-connect + approve USB debugging if `adb devices` is empty).
- App package: `com.prakash.pmusic`; launcher activity `.MainActivity`.

## Temp files on the device
- NEVER write test dumps to `/sdcard/` — it pollutes the user-visible storage.
- The app cache dir is NOT writable by `adb shell uiautomator dump` (run-as + uiautomator fails; `/sdcard/Android/data/<pkg>/cache` is FUSE-restricted on Android 11+).
- Use the shell temp area instead and clean up afterwards:
  - UI dumps: `adb shell uiautomator dump /data/local/tmp/ui.xml`, read with `adb shell cat /data/local/tmp/ui.xml`, then `adb shell rm -f /data/local/tmp/ui.xml`.
  - Screenshots: `adb exec-out screencap -p > <local-file>.png` (keeps files on the PC, not the device).

## Device quirks
- While a song is playing, `uiautomator dump` can fail with "could not get idle state" because the progress tick never lets the UI idle. Pause first (`adb shell input keyevent 127`), dump, then resume (`adb shell input keyevent 126`).
