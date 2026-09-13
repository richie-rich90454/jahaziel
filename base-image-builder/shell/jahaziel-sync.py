#!/usr/bin/env python3
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

"""Workspace file sync client (runs inside the guest VM).

Pulls the workspace home from the host at start, pushes it back every
minute and at stop. Talks to the host over vsock (CID 2, port 9002 in
production); the host relays that to the node agent sync server. The
workspace id and secret come from the kernel command line, so no
config files or extra services are needed in the guest.

Protocol (JSON control frames are u32be-length-prefixed, ZIP blobs are
u64be-length-prefixed). Both directions mirror: files absent from the
incoming tree are deleted, so renames and deletions propagate.
  hello {"v":1,"op":"hello","workspace":...,"secret":...} -> {"ok":true}
  pull  {"op":"pull"} -> ZIP blob -> {"ok":true,"files":N}
  push  {"op":"push"} -> {"ready":true} -> client sends ZIP -> {"ok":true}
  bye   {"op":"bye"} -> {"ok":true}

Usage: jahaziel-sync pull|push
Test hooks (never set in production): JAHAZIEL_SYNC_FAMILY=tcp,
JAHAZIEL_SYNC_HOST, JAHAZIEL_SYNC_PORT.
"""

import io
import json
import os
import socket
import struct
import sys
import time
import zipfile
from pathlib import Path

VSOCK_CID = 2
VSOCK_PORT = 9002
RETRIES = 30
RETRY_DELAY_SECONDS = 2
SOCKET_TIMEOUT_SECONDS = 30
MAX_FRAME_BYTES = 16 * 1024 * 1024
WORKSPACE_DIR = Path("/tmp/jahaziel/workspace")


def read_frame(conn: socket.socket) -> dict:
    raw = b""
    while len(raw) < 4:
        chunk = conn.recv(4 - len(raw))
        if not chunk:
            raise ConnectionError("server closed the connection")
        raw += chunk
    (size,) = struct.unpack(">I", raw)
    if size > MAX_FRAME_BYTES:
        raise ConnectionError(f"oversize frame: {size}")
    payload = b""
    while len(payload) < size:
        chunk = conn.recv(min(65536, size - len(payload)))
        if not chunk:
            raise ConnectionError("server closed the connection")
        payload += chunk
    return json.loads(payload.decode("utf-8"))


def write_frame(conn: socket.socket, message: dict) -> None:
    payload = json.dumps(message).encode("utf-8")
    conn.sendall(struct.pack(">I", len(payload)) + payload)


def read_blob(conn: socket.socket) -> bytes:
    raw = b""
    while len(raw) < 8:
        chunk = conn.recv(8 - len(raw))
        if not chunk:
            raise ConnectionError("server closed the connection")
        raw += chunk
    (size,) = struct.unpack(">Q", raw)
    payload = b""
    while len(payload) < size:
        chunk = conn.recv(min(65536, size - len(payload)))
        if not chunk:
            raise ConnectionError("server closed the connection")
        payload += chunk
    return payload


def write_blob(conn: socket.socket, payload: bytes) -> None:
    conn.sendall(struct.pack(">Q", len(payload)) + payload)


def zip_tree(root: Path) -> tuple[bytes, int]:
    buffer = io.BytesIO()
    count = 0
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.is_symlink():
                continue
            archive.write(path, path.relative_to(root).as_posix())
            count += 1
    return buffer.getvalue(), count


def unzip_tree(payload: bytes, root: Path) -> set[str]:
    written: set[str] = set()
    base = root.resolve()
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            target = (root / entry.filename).resolve()
            if base not in target.parents:
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(entry) as source, open(target, "wb") as dest:
                while True:
                    chunk = source.read(65536)
                    if not chunk:
                        break
                    dest.write(chunk)
            written.add(target.relative_to(base).as_posix())
    return written


def mirror(root: Path, keep: set[str]) -> None:
    for path in sorted(root.rglob("*")):
        if path.is_file() and not path.is_symlink():
            if path.relative_to(root).as_posix() not in keep:
                path.unlink()
    for path in sorted((p for p in root.rglob("*") if p.is_dir()), reverse=True):
        try:
            path.rmdir()
        except OSError:
            pass


def command_line() -> dict[str, str]:
    values: dict[str, str] = {}
    for token in Path("/proc/cmdline").read_text(encoding="utf-8").split():
        if token.startswith("jahaziel."):
            key, _, value = token[len("jahaziel."):].partition("=")
            values[key] = value
    return values


def connect() -> socket.socket:
    if os.environ.get("JAHAZIEL_SYNC_FAMILY") == "tcp":
        conn = socket.create_connection(
            (os.environ.get("JAHAZIEL_SYNC_HOST", "127.0.0.1"),
             int(os.environ.get("JAHAZIEL_SYNC_PORT", "19002"))),
            timeout=SOCKET_TIMEOUT_SECONDS,
        )
    else:
        conn = socket.socket(socket.AF_VSOCK, socket.SOCK_STREAM)
        conn.settimeout(SOCKET_TIMEOUT_SECONDS)
        conn.connect((VSOCK_CID, VSOCK_PORT))
    return conn


def hello(conn: socket.socket, workspace: str, secret: str) -> None:
    write_frame(conn, {"v": 1, "op": "hello", "workspace": workspace, "secret": secret})
    reply = read_frame(conn)
    if not reply.get("ok"):
        raise PermissionError(reply.get("error", "hello rejected"))


def pull(conn: socket.socket, target: Path) -> int:
    write_frame(conn, {"op": "pull"})
    kept = unzip_tree(read_blob(conn), target)
    mirror(target, kept)
    reply = read_frame(conn)
    if not reply.get("ok"):
        raise ConnectionError(reply.get("error", "pull rejected"))
    return len(kept)


def push(conn: socket.socket, source: Path) -> int:
    payload, _ = zip_tree(source)
    write_frame(conn, {"op": "push"})
    reply = read_frame(conn)
    if not reply.get("ready"):
        raise ConnectionError(reply.get("error", "push rejected"))
    write_blob(conn, payload)
    reply = read_frame(conn)
    if not reply.get("ok"):
        raise ConnectionError(reply.get("error", "push rejected"))
    return reply.get("files", 0)


def run_pull(workspace: str, secret: str, target: Path) -> int:
    conn = connect()
    try:
        hello(conn, workspace, secret)
        target.mkdir(parents=True, exist_ok=True)
        count = pull(conn, target)
        write_frame(conn, {"op": "bye"})
        reply = read_frame(conn)
        if not reply.get("ok"):
            raise ConnectionError(reply.get("error", "bye rejected"))
        return count
    finally:
        conn.close()


def run_push(workspace: str, secret: str, source: Path) -> int:
    conn = connect()
    try:
        hello(conn, workspace, secret)
        count = push(conn, source)
        write_frame(conn, {"op": "bye"})
        reply = read_frame(conn)
        if not reply.get("ok"):
            raise ConnectionError(reply.get("error", "bye rejected"))
        return count
    finally:
        conn.close()


def main() -> int:
    if len(sys.argv) != 2 or sys.argv[1] not in ("pull", "push"):
        print("usage: jahaziel-sync pull|push", file=sys.stderr)
        return 2
    params = command_line()
    workspace = params.get("workspace", "")
    secret = params.get("sync-secret", "")
    if not workspace or not secret:
        print("jahaziel.workspace and jahaziel.sync-secret are required on the kernel command line",
              file=sys.stderr)
        return 1
    mode = sys.argv[1]
    last_error = ""
    for _ in range(RETRIES):
        try:
            if mode == "pull":
                count = run_pull(workspace, secret, WORKSPACE_DIR)
            else:
                WORKSPACE_DIR.mkdir(parents=True, exist_ok=True)
                count = run_push(workspace, secret, WORKSPACE_DIR)
            print(f"jahaziel-sync {mode}: {count} files")
            return 0
        except (OSError, ConnectionError, PermissionError) as failure:
            last_error = str(failure)
            time.sleep(RETRY_DELAY_SECONDS)
    print(f"jahaziel-sync {mode} failed: {last_error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
