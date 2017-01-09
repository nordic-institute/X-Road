#!/usr/bin/python

from BaseHTTPServer import BaseHTTPRequestHandler
import urlparse
import subprocess
import ssl
import tempfile
import os

class TSHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.getheader('content-length',0))

        if length > 100000: 
            self.send_error(400)
            return
        if self.headers.getheader('content-type',"").lower() != "application/timestamp-query":
            self.send_error(400)
            return

        expect=self.headers.getheader('expect',"")
        if (expect.lower() == "100-continue"):
            self.send_response(100)
            self.end_headers()

        bytes = self.rfile.read(length)
        try:
            t=tempfile.NamedTemporaryFile()
            t.write(bytes)
            t.flush()
            devnull = open(os.devnull, 'w')
            p=subprocess.Popen(["openssl","ts","-reply","-config","TSA.cnf","-queryfile", t.name],stdout=subprocess.PIPE,stderr=devnull)
            (out,err) = p.communicate()
            t.close()
            p.wait()            
            if ( p.returncode == 0 ):
                self.send_response(200,'OK')
                self.send_header('Content-Type','application/timestamp-response')
                self.send_header('Content-Length',len(out))
                self.end_headers()
                self.wfile.write(out);
            else:
                send_error(400)

        finally:
            t.close()
            devnull.close()

        return

    def send_error(self, code):
        self.send_response(code)
        self.end_headers()

if __name__ == '__main__':
    from BaseHTTPServer import HTTPServer
    server = HTTPServer(('localhost', 9999), TSHandler)
    print 'Starting server...'
    server.serve_forever()

