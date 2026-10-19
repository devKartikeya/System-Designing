import itertools
import json
import logging
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


CONFIG_PATH = "config.json"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
logger = logging.getLogger("load-balancer")


class Backend:
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.healthy = True
        self.failures = 0
        self.last_check = 0.0

    @property
    def address(self):
        return f"{self.host}:{self.port}"


class BackendPool:
    def __init__(self, backends, health_path="/health"):
        self.backends = backends
        self.health_path = health_path
        self._lock = threading.Lock()
        self._cursor = itertools.count()

    def select(self):
        """Return the next healthy backend using round robin."""
        with self._lock:
            healthy = [b for b in self.backends if b.healthy]

            if not healthy:
                return None

            index = next(self._cursor) % len(healthy)
            return healthy[index]

    def check_backend(self, backend, timeout=1.5):
        """Probe one backend without holding the pool lock."""
        connection = HTTPConnection(
            backend.host,
            backend.port,
            timeout=timeout,
        )

        try:
            connection.request("GET", self.health_path)
            response = connection.getresponse()
            response.read()
            is_healthy = 200 <= response.status < 300
        except OSError:
            is_healthy = False
        finally:
            connection.close()

        with self._lock:
            backend.healthy = is_healthy
            backend.last_check = time.time()
            backend.failures = 0 if is_healthy else backend.failures + 1

        logger.info(
            "health_check backend=%s healthy=%s failures=%s",
            backend.address,
            is_healthy,
            backend.failures,
        )

    def health_check_loop(self, interval=5):
        while True:
            with ThreadPoolExecutor(
                max_workers=max(1, len(self.backends))
            ) as executor:
                list(executor.map(self.check_backend, self.backends))

            time.sleep(interval)


class LoadBalancerHandler(BaseHTTPRequestHandler):
    pool = None
    request_timeout = 3.0
    max_request_body = 1_000_000

    # Avoid logging every request through BaseHTTPRequestHandler.
    def log_message(self, fmt, *args):
        logger.info("client=%s " + fmt, self.client_address[0], *args)

    def do_GET(self):
        self._proxy()

    def do_POST(self):
        self._proxy()

    def do_PUT(self):
        self._proxy()

    def do_PATCH(self):
        self._proxy()

    def do_DELETE(self):
        self._proxy()

    def do_OPTIONS(self):
        self._proxy()

    def _proxy(self):
        if self.path == "/_lb/health":
            self._send_json(200, {"status": "ok"})
            return

        backend = self.pool.select()
        if backend is None:
            self._send_json(503, {"error": "No healthy backends"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._send_json(400, {"error": "Invalid Content-Length"})
            return

        if content_length < 0 or content_length > self.max_request_body:
            self._send_json(413, {"error": "Request body too large"})
            return

        body = self.rfile.read(content_length) if content_length else None

        # Hop-by-hop headers must not be forwarded unchanged.
        hop_by_hop = {
            "connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer",
            "transfer-encoding", "upgrade",
        }

        headers = {
            key: value
            for key, value in self.headers.items()
            if key.lower() not in hop_by_hop
            and key.lower() not in {"host", "content-length"}
        }
        headers["Host"] = backend.address
        headers["X-Forwarded-For"] = self.client_address[0]
        headers["X-Forwarded-Proto"] = "http"

        connection = HTTPConnection(
            backend.host,
            backend.port,
            timeout=self.request_timeout,
        )

        started = time.monotonic()

        try:
            connection.request(
                self.command,
                self.path,
                body=body,
                headers=headers,
            )
            response = connection.getresponse()
            response_body = response.read()

            response_headers = [
                (key, value)
                for key, value in response.getheaders()
                if key.lower() not in hop_by_hop
                and key.lower() not in {"content-length", "server", "date"}
            ]

            self.send_response(response.status, response.reason)

            for key, value in response_headers:
                self.send_header(key, value)

            self.send_header("Content-Length", str(len(response_body)))
            self.send_header("X-Backend", backend.address)
            self.end_headers()

            if self.command != "HEAD":
                self.wfile.write(response_body)

            logger.info(
                "method=%s path=%s backend=%s status=%s duration_ms=%.2f",
                self.command,
                urlsplit(self.path).path,
                backend.address,
                response.status,
                (time.monotonic() - started) * 1000,
            )

        except OSError:
            logger.exception("backend_request_failed backend=%s", backend.address)

            # Do not blindly retry requests: POST/other operations may have
            # succeeded at the backend before the connection failed.
            if not self.wfile.closed:
                try:
                    self._send_json(502, {"error": "Backend request failed"})
                except OSError:
                    pass
        finally:
            connection.close()

    def _send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main():
    with open(CONFIG_PATH, encoding="utf-8") as file:
        config = json.load(file)

    backends = [
        Backend(item["host"], item["port"])
        for item in config["backends"]
    ]

    pool = BackendPool(
        backends,
        health_path=config.get("health_path", "/health"),
    )

    LoadBalancerHandler.pool = pool
    LoadBalancerHandler.request_timeout = config.get("request_timeout", 3.0)

    health_thread = threading.Thread(
        target=pool.health_check_loop,
        daemon=True,
    )
    health_thread.start()

    server = ThreadingHTTPServer(
        (config.get("host", "127.0.0.1"), config.get("port", 8080)),
        LoadBalancerHandler,
    )

    logger.info(
        "load_balancer_started address=%s:%s backends=%s",
        config.get("host", "127.0.0.1"),
        config.get("port", 8080),
        len(backends),
    )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("shutting_down")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
