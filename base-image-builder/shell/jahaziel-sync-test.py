#!/usr/bin/env python3
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

"""Executable check for jahaziel-sync.py. Framework-free asserts only.

Spins an in-process fake sync server on loopback TCP and drives the
real client functions through pull/push round-trips, an auth denial,
and a traversal attack. Run: python3 jahaziel-sync-test.py
"""

import io
import json
import socket
import struct
import sys
import tempfile
import threading
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent

import importlib.util

_spec = importlib.util.spec_from_file_location("jahaziel_sync", HERE / "jahaziel-sync.py")
assert _spec is not None and _spec.loader is not None
sync = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sync)


def send_frame(conn: socket.socket, message: dict) -> None:
    payload = json.dumps(message).encode("utf-8")
    conn.sendall(struct.pack(">I", len(payload)) + payload)


def recv_frame(conn: socket.socket) -> dict:
    raw = b""
    while len(raw) < 4:
        chunk = conn.recv(4 - len(raw))
        assert chunk, "server closed the connection"
        raw += chunk
    (size,) = struct.unpack(">I", raw)
    payload = b""
    while len(payload) < size:
        chunk = conn.recv(min(65536, size - len(payload)))
        assert chunk, "server closed the connection"
        payload += chunk
    return json.loads(payload.decode("utf-8"))


def send_blob(conn: socket.socket, payload: bytes) -> None:
    conn.sendall(struct.pack(">Q", len(payload)) + payload)


def recv_blob(conn: socket.socket) -> bytes:
    raw = b""
    while len(raw) < 8:
        chunk = conn.recv(8 - len(raw))
        assert chunk, "server closed the connection"
        raw += chunk
    (size,) = struct.unpack(">Q", raw)
    payload = b""
    while len(payload) < size:
        chunk = conn.recv(min(65536, size - len(payload)))
        assert chunk, "server closed the connection"
        payload += chunk
    return payload


def zip_of(root: Path) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(root.rglob("*")):
            if path.is_file() and not path.is_symlink():
                archive.write(path, path.relative_to(root).as_posix())
    return buffer.getvalue()


class FakeServer:
    """Minimal sync server over loopback TCP for the round-trip checks."""

    def __init__(self, home: Path, secret: str) -> None:
        self.home = home
        self.secret = secret
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.bind(("127.0.0.1", 0))
        self.socket.listen(1)
        self.port = self.socket.getsockname()[1]
        self.thread = threading.Thread(target=self.serve, daemon=True)

    def start(self) -> None:
        self.thread.start()

    def serve(self) -> None:
        while True:
            conn, _ = self.socket.accept()
            worker = threading.Thread(target=self.handle_quietly, args=(conn,), daemon=True)
            worker.start()

    def handle_quietly(self, conn: socket.socket) -> None:
        with conn:
            try:
                self.handle(conn)
            except (AssertionError, ConnectionError, OSError):
                pass

    def handle(self, conn: socket.socket) -> None:
        hello = recv_frame(conn)
        if hello.get("workspace") != "ws-1" or hello.get("secret") != self.secret:
            send_frame(conn, {"ok": False, "error": "denied"})
            return
        send_frame(conn, {"ok": True})
        while True:
            try:
                message = recv_frame(conn)
            except AssertionError:
                return
            op = message.get("op")
            if op == "pull":
                send_blob(conn, zip_of(self.home))
                send_frame(conn, {"ok": True, "files": 1})
            elif op == "push":
                send_frame(conn, {"ready": True})
                payload = recv_blob(conn)
                kept = sync.unzip_tree(payload, self.home)
                sync.mirror(self.home, kept)
                send_frame(conn, {"ok": True, "files": len(kept)})
            elif op == "bye":
                send_frame(conn, {"ok": True})
                return
            else:
                send_frame(conn, {"ok": False, "error": "unknown op"})
                return


def tree(root: Path) -> dict[str, str]:
    return {
        path.relative_to(root).as_posix(): path.read_text(encoding="utf-8")
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }


def check_round_trip() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        home = Path(tmp) / "home"
        guest = Path(tmp) / "guest"
        (home / "src").mkdir(parents=True)
        (home / "src" / "main.py").write_text("print(1)\n", encoding="utf-8")
        (home / "notes.txt").write_text("hello\n", encoding="utf-8")
        server = FakeServer(home, "s3cret")
        server.start()
        import os

        os.environ["JAHAZIEL_SYNC_FAMILY"] = "tcp"
        os.environ["JAHAZIEL_SYNC_HOST"] = "127.0.0.1"
        os.environ["JAHAZIEL_SYNC_PORT"] = str(server.port)

        conn = sync.connect()
        try:
            sync.hello(conn, "ws-1", "s3cret")
            assert sync.pull(conn, guest) == 2, "pull mirrors both files"
            assert tree(guest) == tree(home), "guest matches host after pull"
            (guest / "src" / "main.py").write_text("print(2)\n", encoding="utf-8")
            (guest / "new.txt").write_text("new\n", encoding="utf-8")
            (guest / "notes.txt").unlink()
            send_frame(conn, {"op": "bye"})
            assert recv_frame(conn)["ok"], "bye acknowledged"
        finally:
            conn.close()
        conn2 = sync.connect()
        try:
            sync.hello(conn2, "ws-1", "s3cret")
            assert sync.push(conn2, guest) == 2, "push mirrors two files"
            assert tree(home) == tree(guest), "host matches guest after push"
            assert not (home / "notes.txt").exists(), "deletions propagate"
            send_frame(conn2, {"op": "bye"})
            assert recv_frame(conn2)["ok"], "bye acknowledged"
        finally:
            conn2.close()
    print("round_trip ok")


def check_auth_denied() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        server = FakeServer(Path(tmp), "s3cret")
        server.start()
        import os

        os.environ["JAHAZIEL_SYNC_FAMILY"] = "tcp"
        os.environ["JAHAZIEL_SYNC_HOST"] = "127.0.0.1"
        os.environ["JAHAZIEL_SYNC_PORT"] = str(server.port)
        conn = sync.connect()
        try:
            try:
                sync.hello(conn, "ws-1", "wrong")
            except PermissionError:
                print("auth_denied ok")
                return
            raise AssertionError("bad secret was accepted")
        finally:
            conn.close()


def check_traversal_safe() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp) / "root"
        root.mkdir()
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            archive.writestr("../../evil.txt", "evil")
            archive.writestr("/abs.txt", "abs")
            archive.writestr("ok.txt", "ok")
        kept = sync.unzip_tree(buffer.getvalue(), root)
        assert kept == {"ok.txt"}, f"only the safe entry lands: {kept}"
        assert not (Path(tmp) / "evil.txt").exists(), "no escape from the root"
        assert not (root / "abs.txt").exists(), "no absolute write"
        assert (root / "ok.txt").read_text(encoding="utf-8") == "ok"
    print("traversal_safe ok")


def main() -> int:
    check_round_trip()
    check_auth_denied()
    check_traversal_safe()
    print("jahaziel-sync checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
