#!/bin/sh

* * * * * sdsb curl http://127.0.0.1:8084/center-service/gen_conf  2>&1 >/dev/null; curl http://127.0.0.1:8084/center-service/dist_files 2>&1 >/dev/null

