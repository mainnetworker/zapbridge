"""Self-check: python test_server.py  (starts server on a temp dir, round-trips a file)."""
import json, os, socket, tempfile, threading, time, urllib.request

tmp = tempfile.mkdtemp()
os.environ["ZAPBRIDGE_DIR"], os.environ["ZAPBRIDGE_PIN"] = tmp, "123456"
import server  # noqa: E402

threading.Thread(target=server.main, daemon=True).start()
time.sleep(0.5)
base = f"http://127.0.0.1:{server.HTTP_PORT}/files"


def req(url, method="GET", data=None, pin="123456"):
    r = urllib.request.Request(url, data=data, method=method, headers={"X-PIN": pin})
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


blob = os.urandom(5 * 1024 * 1024)
assert req(base, pin="000000")[0] == 401
assert req(base + "/big.bin", "PUT", blob)[0] == 201
assert req(base + "/..%2F..%2Fevil.txt", "PUT", b"x")[0] == 201
assert os.path.exists(os.path.join(tmp, "evil.txt"))  # traversal stripped to basename
assert json.loads(req(base)[1])[0] == {"name": "big.bin", "size": len(blob)}
assert req(base + "/big.bin")[1] == blob

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.settimeout(2)
s.sendto(b"ZAPBRIDGE?", ("127.0.0.1", server.DISCOVERY_PORT))
assert json.loads(s.recv(256))["port"] == server.HTTP_PORT
print("all ok")
