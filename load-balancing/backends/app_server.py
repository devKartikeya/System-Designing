import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class AppHandler(BaseHTTPRequestHandler):
    instance_name = ""

    def do_GET(self):
        if self.path == "/health":
            payload = {"status": "healthy"}
            self.send_response(200)
        else:
            payload = {
                "instance": self.instance_name,
                "path": self.path,
            }
            self.send_response(200)

        body = json.dumps(payload).encode()
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print(f"[{self.instance_name}] {fmt % args}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()

    AppHandler.instance_name = f"app-{args.port}"

    server = ThreadingHTTPServer(
        ("127.0.0.1", args.port),
        AppHandler,
    )

    print(f"{AppHandler.instance_name} listening on {args.port}")
    server.serve_forever()


if __name__ == "__main__":
    main()