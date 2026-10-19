import json
import logging
import math
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from rate_limiter import TokenBucketLimiter


CONFIG_PATH = Path(__file__).with_name("config.json")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
logger = logging.getLogger("rate-limiter")


with CONFIG_PATH.open(encoding="utf-8") as file:
    CONFIG = json.load(file)


limiter = TokenBucketLimiter(
    capacity=CONFIG["capacity"],
    refill_rate=CONFIG["refill_rate"],
    idle_ttl=CONFIG["idle_ttl"],
)


class RateLimitHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        logger.info(
            "client=%s %s",
            self.client_address[0],
            fmt % args,
        )

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {"status": "ok"})
            return

        # Demo identity: direct client IP.
        # Behind a proxy, use forwarded IP headers only when
        # they are supplied by a trusted proxy.
        client_key = self.client_address[0]

        allowed, remaining, retry_after = limiter.allow(client_key)

        if not allowed:
            self.send_json(
                429,
                {
                    "error": "rate_limit_exceeded",
                    "message": "Too many requests",
                    "retry_after_seconds": round(retry_after, 3),
                },
                headers={
                    "Retry-After": str(max(1, math.ceil(retry_after))),
                    "RateLimit-Limit": str(limiter.capacity),
                    "RateLimit-Remaining": "0",
                },
            )
            return

        self.send_json(
            200,
            {
                "message": "Request allowed",
                "client": client_key,
            },
            headers={
                "RateLimit-Limit": str(limiter.capacity),
                "RateLimit-Remaining": str(math.floor(remaining)),
            },
        )

    def send_json(self, status, payload, headers=None):
        body = json.dumps(payload).encode("utf-8")

        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))

        for name, value in (headers or {}).items():
            self.send_header(name, value)

        self.end_headers()
        self.wfile.write(body)


def cleanup_loop():
    while True:
        time.sleep(CONFIG["cleanup_interval"])
        removed = limiter.cleanup_expired()

        if removed:
            logger.info("expired_client_buckets_removed=%d", removed)


def main():
    cleanup_thread = threading.Thread(
        target=cleanup_loop,
        daemon=True,
    )
    cleanup_thread.start()

    server = ThreadingHTTPServer(
        (CONFIG["host"], CONFIG["port"]),
        RateLimitHandler,
    )

    logger.info(
        "rate_limiter_started host=%s port=%s capacity=%s refill_rate=%s",
        CONFIG["host"],
        CONFIG["port"],
        limiter.capacity,
        limiter.refill_rate,
    )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("shutting_down")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
