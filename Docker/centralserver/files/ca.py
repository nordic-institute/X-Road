#!/usr/bin/python

from BaseHTTPServer import BaseHTTPRequestHandler
import subprocess
import tempfile
import cgi
import time

class CAHandler(BaseHTTPRequestHandler):

    form_html = \
        '''
        <html>
        <body>
        <form method="POST" enctype="multipart/form-data" action="sign">
        <fieldset>
        <legend>TestCA: CSR signing</legend>
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
        </html>
        </body>
        '''

    def do_GET(self):
        if self.path == "/favicon.ico":
            self.send_response(410, "Gone")
            return

        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.end_headers()
        self.wfile.write(self.form_html.encode())

    def do_POST(self):
        cgi.maxlen = 10000

        expect = self.headers.getheader('expect', "")
        if expect.lower() == "100-continue":
            self.send_response(100)
            self.end_headers()

        form = cgi.FieldStorage(
            fp=self.rfile,
            headers=self.headers,
            environ={'REQUEST_METHOD': 'POST'})

        req_item = form['certreq']
        req_type = form.getfirst('type', 'auto')

        if req_item.filename:
            # The field contains an uploaded file
            if req_type == "auto":
                if "sign" in req_item.filename:
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
                t.write(req_item.file.read())
                t.flush()
                p = subprocess.Popen(["bash", "/home/ca/CA/sign_req.sh", sign_type, t.name], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                (out, err) = p.communicate()
                t.close()
                p.wait()
                if p.returncode == 0:
                    self.send_response(200, 'OK')
                    self.send_header('Content-Type', 'application/octet-stream')
                    self.send_header('Content-Disposition',
                                     'attachment; filename="{}-crt.pem"'.format(sign_type))
                    self.send_header('Content-Length', len(out))
                    self.end_headers()
                    self.wfile.write(out)
                else:
                    print err
                    self.send_response(500)
                    self.send_header("Content-Type", 'text/html; charset="utf-8"')
                    self.end_headers()
                    self.wfile.write("<html><body>Error:<pre>{}</pre></body></html>".format(err.encode()))
                return
            finally:
                t.close()
                req_item.file.close()

        self.send_error(400)
        return

if __name__ == '__main__':
    from BaseHTTPServer import HTTPServer
    server = HTTPServer(('0.0.0.0', 9998), CAHandler)
    print 'Starting server...'
    server.serve_forever()

