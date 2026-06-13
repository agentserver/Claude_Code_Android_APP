#!/usr/bin/env python3
import argparse
import base64
import json
import re
import subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def run_adb(serial, args, capture=True):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    return subprocess.run(
        cmd,
        check=True,
        stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )


def shell(serial, args, capture=True):
    return run_adb(serial, ["shell"] + args, capture=capture)


def parse_current_activity(raw):
    text = raw.decode("utf-8", errors="replace")
    match = re.search(r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^/\s]+)/([^\s}]+)", text)
    if not match:
        match = re.search(r"mFocusedApp=ActivityRecord\{[^ ]+ [^ ]+ ([^/\s]+)/([^\s}]+)", text)
    if not match:
        return {"package": "", "activity": "", "raw": text[-800:]}
    return {"package": match.group(1), "activity": match.group(2), "raw": text[-800:]}


def encode_input_text(value):
    return str(value).replace("%", "%25").replace(" ", "%s")


class Handler(BaseHTTPRequestHandler):
    server_version = "AndroidAdbCompanion/0.1"

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length).decode("utf-8") if length else "{}"
            args = json.loads(body or "{}")
            action = self.path.removeprefix("/adb/").strip("/")
            result = self.server.dispatch(action, args)
            self.write_json(200, result)
        except Exception as exc:
            self.write_json(500, {"ok": False, "message": str(exc)})

    def log_message(self, fmt, *args):
        if self.server.verbose:
            super().log_message(fmt, *args)

    def write_json(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class CompanionServer(ThreadingHTTPServer):
    def __init__(self, addr, serial, verbose=False):
        super().__init__(addr, Handler)
        self.serial = serial
        self.verbose = verbose

    def dispatch(self, action, args):
        if action == "status":
            activity = self.current_activity()
            return {
                "ok": True,
                "serial": self.serial or "default",
                "package": activity.get("package", ""),
                "activity": activity.get("activity", ""),
                "message": "ADB Companion online",
            }
        if action == "current_activity":
            activity = self.current_activity()
            activity["ok"] = True
            return activity
        if action == "screenshot":
            proc = run_adb(self.serial, ["exec-out", "screencap", "-p"])
            return {
                "ok": True,
                "mime_type": "image/png",
                "image_base64": base64.b64encode(proc.stdout).decode("ascii"),
                "message": "screenshot captured",
            }
        if action == "tap":
            shell(self.serial, ["input", "tap", str(int(args["x"])), str(int(args["y"]))], capture=False)
            return {"ok": True, "message": f"tapped at ({int(args['x'])}, {int(args['y'])})"}
        if action == "swipe":
            duration = int(args.get("duration_ms", 300))
            shell(self.serial, [
                "input", "swipe",
                str(int(args["x1"])), str(int(args["y1"])),
                str(int(args["x2"])), str(int(args["y2"])),
                str(duration),
            ], capture=False)
            return {"ok": True, "message": "swiped"}
        if action == "input_text":
            shell(self.serial, ["input", "text", encode_input_text(args.get("text", ""))], capture=False)
            return {"ok": True, "message": "text input sent"}
        if action == "keyevent":
            shell(self.serial, ["input", "keyevent", str(args["keycode"])], capture=False)
            return {"ok": True, "message": f"keyevent {args['keycode']} sent"}
        return {"ok": False, "message": f"unknown action: {action}"}

    def current_activity(self):
        proc = shell(self.serial, ["dumpsys", "window"])
        return parse_current_activity(proc.stdout)


def main():
    parser = argparse.ArgumentParser(description="ADB companion server for Android MCP adb.* tools")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18765)
    parser.add_argument("--serial", default="", help="ADB device serial. Leave empty to use adb default.")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    server = CompanionServer((args.host, args.port), args.serial, args.verbose)
    print(f"ADB Companion listening on http://{args.host}:{args.port}")
    print(f"Run: adb reverse tcp:{args.port} tcp:{args.port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
