#!/usr/bin/python3

from http.server import BaseHTTPRequestHandler, HTTPServer
import subprocess
import tempfile
import email
import os
import re
import sys

class CAHandler(BaseHTTPRequestHandler):

    # openssl config subjectAltName syntax (DNS:host,IP:1.2.3.4,...) — restricted so the
    # value can only reach openssl as data, never as config syntax beyond a SAN list.
    SAN_RE = re.compile(r'^[A-Za-z0-9.,:_@/*\[\]-]+$')

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

    def do_GET(self):
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
            return (None, None, 'auto', '')
        body = self.rfile.read(length)
        msg = email.message_from_bytes(
            b"Content-Type: " + self.headers.get('Content-Type', '').encode()
            + b"\r\nMIME-Version: 1.0\r\n\r\n" + body)
        certreq_filename = None
        certreq_bytes = None
        req_type = 'auto'
        san = ''
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
        expect = self.headers.get('expect', "")
        if expect.lower() == "100-continue":
            self.send_response(100)
            self.end_headers()

        (req_filename, req_bytes, req_type, san) = self._parse_multipart()

        if san and not self.SAN_RE.match(san):
            self.send_error(400)
            return

        if req_filename and req_bytes is not None:
            # The field contains an uploaded file
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

            try:
                t = tempfile.NamedTemporaryFile()
                t.write(req_bytes)
                t.flush()
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
                    self.send_response(200, 'OK')
                    self.send_header('Content-Type', 'application/octet-stream')
                    self.send_header('Content-Disposition',
                                     'attachment; filename="{}.pem"'.format(crtname))
                    self.send_header('Content-Length', len(out))
                    self.end_headers()
                    self.wfile.write(out)
                else:
                    err = err.decode()
                    print(err, file=sys.stderr)
                    self.send_response(500)
                    self.send_header("Content-Type", 'text/html; charset="utf-8"')
                    self.end_headers()
                    self.wfile.write("<html><body>Error:<pre>{}</pre></body></html>".format(err).encode())
                return
            finally:
                t.close()

        self.send_error(400)
        return

if __name__ == '__main__':
    server = HTTPServer(('localhost', 9998), CAHandler)
    print('Starting server...')
    server.serve_forever()
