#!/usr/bin/env python2

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

# A script for working with the sample exported Zabbix host data provided with the
# operational monitoring daemon. This script allows the user to
# - import the given sample host to Zabbix via its API or
# - extract the JSON data for importing the host to Zabbix manually.

# If the Zabbix API is preferred, an authentication key must be provided. This key can
# be obtained using the Zabbix API.
# See https://www.zabbix.com/documentation/3.0/manual/api for reference.

# The imported host can be used as a basis for cloning the host objects corresponding
# to the actual operational monitoring daemon(s) used, after customization.

# Dependencies:
# python-requests (or equivalent) for Python 2.7

import os
import sys
import json
import random
import argparse
import requests

# Expecting this file to be present alongside this script, installed with the
# xroad-opmonitor package.
EXPORTED_HOST_DATA_FILE = "sample_operational_monitoring_host.json"

def _format_import_conf_request(exported_configuration, auth_key):
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "configuration.import",
                "params": {
                "format": "json",
                "rules": {
                    "hosts": {
                        "createMissing": True,
                        "updateExisting": True
                    },
                    "groups": {
                        "createMissing": True,
                        "updateExisting": True
                    },
                    "items": {
                        "createMissing": True,
                        "updateExisting": True
                    },
                    "applications": {
                        "createMissing": True,
                        "updateExisting": True
                    }
                },
                "source": exported_configuration
            },
            "auth": auth_key,
            "id": random.randint(1, 100),
        }
    )

# Because the --extract mode prints data to stdout, let's explicitly use stderr
# for other runtime messages.
def _print_err(msg):
    print >> sys.stderr, msg

def _post_json_rpc_request(zabbix_api_url, data):
    response = requests.post(
            zabbix_api_url, headers={"Content-type": "application/json"}, data=data,
            # Accept self-signed TLS certs
            verify=False)
    response_json = json.loads(response.text)

    if response_json.get("error"):
        _print_err("Received the following error in the response:")
        _print_err(response_json.get("error"))

    # Let the caller process the result further.
    return response_json

def _import_sample_configuration(zabbix_api_url, auth_key):
    sample_data = None
    with open(EXPORTED_HOST_DATA_FILE) as f:
        sample_data = json.loads(f.read()).get("result")

    request_data = _format_import_conf_request(sample_data, auth_key)
    result = _post_json_rpc_request(zabbix_api_url, request_data)
    if not result.get("error"):
        _print_err("Sample host imported successfully")

def _extract_sample_configuration():
    sample_data = None
    with open(EXPORTED_HOST_DATA_FILE) as f:
        sample_data = json.loads(f.read())

    # Print the formatted JSON to stdout so the user can pipe it to a file.
    print json.dumps(json.loads(sample_data.get("result")), sort_keys=True, indent=4)

if __name__ == '__main__':
    if not os.path.isfile(EXPORTED_HOST_DATA_FILE):
        _print_err("Failed to read the host data at '%s'" % (EXPORTED_HOST_DATA_FILE))
        sys.exit(1)

    argparser = argparse.ArgumentParser()
    argparser.add_argument("--import", dest="run_import", action="store_true",
        help="Import the sample host using Zabbix API")
    argparser.add_argument("--extract", dest="run_extract", action="store_true",
        help="Extract the sample configuration for importing using the UI")
    argparser.add_argument("--zabbix-api-url", dest="zabbix_api_url",
        help="Full URL of the Zabbix API endpoint. " \
                "Required if --import is given, ignored otherwise ")
    argparser.add_argument("--zabbix-auth-key", dest="zabbix_auth_key",
        help="Zabbix authentication key obtained using its API. " \
                "Required if --import is given, ignored otherwise ")
    args = argparser.parse_args()

    if args.run_import:
        if not args.zabbix_api_url:
            _print_err(
                "Please provide the URL of the Zabbix API endpoint with --zabbix-api-url")
        if not args.zabbix_auth_key:
            _print_err("Please provide the Zabbix API auth key with --zabbix-auth-key")
        if args.zabbix_api_url and args.zabbix_auth_key:
            _import_sample_configuration(args.zabbix_api_url, args.zabbix_auth_key)
        else:
            sys.exit(2)

    elif args.run_extract:
        _extract_sample_configuration()

    else:
        _print_err("Please provide either --import or --extract to select the action")
        sys.exit(2)
