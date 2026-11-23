"""ZapBridge PC server: fast LAN file transfer between PC and Android.

Run:  python server.py   (shares ~/ZapBridge, prints a PIN for the phone)
"""
import json
import os
import secrets
import socket
import threading
import urllib.parse
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path

HTTP_PORT = 8765
DISCOVERY_PORT = 8766
CHUNK = 1024 * 1024  # 1 MB chunks keep Wi-Fi saturated without big RAM use
SHARE_DIR = Path(os.environ.get("ZAPBRIDGE_DIR", Path.home() / "ZapBridge"))
PIN = os.environ.get("ZAPBRIDGE_PIN") or f"{secrets.randbelow(10**6):06d}"


def safe_path(name: str) -> Path | None:
    """Only plain file names inside SHARE_DIR; blocks ../ and absolute paths."""
    name = Path(urllib.parse.unquote(name)).name
    if not name or name in (".", ".."):
        return None
    return SHARE_DIR / name


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _authed(self) -> bool:
        if secrets.compare_digest(self.headers.get("X-PIN", ""), PIN):
            return True
        self._send(401, b"bad pin")
        return False

    def _send(self, code: int, body: bytes, ctype: str = "text/plain"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not self._authed():
            return
        if self.path == "/files":
            files = [{"name": p.name, "size": p.stat().st_size}
                     for p in sorted(SHARE_DIR.iterdir()) if p.is_file()]
            return self._send(200, json.dumps(files).encode(), "application/json")
        if self.path.startswith("/files/"):
            p = safe_path(self.path[len("/files/"):])
            if not p or not p.is_file():
                return self._send(404, b"not found")
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(p.stat().st_size))
            self.end_headers()
            with p.open("rb") as f:
                while chunk := f.read(CHUNK):
                    self.wfile.write(chunk)
            return
        self._send(404, b"not found")

    def do_PUT(self):
        if not self._authed():
            return
        p = safe_path(self.path[len("/files/"):]) if self.path.startswith("/files/") else None
        length = int(self.headers.get("Content-Length", -1))
        if not p or length < 0:
            return self._send(400, b"need /files/<name> and Content-Length")
        tmp = p.with_name(p.name + ".part")
        with tmp.open("wb") as f:
            left = length
            while left > 0:
                chunk = self.rfile.read(min(CHUNK, left))
                if not chunk:
                    break
                f.write(chunk)
                left -= len(chunk)
        if left:  # connection dropped: don't leave a truncated file posing as complete
            tmp.unlink(missing_ok=True)
            return self._send(400, b"incomplete upload")
        tmp.replace(p)
        print(f"  <- received {p.name} ({length:,} bytes)")
        self._send(201, b"ok")

    def log_message(self, *args):
        pass


def discovery():
    """Answer phone's UDP broadcast 'ZAPBRIDGE?' with our name and HTTP port."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.bind(("", DISCOVERY_PORT))
    reply = json.dumps({"name": socket.gethostname(), "port": HTTP_PORT}).encode()
    while True:
        data, addr = s.recvfrom(64)
        if data == b"ZAPBRIDGE?":
            s.sendto(reply, addr)


MIC_PORT = 8767
MIC_RATE = 48000  # phone sends 16-bit mono PCM at this rate
MIC_MAX_BUF = MIC_RATE * 2 // 10  # >100 ms queued -> drop old audio so latency can't creep up


def mic_device():
    """Output device that Windows apps see as a mic: VB-Cable's 'CABLE Input'."""
    import sounddevice as sd
    want = os.environ.get("ZAPBRIDGE_MIC_DEVICE", "CABLE Input")
    for i, d in enumerate(sd.query_devices()):
        if want.lower() in d["name"].lower() and d["max_output_channels"] > 0:
            return i
    return None


def mic_client(conn: socket.socket):
    import sounddevice as sd
    with conn:
        conn.settimeout(5)
        head = b""  # MSG_WAITALL isn't allowed with a timeout on Windows, so loop
        while len(head) < 7 and (part := conn.recv(7 - len(head))):
            head += part
        if not secrets.compare_digest(head, (PIN + "\n").encode()):
            return
        conn.settimeout(None)
        dev = mic_device()
        if dev is None:
            print("  ! mic: 'CABLE Input' device not found (install VB-Cable)")
            return
        buf, lock = bytearray(), threading.Lock()

        def play(out, frames, _time, _status):
            n = frames * 2
            with lock:
                chunk = bytes(buf[:n])
                del buf[:n]
            out[:] = chunk + b"\0" * (n - len(chunk))  # underrun -> silence, not a glitch

        print("  mic: phone microphone connected -> 'CABLE Output'")
        with sd.RawOutputStream(MIC_RATE, channels=1, dtype="int16", device=dev,
                                latency="low", callback=play):
            while data := conn.recv(4096):
                with lock:
                    buf += data
                    if len(buf) > MIC_MAX_BUF:
                        del buf[:len(buf) - MIC_MAX_BUF // 3 & ~1]  # keep sample alignment
        print("  mic: disconnected")


def mic_server():
    s = socket.create_server(("0.0.0.0", MIC_PORT))
    while True:
        conn, _ = s.accept()
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        threading.Thread(target=mic_client, args=(conn,), daemon=True).start()


def main():
    SHARE_DIR.mkdir(parents=True, exist_ok=True)
    threading.Thread(target=discovery, daemon=True).start()
    threading.Thread(target=mic_server, daemon=True).start()
    print(f"ZapBridge ready. Shared folder: {SHARE_DIR}")
    print(f"PIN for the phone: {PIN}")
    ThreadingHTTPServer(("0.0.0.0", HTTP_PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
