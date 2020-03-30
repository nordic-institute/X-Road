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

# A sample script for configuring one or more sets of Zabbix applications and
# items related to X-Road services registered by the operational monitoring daemon.
# This script uses the Zabbix API. See https://www.zabbix.com/documentation/3.0/manual/api

# Prerequisites:
# - A host object related to each operational monitoring daemon has been configured in a
#    corresponding Zabbix instance (either a local or a central Zabbix)
# - A JMX interface has been configured for each such host object in its corresponding
#    Zabbix instance.
# - An authentication key has been obtained using the user.login JSON-RPC method of
#    the Zabbix API.
# - The user of the script has obtained the IDs of the host objects via the UI of
#    Zabbix or using the Zabbix API.
# - The file zabbix_hosts_and_services.json has been created in this directory,
#   based on the sample file zabbix_hosts_and_services.json.sample, and edited
#   accordingly.

# Dependencies:
# python-requests (or equivalent) for Python 2.7

import sys
import json
import random
import requests

# Create this file based on the sample file and edit it to match your Zabbixes
# and services.
HOST_DATA_FILE = "zabbix_hosts_and_services.json"
HOST_DATA_SAMPLE_FILE = HOST_DATA_FILE + ".sample"

ZABBIX_FLOAT = 0 # numeric float
ZABBIX_UNSIGNED = 3 # numeric unsigned
ZABBIX_DEFAULT_POLL_PERIOD_SEC = 30
ZABBIX_API_ERROR_INVALID_PARAMS = -32602

# The components of JMX items we need as part of the item keys, the corresponding
# human-readable item names and the data types for configuring each item in Zabbix.
HEALTH_DATA_ITEMS_PER_HOST = (
    ("monitoringStartupTimestamp", "Monitoring startup timestamp",
        "Value", ZABBIX_UNSIGNED),
    ("statisticsPeriodSeconds", "Statistics period (seconds)",
        "Value", ZABBIX_UNSIGNED),
)
HEALTH_DATA_ITEMS_PER_SERVICE = (
    ("successfulRequestCount", "Successful request count", "Count", ZABBIX_UNSIGNED),
    ("unsuccessfulRequestCount", "Unsuccessful request count", "Count", ZABBIX_UNSIGNED),
    ("lastSuccessfulRequestTimestamp", "Last successful request timestamp",
        "Value", ZABBIX_UNSIGNED),
    ("lastUnsuccessfulRequestTimestamp", "Last unsuccessful request timestamp",
        "Value", ZABBIX_UNSIGNED),
    ("requestDuration", "Duration of request (min)", "Min", ZABBIX_UNSIGNED),
    ("requestDuration", "Duration of request (mean)", "Mean", ZABBIX_FLOAT),
    ("requestDuration", "Duration of request (max)", "Max", ZABBIX_UNSIGNED),
    ("requestDuration", "Duration of request (std dev)", "StdDev", ZABBIX_FLOAT),
    ("requestSoapSize", "SOAP size of request (min)", "Min", ZABBIX_UNSIGNED),
    ("requestSoapSize", "SOAP size of request (mean)", "Mean", ZABBIX_FLOAT),
    ("requestSoapSize", "SOAP size of request (max)", "Max", ZABBIX_UNSIGNED),
    ("requestSoapSize", "SOAP size of request (std dev)", "StdDev", ZABBIX_FLOAT),
    ("responseSoapSize", "SOAP size of response (min)", "Min", ZABBIX_UNSIGNED),
    ("responseSoapSize", "SOAP size of response (mean)", "Mean", ZABBIX_FLOAT),
    ("responseSoapSize", "SOAP size of response (max)", "Max", ZABBIX_UNSIGNED),
    ("responseSoapSize", "SOAP size of response (std dev)", "StdDev", ZABBIX_FLOAT),
)

# Sample item: jmx[metrics:name=monitoringStartupTimestamp, Value]
JMX_HOST_WIDE_ITEM_KEY_TEMPLATE = "jmx[metrics:name=%s, %s]"
# Sample item:
# jmx[metrics:name=requestDuration(XTEE-CI-XM/GOV/00000000/Center/xroadGetRandom/v1), Mean]
JMX_SERVICE_ITEM_KEY_TEMPLATE = "jmx[metrics:name=%s(%s), %s]"

def _format_create_application_request(service, host_id, auth_key):
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "application.create",
            "params": {
                "name": service,
                "hostid": host_id,
            },
            "auth": auth_key,
            "id": random.randint(1, 100),
        }
    )

def _format_get_application_request(service, host_id, auth_key):
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "application.get",
            "params": {
                "hostids": host_id,
                "search": {
                    "name": service,
                },
            },
            "auth": auth_key,
            "id": random.randint(1, 100),
        }
    )

# Interface type 4 stands for JMX agent.
def _format_get_jmx_interface_request(host_id, auth_key):
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "hostinterface.get",
            "params": {
                "hostids": host_id,
                "filter": {
                    "type": 4,
                }
             },
            "auth": auth_key,
            "id": random.randint(1, 100),
        }
    )

# Item type 16 stands for JMX item.
# The value of value_type will be set based on the type of each JMX parameter.
def _format_create_item_request(
        item_name, item_key, host_id, application_ids, value_type,
        interface_id, auth_key, jmx_username, jmx_password):
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "item.create",
            "params": {
                "name": item_name,
                "key_": item_key,
                "hostid": host_id,
                "applications": application_ids,
                "type": 16,
                "value_type": value_type,
                "interfaceid": interface_id,
                "delay": ZABBIX_DEFAULT_POLL_PERIOD_SEC,
                "username": jmx_username,
                "password": jmx_password,
                },
            "auth": auth_key,
            "id": random.randint(1, 100),
        }
    )

def _post_json_rpc_request(zabbix_api_url, data):
    response = requests.post(
            zabbix_api_url, headers={"Content-type": "application/json"}, data=data,
            # Support self-signed certificates in Zabbix instances
            verify=False)
    response_json = json.loads(response.text)

    if response_json.get("error"):
        print "Received the following error in the response:"
        print response_json.get("error")
    else:
        print "Result of the request:"
        print response_json.get("result")

    # Let the caller process the result further.
    return response_json

monitored_hosts = None
try:
    with open(HOST_DATA_FILE, "r") as host_data_file:
        monitored_hosts = json.loads(host_data_file.read())
except Exception as e:
    print "Failed to read the host data file '%s': %s" % (HOST_DATA_FILE, e, )
    print "Please copy '%s' and edit it to your needs" % (HOST_DATA_SAMPLE_FILE, )
    sys.exit(1)

for host in monitored_hosts:
    # First get the ID of the JMX interface.
    _data = _format_get_jmx_interface_request(
            host.get("zabbix_host_id"), host.get("zabbix_auth_key"))
    result = _post_json_rpc_request(host.get("target_zabbix_api_url"), _data)

    if result.get("error"):
        print "Failed to query the JMX interface for host '%s'" % (
            host.get("zabbix_host_id"), )
        # Perhaps there is an error in the query.
        sys.exit(1)

    if not result.get("result"):
        # We don't create the JMX interface automatically, this must be configured
        # manually or using our sample host data as a template.
        print "No JMX interfaces (type 4) were found for host '%s'" % (
            host.get("zabbix_host_id"), )
        sys.exit(1)

    jmx_interface_id = result.get("result")[0].get("interfaceid")

    # Create the items that are not specific to a service.
    for item in HEALTH_DATA_ITEMS_PER_HOST:
        _item_name = item[1]
        _item_key = JMX_HOST_WIDE_ITEM_KEY_TEMPLATE % (item[0], item[2],)
        _value_type = item[3]
        _data = _format_create_item_request(
                _item_name, _item_key, host.get("zabbix_host_id"),
                [], _value_type, jmx_interface_id,
                host.get("zabbix_auth_key"),
                host.get("jmx_username"), host.get("jmx_password"))
        result = _post_json_rpc_request(host.get("target_zabbix_api_url"), _data)

    # Create the configured applications and service-specific items.
    for _service in host.get("services"):
        target_application_id = None

        print"Trying to add an application for service '%s'" % (_service, )
        # Application names must be unique within a host only, so we can safely add
        # applications named by the same service to several hosts.
        _data = _format_create_application_request(
                _service, host.get("zabbix_host_id"), host.get("zabbix_auth_key"))
        result = _post_json_rpc_request(host.get("target_zabbix_api_url"), _data)

        if result.get("error", {}).get("code") == ZABBIX_API_ERROR_INVALID_PARAMS:
            # Check if the application has been created already.
            _data = _format_get_application_request(
                    _service, host.get("zabbix_host_id"), host.get("zabbix_auth_key"))
            result = _post_json_rpc_request(host.get("target_zabbix_api_url"), _data)
            target_application_id = result.get("result")[0].get("applicationid")
        else:
            target_application_id = result.get("result").get("applicationids")[0]

        if target_application_id is None:
            print "Failed to get the application ID for service '%s'" % (_service, )
            sys.exit(1)

        for item in HEALTH_DATA_ITEMS_PER_SERVICE:
            _item_name = item[1]
            _item_key = JMX_SERVICE_ITEM_KEY_TEMPLATE % (item[0], _service, item[2],)
            _value_type = item[3]
            _data = _format_create_item_request(
                    _item_name, _item_key, host.get("zabbix_host_id"),
                    [target_application_id], _value_type, jmx_interface_id,
                    host.get("zabbix_auth_key"),
                    host.get("jmx_username"), host.get("jmx_password"))
            result = _post_json_rpc_request(host.get("target_zabbix_api_url"), _data)
