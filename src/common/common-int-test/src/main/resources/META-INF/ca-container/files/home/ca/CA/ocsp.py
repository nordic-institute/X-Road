#!/usr/bin/python3

from http.server import BaseHTTPRequestHandler
import subprocess
import tempfile
import sys

class OCSPHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.get('content-length', 0))

        if length > 100000:
            self.send_error(400)
            return
        if self.headers.get('content-type', "").lower() != "application/ocsp-request":
            self.send_error(400)
            return

        expect = self.headers.get('expect', "")
        if expect.lower() == "100-continue":
            self.send_response(100)
            self.end_headers()

        bytes = self.rfile.read(length)
        try:
            t = tempfile.NamedTemporaryFile()
            t.write(bytes)
            t.flush()
            p = subprocess.Popen(["openssl", "ocsp", "-index", "index.txt", "-rsigner", "certs/ocsp.cert.pem", "-rkey", "private/ocsp.key.pem", "-CA", "certs/ca.cert.pem", "-reqin", t.name, '-respout', '-'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            (out, err) = p.communicate()
            t.close()
            p.wait()
            if p.returncode == 0:
                self.send_response(200, 'OK')
                self.send_header('Content-Type', 'application/ocsp-response')
                self.send_header('Content-Length', len(out))
                self.end_headers()
                self.wfile.write(out)
            else:
                sys.stderr.write(err)
                self.send_error(400)

        finally:
            t.close()

        return

    def send_error(self, code):
        self.send_response(code)
        self.end_headers()

if __name__ == '__main__':
    from http.server import HTTPServer
    server = HTTPServer(('localhost', 8889), OCSPHandler)
    print('Starting server...')
    server.serve_forever()
