#!/usr/bin/env python3

""" Script for getting status information about this cluster of central servers.

By default the request is made to the local server and reflects the view of the
cluster as seen by the local node.
"""

import requests
import json
import sys

HOST = "localhost"
PORT = 8083 # The internal listening port of the Jetty web server.

TARGET_URL = "http://%s:%s/public_system_status/check_ha_cluster_status" % (
        HOST, PORT)

r = requests.get(TARGET_URL)
if r.status_code != requests.codes.OK:
    print("Failed to check the status of the HA cluster: %s (%s)" % (
        r.status_code, r.text))
    sys.exit(1)

try:
    result = json.loads(r.text).get('ha_node_status', dict())
    if not result.get('ha_configured'):
        print("This central server is not part of an HA cluster")
        sys.exit(0)

    print("\nSUMMARY OF CLUSTER STATUS:")
    print("  All nodes: %s" % ("OK" if result.get("all_nodes_ok") else "NOK"))
    print("  Configuration: %s" % (
        "OK" if result.get("configuration_ok") else "NOK"))

    print("\nDETAILED CLUSTER STATUS INFORMATION:")
    print(json.dumps(result, sort_keys=True, indent=4, separators=(',', ': ')))

except Exception as e:
    print("Failed to parse the status of the HA cluster: %s" % (e))
    sys.exit(1)
