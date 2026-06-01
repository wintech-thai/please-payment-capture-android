#!/usr/bin/env python3
"""Small HTTP server used by soak tests.

Endpoints:
- POST /webhook
- POST /probe

Each request is appended to a JSONL file so the soak script can count and
inspect deliveries afterwards.
"""

from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class Handler(BaseHTTPRequestHandler):
    log_path: Path

    def log_message(self, fmt: str, *args) -> None:
        # Keep stdout quiet; the JSONL file is our durable log.
        return

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8", errors="replace") if length else ""
        path = self.path.rstrip("/")
        kind = "webhook" if path.endswith("/webhook") else "probe" if path.endswith("/probe") else "other"
        entry = {
            "ts": time.strftime("%Y-%m-%d %H:%M:%S"),
            "epoch_ms": int(time.time() * 1000),
            "kind": kind,
            "path": self.path,
            "auth_present": self.headers.get("Authorization", "").startswith("Bearer "),
            "content_length": length,
            "body_excerpt": body[:500],
        }
        with self.log_path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"ok":true}')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8088)
    parser.add_argument("--log", required=True)
    args = parser.parse_args()

    log_path = Path(args.log)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text("", encoding="utf-8")

    Handler.log_path = log_path
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Listening on http://{args.host}:{args.port} -> {log_path}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

