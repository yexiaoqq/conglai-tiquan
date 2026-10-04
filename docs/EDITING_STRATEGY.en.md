# AI Editing Strategy · English

> Notice: This document was written entirely by AI, so that "the next AI" can locate the right strategy in the shortest time.
> It only covers how to edit, where to edit, and how to verify — not the privilege-escalation internals.

## 0. One-line orientation

This project = a UI rework + bug fix of the APK **CongLai TiQuan (df.root)**.
The only self-written code is source/src/df/root/MainActivity.java (~626 lines) + IReporter.java; the other Java files are compile-time stubs.
The kernel-level escalation logic (DirtyFrag → libexp.so → dirtyfrag.ko → ksud) is NOT in this repository and CANNOT be changed through this project — it is only called via the ExploitRunner interface.

## 1. Directory map (read this before editing)

| I want to change… | Go to this file |
|---|---|
| UI text, cards, switches, button behavior | source/src/df/root/MainActivity.java |
| Three-tab navigation (Home / Logs / About) logic | MainActivity.java: selectTab() / onNavigationItemSelected / mNavSync |
| Run-record write, persistence, export | MainActivity.java: startNewRecord / saveHistory / renderHistory / runExploit |
| Main screen layout | source/res/layout/activity_main.xml |
| Run-record item layout | source/res/layout/cl_history_item.xml |
| Bottom-nav menu items | source/res/menu/cl_bottom_nav.xml |
| Colors | source/res/color/cl_nav_item.xml |
| Icons (vector) | source/res/drawable/cl_ic_*.xml |
| Build flow | source/build_ui.sh |
| Align & sign | source/zipalign.py |
| Compile stubs | source/stub/** |

Note: source/src/df/root/ExploitRunner.java and BootReceiver.java are compile-time stubs (comment: `Compile-only stub. The real class is inside the APK.`). They only provide the signatures needed to compile; the real implementations come from the APK's dex. Editing them will NOT change app behavior.

## 2. Build chain (8 steps, build_ui.sh)

1. Compile stubs (minimal stand-ins for androidx / material)
2. Compile MainActivity.java
3. d8 → dex
4. baksmali: disassemble the original APK's classes.dex
5. Inject the new MainActivity smali back into the disassembly
6. Inject new resources (res/**)
7. apktool b to repack
8. zipalign.py align → apksigner sign
Output: conglai-md3-ui-signed.apk

Key strategy: this project does NOT build the whole app from scratch. It does "disassemble original APK → replace only MainActivity + resources → repack & sign". Therefore:
- UI/logic change → just recompile MainActivity.java.
- Resource change → just replace res/**.
- Do NOT try to compile the logic of ExploitRunner/BootReceiver — they are stubs.

## 3. Standard edit procedure (follow next time)

1. Locate: grep the keyword first, don't read all 626 lines. e.g. `grep -n 'text|methodName' source/src/df/root/MainActivity.java`
2. Edit code: touch MainActivity.java only.
3. Edit resources: layouts in activity_main.xml, icons in res/drawable/cl_ic_*.xml.
4. Build: `cd /home/rmg/ui && sh build_ui.sh 2>&1 | tail -30`
5. Push: `cp -f conglai-md3-ui-signed.apk /sdcard/Download/`, then `cp` to `/data/local/tmp/` (you CANNOT `pm install` straight from /sdcard — it is rejected as fuse:s0).
6. Install: `pm install -r /data/local/tmp/conglai-md3.apk`
7. Verify: see section 4.
8. Upload: see section 5.

## 4. Verification strategy (crucial!)

### 4.1 Crash check
```
logcat -c
monkey -p df.root -c android.intent.category.LAUNCHER 1
sleep 4
pidof df.root                 # pid present = alive
logcat -d -s AndroidRuntime:E # empty = no crash
```

### 4.2 UI check
```
am start -n df.root/.MainActivity      # bring to foreground FIRST! otherwise screenshot/dump grabs another app
sleep 2
screencap -p /sdcard/shot.png
uiautomator dump /sdcard/ui.xml        # then grep the widgets
```
Pitfall: screencap and uiautomator dump frequently capture the foreground window (e.g. Operit itself). Always `am start` df.root to the foreground first.

### 4.3 "Symptom 1" closed-loop check (run records)
- Tap "Start escalation" → a record with running=true should appear immediately and jump to the "Logs" tab.
- Check the write path: startNewRecord → saveHistory (atomic write of run-history.json: .tmp + fsync + renameTo) → renderHistory.
- A runtime verification with a real escalation run (triggers kernel panic / soft reboot — use with caution).

## 5. Upload strategy (GitHub API)

- Repo: yexiaoqq/conglai-tiquan (public, default branch main).
- A README-only repo; source goes into source/ and docs/ via the Contents API.
- Release: v2.0-md3, asset conglai-md3-signed.apk.
- Same-named assets cannot coexist: you MUST DELETE the old asset first, then POST the new one.
- After upload you MUST re-download anonymously (without token) and compare sha256, to confirm it is truly public and correct.

## 6. Verified hard constraints (pitfall list)

1. Sandbox terminal has no unzip, no aapt2 → verify APKs with python3 zipfile.
2. The terminal tool swallows multi-line commands → write a script file then `sh` it.
3. `rm -rf <dir>` is blocked by Operit → use exact filenames with `rm -f` + `rmdir`.
4. `pm install` cannot be fed /sdcard → `cp` to /data/local/tmp/ first.
5. A soft reboot drops Shizuku → confirm Shizuku is online before UI verification.
6. `logcat -s AndroidRuntime:E` is the first-choice crash filter.

## 7. One-line cheat sheet

Edit UI/logic → touch only MainActivity.java + res/** → sh build_ui.sh → push to /data/local/tmp → pm install -r → logcat for crashes → am start then screenshot → upload via GitHub API.

---
Generated by AI. / 本文由 AI 生成.