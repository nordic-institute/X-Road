#!/usr/bin/python3

from http.server import BaseHTTPRequestHandler, HTTPServer
import subprocess
import tempfile
import email
import os
import re
import sys
import time
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger('ca_server')

SAN_PATTERN = re.compile(r'^[A-Za-z0-9.,:\-_* ]{1,1000}$')

class CAHandler(BaseHTTPRequestHandler):

    FORM_HTML = '''\
<!DOCTYPE html>
<html lang="en">
  <head>
    <title>Test CA</title>
  </head>
  <body>
    <form method="POST" enctype="multipart/form-data" action="sign">
      <fieldset>
        <legend>Test CA: CSR signing</legend>
        <div>
          <label style="display:inline-block; width:4em" for="csr">CSR</label>
          <input name="certreq" type="file" id="csr">
        </div>
        <div>
          <label style="display:inline-block; width:4em">Type</label>
          <input type="radio" name="type" id="sign" value="sign">
          <label for="sign">Sign</label>
          <input type="radio" name="type" id="auth" value="auth">
          <label for="auth">Auth</label>
          <input type="radio" name="type" id="auto" value="auto" checked>
          <label for="auto">Autodetect from file name</label>
        </div>
        <div>
          <input type="submit" value="Sign" style="margin-top:1em"/>
         </div>
      </fieldset>
    </form>
  </body>
</html>
'''.encode()

    def log_request_info(self):
        client_address = self.client_address[0]
        request_line = self.requestline
        logger.info(f"Request from {client_address}: {request_line}")

    def log_response_info(self, status_code, message=""):
        logger.info(f"Response: {status_code} {message}")

    def send_response(self, code, message=None):
        super().send_response(code, message)
        self.log_response_info(code, message)

    def do_GET(self):
        self.log_request_info()

        if self.path == "/favicon.ico":
            self.send_response(410, "Gone")
            return

        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.end_headers()
        self.wfile.write(self.FORM_HTML)

    def _parse_multipart(self):
        # Parse the multipart/form-data POST with the stdlib email module.
        # Replaces cgi.FieldStorage (the cgi module was removed in Python 3.13).
        # Returns (certreq_filename, certreq_bytes, req_type, san).
        length = int(self.headers.get('Content-Length', 0))
        if length <= 0 or length > 10000:
            return (None, None, 'auto', None)
        body = self.rfile.read(length)
        msg = email.message_from_bytes(
            b"Content-Type: " + self.headers.get('Content-Type', '').encode()
            + b"\r\nMIME-Version: 1.0\r\n\r\n" + body)
        certreq_filename = None
        certreq_bytes = None
        req_type = 'auto'
        san = None
        if msg.is_multipart():
            for part in msg.get_payload():
                name = part.get_param('name', header='content-disposition')
                if part.get_filename():
                    certreq_filename = part.get_filename()
                    certreq_bytes = part.get_payload(decode=True)
                elif name == 'type':
                    req_type = part.get_payload(decode=True).decode().strip()
                elif name == 'san':
                    san = part.get_payload(decode=True).decode().strip()
        return (certreq_filename, certreq_bytes, req_type, san)

    def do_POST(self):
        self.log_request_info()
        logger.info("Processing certificate signing request")

        expect = self.headers.get('expect', "")
        if expect.lower() == "100-continue":
            self.send_response(100)
            self.end_headers()

        (req_filename, req_bytes, req_type, san) = self._parse_multipart()

        if san is not None and not SAN_PATTERN.match(san):
            logger.warning("Bad request - invalid san field")
            self.send_error(400, explain="Invalid san field")
            return

        if req_filename and req_bytes is not None:
            # The field contains an uploaded file
            logger.info(f"Received CSR file: {req_filename}")

            if req_type == "auto":
                if "sign" in req_filename:
                    sign_type = "sign"
                else:
                    sign_type = "auth"
            else:
                if req_type == "sign":
                    sign_type = "sign"
                else:
                    sign_type = "auth"

            logger.info(f"Certificate type: {sign_type}")

            try:
                t = tempfile.NamedTemporaryFile()
                t.write(req_bytes)
                t.flush()
                logger.info(f"Executing sign_req.sh with type {sign_type}")
                cmd = ["bash", "/home/ca/CA/sign_req.sh", sign_type, t.name]
                if san:
                    cmd.append(san)
                p = subprocess.Popen(cmd,
                                     stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE)
                (out, err) = p.communicate()
                t.close()
                p.wait()
                if p.returncode == 0:
                    crtname = os.path.splitext(req_filename)[0].replace("_csr_", "_crt_")
                    logger.info(f"Certificate successfully created: {crtname}.pem")
                    self.send_response(200, 'OK')
                    self.send_header('Content-Type', 'application/octet-stream')
                    self.send_header('Content-Disposition',
                                     'attachment; filename="{}.pem"'.format(crtname))
                    self.send_header('Content-Length', len(out))
                    self.end_headers()
                    self.wfile.write(out)
                else:
                    err = err.decode()
                    logger.error(f"Certificate signing failed: {err}")
                    self.send_response(500)
                    self.send_header("Content-Type", 'text/html; charset="utf-8"')
                    self.end_headers()
                    self.wfile.write("<html><body>Error:<pre>{}</pre></body></html>".format(err).encode())
                return
            finally:
                t.close()

        logger.warning("Bad request - missing or invalid certificate request")
        self.send_response(400, "Bad Request - Missing or invalid certificate request")
        return

if __name__ == '__main__':
    server = HTTPServer(('localhost', 9998), CAHandler)
    logger.info('Starting CA server on localhost:9998')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info('Server shutting down')
    except Exception as e:
        logger.error(f'Unexpected error: {str(e)}')
