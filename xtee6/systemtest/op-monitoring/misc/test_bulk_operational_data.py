#!/usr/bin/env python3

# The MIT License
# Copyright (c) 2016 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

# A quick and dirty script for sending bulk JSON requests directly to the JSON-based
# store interface of the operational monitoring daemon.
# Assuming that sufficient memory is available to the daemon.
# Assuming a tunnel has been created from localhost to the actual target host.

import copy
import json
import time
import random
import requests

# Using the default address and port of the operational monitoring daemon.
OP_MONITOR_DAEMON_STORE_DATA_URL = "http://localhost:2080/store_data"
OP_MONITOR_DAEMON_STORE_DATA_HEADERS = {
        "Content-Type": "application/json"
}

SAMPLE_RECORD = { 
      "clientMemberCode":"00000001",
      "serviceXRoadInstance":"XTEE-CI-XM",
      "clientSubsystemCode":"System1",
      "serviceCode":"xroadGetRandom",
      "messageProtocolVersion":"4.0",
      "messageId":"c60b7e66-1dc8-4203-a3c1-3235661f6a84",
      "clientXRoadInstance":"XTEE-CI-XM",
      "messageUserId":"EE37702211234", 
      "clientMemberClass":"GOV", 
      "serviceMemberCode":"00000000",
      "securityServerType":"Client", 
      "securityServerInternalIp":"192.168.56.1",
      "serviceVersion":"v1",
      "serviceMemberClass":"GOV",
      "requestInTs":1479733697687,
      "serviceSubsystemCode":"Center",
      "responseOutTs":1479733718087,
      "succeeded": True,
}

NUM_OF_RECORDS_IN_DATA = 10000

def _timestamp_with_millis():
    return int(time.time() * 1000)

# Construct JSON payload with lots of records to test if the memory of the operational
# monitoring daemon gets exhausted either at the request processing or database side.
json_payload = dict(records=[])

for _ in range(NUM_OF_RECORDS_IN_DATA):
    rec = copy.deepcopy(SAMPLE_RECORD)
    rec["requestInTs"] = _timestamp_with_millis()
    rec["responseOutTs"] = _timestamp_with_millis()
    rec["succeeded"] = bool(random.getrandbits(1))
    json_payload["records"].append(rec)

print(json_payload)

response = requests.post(
        OP_MONITOR_DAEMON_STORE_DATA_URL, data=json.dumps(json_payload),
        headers=OP_MONITOR_DAEMON_STORE_DATA_HEADERS)

print(response.text)
