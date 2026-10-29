"""Local FX fixture. A restart establishes a new effective time; never a market feed."""
import json
import os
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

quote = {
    "rate": os.getenv("FX_MOCK_RATE", "5.4321"),
    "origin": "local-mock",
    "effectiveAt": os.getenv("FX_MOCK_EFFECTIVE_AT")
    or datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z"),
}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Do not log paths, headers or arbitrary request text.
        pass

    def do_GET(self):
        if self.path != "/rate":
            self.send_error(404)
            return
        body = json.dumps(quote).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()
