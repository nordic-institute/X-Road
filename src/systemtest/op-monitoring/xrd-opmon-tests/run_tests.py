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

# End-to-end system test for verifying the behaviour of the operational
# monitoring daemon.

# Expecting Python 3.4 under Ubuntu 14.04
# Additional dependencies:
# sudo apt-get install python3-requests
# sudo apt-get install python3-pip
# sudo pip3 install typing

# Expecting key-based SSH access to the security servers and
# passwordless sudo inside the security servers.
# Use ~/.ssh/config for custom usernames and key paths.
# Alternatively, an SSH user can be supplied with the --ssh-user command
# line argument if the same user is suitable for running remote commands
# on all the servers.

import sys
import copy
import time
import socket
import argparse
import unittest
import traceback
import subprocess
from contextlib import contextmanager
from typing import Tuple
from testcases import *
import common

# Sleep time after proxy restart must be higher
SERVICE_RESTART_SLEEP_SECONDS = 5
PROXY_RESTART_SLEEP_SECONDS = 15

# Must be the same value as in test_health_data.py
STATISTICS_PERIOD_SECONDS = 20

REQUEST_TEMPLATE_DIR = "templates"

LOCAL_INI_PATH = "/etc/xroad/conf.d/local.ini"

LOCAL_INI_PARAMETERS = {
    # The default offset is a minute to support heavy loads, but we want
    # to be able to get the operational data records pertaining to our
    # tests without waiting too long.
    "op-monitor.records-available-timestamp-offset-seconds": 0,
    # Make sure all the records are returned unless we reconfigure
    # the daemon explicitly.
    "op-monitor.max-records-in-payload": 100,
}

# Default values for queries
QUERY_PARAMETERS = {
    "producer_instance": "INST",
    "producer_class": "GOV",
    "producer_code": "00000000",
    "producer_system": "System0",
    "producer_server_code": "00000000_1",
    "producer_server_address": "xrd0.ss.dns",
    "producer_server_ip": "0.0.0.0",
    "client_instance": "INST",
    "client_class": "GOV",
    "client_code": "00000001",
    "client_system": "System1",
    "client_monitor_system": "Central monitoring",
    "client_server_code": "00000001_1",
    "client_server_address": "xrd1.ss.dns",
    "client_server_ip": "0.0.0.0",
    "producer2_instance": "INST",
    "producer2_class": "COM",
    "producer2_code": "00000002",
    "producer2_system": "System2",
    "producer2_server_code": "00000002_1",
    "producer2_server_address": "xrd2.ss.dns",
    "ssh_user": None
}


class OperationalMonitoringIntegrationTest(unittest.TestCase):
    def test_simple_store_and_query(self):
        self._run_test(test_simple_store_and_query, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_soap_fault(self):
        self._run_test(test_soap_fault, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_get_metadata(self):
        self._run_test(test_get_metadata, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_metaservices(self):
        self._run_test(test_metaservices, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_attachments(self):
        self._run_test(test_attachments, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_health_data(self):
        ini_parameters = copy.deepcopy(LOCAL_INI_PARAMETERS)
        # Let the health statistics period be reset in a reasonable
        # period in the context of the tests so we can check that
        # the values are reset, too.
        ini_parameters["op-monitor.health-statistics-period-seconds"] = STATISTICS_PERIOD_SECONDS
        self._run_test(test_health_data, ini_parameters, QUERY_PARAMETERS)

    def test_limited_operational_data_response(self):
        ini_parameters = copy.deepcopy(LOCAL_INI_PARAMETERS)
        # This is to check if sending operational data in multiple
        # batches works as expected.
        ini_parameters["op-monitor.max-records-in-payload"] = 2
        ini_parameters["op-monitor.records-available-timestamp-offset-seconds"] = 2
        self._run_test(test_limited_operational_data_response, ini_parameters, QUERY_PARAMETERS)

    def test_service_cluster(self):
        self._run_test(test_service_cluster, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_outputspec(self):
        self._run_test(test_outputspec, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_time_interval(self):
        self._run_test(test_time_interval, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_client_filter(self):
        self._run_test(test_client_filter, LOCAL_INI_PARAMETERS, QUERY_PARAMETERS)

    def test_zero_buffer_size(self):
        ini_parameters = copy.deepcopy(LOCAL_INI_PARAMETERS)
        # This is to check that setting operational monitoring buffer
        # size to zero results with operational data not being sent to
        # the operational monitoring daemon.
        ini_parameters["op-monitor-buffer.size"] = 0
        self._run_test(test_zero_buffer_size, ini_parameters, QUERY_PARAMETERS)

    @staticmethod
    def _run_test(testcase_module_name, ini_parameters, query_parameters):
        # Wait before starting the test case to avoid getting
        # operational monitoring data of previous testcases in
        # the result of the operational data requests made in this test.
        time.sleep(1)

        exit_status = 0
        with configure_and_restart_opmonitor(ini_parameters, query_parameters):
            try:
                getattr(testcase_module_name, "run")(REQUEST_TEMPLATE_DIR, query_parameters)

            except Exception as e:
                print("An exception occurred: {}".format(e))
                traceback.print_exc()
                exit_status = 1
                # Let the context manager restore the configuration and
                # restart the servers before we exit.

        if exit_status != 0:
            sys.exit(1)


def _get_initial_ini_parameters(
        server_addresses: Tuple, ssh_user: str, target_ini_parameter_keys: Tuple):
    """ Helper for getting the values of the target ini parameters from
    the given servers.
    """
    initial_parameters = dict()

    for param_key in target_ini_parameter_keys:
        param_parts = param_key.split('.')
        ini_section = param_parts[0]
        parameter = param_parts[1]
        command = "sudo crudini --get {} {} {}".format(LOCAL_INI_PATH, ini_section, parameter)
        for server_address in server_addresses:
            user_and_server = common.generate_user_and_server(server_address, ssh_user)
            param_and_server = (param_key, server_address)
            try:
                param_value = subprocess.check_output(["ssh", user_and_server, command, ])
                value = param_value.strip().decode('utf-8')
                initial_parameters[param_and_server] = value
            except subprocess.CalledProcessError as e:
                if e.returncode == 1:
                    print("No existing value was found for the parameter '{}' "
                          "in security server {} \n".format(param_key, server_address))
                    initial_parameters[param_and_server] = None
                    continue
                print(e)
                sys.exit(1)

    return initial_parameters


def _configure_ini_parameters(
        server_addresses: Tuple, target_ini_parameters: dict,
        initial_parameters: dict, ssh_user: str, mode: str):
    """ Helper for reconfiguring the servers before and after the tests.
    
    Use mode="edit" for initial editing and mode="restore" for restoring
    the configuration.
    """
    service_restarted = False
    proxy_restarted = False
    if mode == "restore":
        activity = "Restoring"
    elif mode == "edit":
        activity = "Editing"
    else:
        raise Exception("Programming error: only edit and restore modes are available")

    for server_address in server_addresses:
        opmonitor_needs_restart = False
        proxy_needs_restart = False
        for param, value in target_ini_parameters.items():
            initial_value = initial_parameters[(param, server_address)]
            if str(target_ini_parameters[param]) != str(initial_value):
                param_parts = param.split('.')
                ini_section = param_parts[0]
                parameter = param_parts[1]
                if ini_section == "op-monitor":
                    opmonitor_needs_restart = True
                    target = "daemon"
                elif ini_section == "op-monitor-buffer":
                    proxy_needs_restart = True
                    target = "buffer"
                else:
                    raise Exception("Programming error: unsupported ini section name")
                print("\n{} the configuration parameters of the "
                      "operational monitoring {} in security server {}".format(
                        activity, target, server_address))

                if mode == "restore" and initial_value is None:
                    print("Removing '{}'".format(param))
                    command = "sudo crudini --del {} {} {}".format(
                        LOCAL_INI_PATH, ini_section, parameter)
                else:
                    value_to_set = value
                    if mode == "restore":
                        value_to_set = initial_value
                    print("Setting '{}' to '{}'".format(param, value_to_set))
                    command = "sudo crudini --set {} {} {} {}".format(
                        LOCAL_INI_PATH, ini_section, parameter, value_to_set)
                user_and_server = common.generate_user_and_server(
                    server_address, ssh_user)
                try:
                    subprocess.check_call(["ssh", user_and_server, command, ])
                except subprocess.CalledProcessError as e:
                    print(e)
                    # This will leave the configuration files in
                    # an undefined state.
                    sys.exit(1)

        if opmonitor_needs_restart:
            common.restart_service(server_address, "opmonitor", ssh_user)
            service_restarted = True
        if proxy_needs_restart:
            common.restart_service(server_address, "proxy", ssh_user)
            proxy_restarted = True

    # In case any services were restarted, wait a bit so the requests to
    # the operational monitoring daemon succeed again.
    if proxy_restarted:
        print("Sleeping for {} seconds after the restart of proxies".format(
            PROXY_RESTART_SLEEP_SECONDS))
        time.sleep(PROXY_RESTART_SLEEP_SECONDS)
    elif service_restarted:
        print("Sleeping for {} seconds after the restart of services".format(
            SERVICE_RESTART_SLEEP_SECONDS))
        time.sleep(SERVICE_RESTART_SLEEP_SECONDS)


@contextmanager
def configure_and_restart_opmonitor(local_ini_parameters: dict, query_parameters: dict):
    """ A wrapper to be used with the with-keyword upon specific
    configuration needs.

    Before the wrapped test is run, the configuration of the operational
    monitoring daemon and/or the proxy is changed and the services are
    restarted in all the given servers, if necessary.
    After the test has been run, the initial parameters (if any were
    found) are restored, otherwise they are removed, and the services
    are restarted again, if any initial changes were made.

    NOTE: When using this function with the with-keyword, DO NOT call
    sys.exit before you are done with the with-block.
    """
    server_addresses = (
        query_parameters["producer_server_ip"], query_parameters["client_server_ip"])
    ssh_user = query_parameters["ssh_user"]

    # First put aside the original parameters that we are about to
    # change.
    initial_parameters = _get_initial_ini_parameters(
        server_addresses, ssh_user, tuple(local_ini_parameters.keys()))

    # Now edit the parameters if necessary.
    _configure_ini_parameters(
        server_addresses, local_ini_parameters, initial_parameters, ssh_user, "edit")

    # Pass control to the caller for running the test case.
    yield

    # Now restore the parameters if necessary.
    _configure_ini_parameters(
        server_addresses, local_ini_parameters, initial_parameters, ssh_user, "restore")


def _resolve_address(name_or_address):
    """ Resolve the name or address given by the user to a valid IPv4
    address.
    """
    try:
        addr_info = socket.getaddrinfo(
            name_or_address, port=80, family=socket.AddressFamily.AF_INET)
        # The function returns a list of 5-tuples with the address as
        # a 2-tuple
        return addr_info[0][4][0]
    except Exception:
        print("The server name '{}' cannot be resolved".format(name_or_address))
        raise


if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument("--producer-server-address", required=False)
    argparser.add_argument("--client-server-address", required=False)
    argparser.add_argument("--producer2-server-address", required=False)
    argparser.add_argument("--ssh-user", required=False)
    argparser.add_argument("--producer-system", required=False)
    argparser.add_argument("--client-system", required=False)
    argparser.add_argument("--producer2-system", required=False)
    argparser.add_argument("--client-monitor-system", required=False)
    argparser.add_argument("--producer-server-code", required=False)
    argparser.add_argument("--client-server-code", required=False)
    argparser.add_argument("--producer2-server-code", required=False)
    argparser.add_argument("--service-restart-sleep-seconds", type=int)
    argparser.add_argument("--proxy-restart-sleep-seconds", type=int)
    # Capture the rest of the arguments for passing to unittest.
    argparser.add_argument('unittest_args', nargs='*')
    args = argparser.parse_args()

    # Overriding QUERY_PARAMETERS defaults.
    if args.producer_server_address:
        QUERY_PARAMETERS["producer_server_address"] = args.producer_server_address
    if args.client_server_address:
        QUERY_PARAMETERS["client_server_address"] = args.client_server_address
    if args.producer2_server_address:
        QUERY_PARAMETERS["producer2_server_address"] = args.producer2_server_address
    if args.ssh_user:
        QUERY_PARAMETERS["ssh_user"] = args.ssh_user
    if args.producer_system:
        system = args.producer_system.split("/")
        QUERY_PARAMETERS["producer_instance"] = system[0]
        QUERY_PARAMETERS["producer_class"] = system[1]
        QUERY_PARAMETERS["producer_code"] = system[2]
        QUERY_PARAMETERS["producer_system"] = system[3]
    if args.client_system:
        system = args.client_system.split("/")
        QUERY_PARAMETERS["client_instance"] = system[0]
        QUERY_PARAMETERS["client_class"] = system[1]
        QUERY_PARAMETERS["client_code"] = system[2]
        QUERY_PARAMETERS["client_system"] = system[3]
    if args.producer2_system:
        system = args.producer2_system.split("/")
        QUERY_PARAMETERS["producer2_instance"] = system[0]
        QUERY_PARAMETERS["producer2_class"] = system[1]
        QUERY_PARAMETERS["producer2_code"] = system[2]
        QUERY_PARAMETERS["producer2_system"] = system[3]
    if args.client_monitor_system:
        QUERY_PARAMETERS["client_monitor_system"] = args.client_monitor_system
    if args.producer_server_code:
        QUERY_PARAMETERS["producer_server_code"] = args.producer_server_code
    if args.client_server_code:
        QUERY_PARAMETERS["client_server_code"] = args.client_server_code
    if args.producer2_server_code:
        QUERY_PARAMETERS["producer2_server_code"] = args.producer2_server_code
    if args.service_restart_sleep_seconds:
        SERVICE_RESTART_SLEEP_SECONDS = args.service_restart_sleep_seconds
    if args.proxy_restart_sleep_seconds:
        PROXY_RESTART_SLEEP_SECONDS = args.proxy_restart_sleep_seconds

    # Sleep time after proxy restart must be higher
    if SERVICE_RESTART_SLEEP_SECONDS > PROXY_RESTART_SLEEP_SECONDS:
        PROXY_RESTART_SLEEP_SECONDS = SERVICE_RESTART_SLEEP_SECONDS

    QUERY_PARAMETERS["producer_server_ip"] = _resolve_address(
        QUERY_PARAMETERS["producer_server_address"])
    QUERY_PARAMETERS["client_server_ip"] = _resolve_address(QUERY_PARAMETERS["client_server_address"])

    # Pass the rest of the command line arguments to unittest.
    unittest_args = [sys.argv[0]] + args.unittest_args
    unittest.main(argv=unittest_args, verbosity=2)
