#!/usr/bin/env python3
"""
Simple OTA Update Server for Subway Alert App
Usage: python3 ota_server.py [--port 8080] [--host 0.0.0.0]
"""

import json
import os
import argparse
from http.server import HTTPServer, SimpleHTTPRequestHandler
from datetime import datetime

# Configuration
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
VERSION_FILE = os.path.join(SCRIPT_DIR, 'version.json')
APK_DIR = os.path.join(SCRIPT_DIR, 'apk')

DEFAULT_HOST = '0.0.0.0'
DEFAULT_PORT = 8080


class OTARequestHandler(SimpleHTTPRequestHandler):
    """Custom handler for OTA API endpoints"""

    def do_GET(self):
        if self.path == '/api/check-update' or self.path == '/check-update':
            self.send_version_info()
        elif self.path == '/api/version' or self.path == '/version':
            self.send_version_info()
        elif self.path.startswith('/apk/'):
            # Serve APK files
            super().do_GET()
        elif self.path == '/' or self.path == '/index.html':
            self.send_index()
        else:
            self.send_error(404, 'Not Found')

    def send_version_info(self):
        """Return version JSON for update check"""
        try:
            with open(VERSION_FILE, 'r', encoding='utf-8') as f:
                version_info = json.load(f)

            # Update download URL with actual server address
            if 'downloadUrl' in version_info and '<你的服务器IP或域名>' in version_info['downloadUrl']:
                host = self.headers.get('Host', f'{self.server.server_address[0]}:{self.server.server_address[1]}')
                version_info['downloadUrl'] = f'http://{host}/apk/app.apk'

            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(version_info, ensure_ascii=False).encode('utf-8'))

        except FileNotFoundError:
            self.send_error(404, 'Version file not found')
        except Exception as e:
            self.send_error(500, f'Server error: {str(e)}')

    def send_index(self):
        """Return simple index page"""
        html = f"""<!DOCTYPE html>
<html>
<head>
    <title>Subway Alert OTA Server</title>
    <style>
        body {{ font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }}
        h1 {{ color: #333; }}
        .info {{ background: #f5f5f5; padding: 15px; border-radius: 8px; margin: 20px 0; }}
        .endpoint {{ background: #e8f4f8; padding: 10px; border-radius: 4px; font-family: monospace; }}
        a {{ color: #0066cc; }}
    </style>
</head>
<body>
    <h1>🚇 Subway Alert OTA Server</h1>
    <div class="info">
        <p><strong>Status:</strong> Running</p>
        <p><strong>APK Directory:</strong> {APK_DIR}</p>
        <p><strong>Version File:</strong> {VERSION_FILE}</p>
    </div>
    <h3>API Endpoints:</h3>
    <div class="endpoint">
        <p><a href="/api/check-update">GET /api/check-update</a> - Check for updates</p>
        <p><a href="/apk/">GET /apk/</a> - Browse APK files</p>
    </div>
</body>
</html>"""
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.end_headers()
        self.wfile.write(html.encode('utf-8'))

    def log_message(self, format, *args):
        """Custom log format"""
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        print(f'[{timestamp}] {args[0]}')


def main():
    parser = argparse.ArgumentParser(description='Subway Alert OTA Server')
    parser.add_argument('--host', default=DEFAULT_HOST, help='Host to bind (default: 0.0.0.0)')
    parser.add_argument('--port', type=int, default=DEFAULT_PORT, help='Port to listen (default: 8080)')
    args = parser.parse_args()

    # Change to script directory
    os.chdir(SCRIPT_DIR)

    server = HTTPServer((args.host, args.port), OTARequestHandler)
    print(f"""
╔═══════════════════════════════════════════════════╗
║       🚇 Subway Alert OTA Server                   ║
╠═══════════════════════════════════════════════════╣
║  Server:  http://{args.host}:{args.port}                    ║
║  APK Dir: {APK_DIR}              ║
║                                                   ║
║  Endpoints:                                       ║
║    GET /api/check-update  - Check app update     ║
║    GET /apk/<file>        - Download APK         ║
╚═══════════════════════════════════════════════════╝
    """)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\nShutting down server...')
        server.shutdown()


if __name__ == '__main__':
    main()
