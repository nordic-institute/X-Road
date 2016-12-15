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

# Expecting key-based SSH access to the security servers and passwordless sudo
# inside the security servers.
# Use ~/.ssh/config for custom usernames and key paths.
# Alternatively, an SSH user can be supplied with the --ssh-user command line
# argument if the same user is suitable for running remote commands on all
# the servers.

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

sys.path.append('.')
from testcases import *

sys.path.append('..')
import python_common as common

CLIENT_SECURITY_SERVER_ADDRESS = None
PRODUCER_SECURITY_SERVER_ADDRESS = None
SSH_USER = None
SERVICE_RESTART_SLEEP_SECONDS = 2 # can be overridden on the command line

REQUEST_TEMPLATE_DIR = "templates"

LOCAL_INI_PATH = "/etc/xroad/conf.d/local.ini"

LOCAL_INI_PARAMETERS = {
    # The default offset is a minute to support heavy loads, but we want to be able to
    # get the operational data records pertaining to our tests without waiting
    # too long.
    "op-monitor.records-available-timestamp-offset-seconds": 0,
    # Make sure all the records are returned unless we reconfigure the daemon
    # explicitly.
    "op-monitor.max-records-in-payload": 100,
}

class OperationalMonitoringIntegrationTest(unittest.TestCase):

    def test_simple_store_and_query(self):
        self._run_test(test_simple_store_and_query, LOCAL_INI_PARAMETERS)

    def test_soap_fault(self):
        self._run_test(test_soap_fault, LOCAL_INI_PARAMETERS)

    def test_get_metadata(self):
        self._run_test(test_get_metadata, LOCAL_INI_PARAMETERS)

    def test_metaservices(self):
        self._run_test(test_metaservices, LOCAL_INI_PARAMETERS)

    def test_attachments(self):
        self._run_test(test_attachments, LOCAL_INI_PARAMETERS)

    def test_health_data(self):
        ini_parameters = copy.deepcopy(LOCAL_INI_PARAMETERS)
        # Let the health statistics period be reset in a reasonable period in
        # the context of the tests so we can check that the values are reset,
        # too.
        ini_parameters["op-monitor.health-statistics-period-seconds"] = 10
        self._run_test(test_health_data, ini_parameters)

    def test_limited_operational_data_response(self):
        ini_parameters = copy.deepcopy(LOCAL_INI_PARAMETERS)
        # This is to check if sending operational data in multiple batches works
        # as expected.
        ini_parameters["op-monitor.max-records-in-payload"] = 2
        ini_parameters["op-monitor.records-available-timestamp-offset-seconds"] = 2
        self._run_test(test_limited_operational_data_response, ini_parameters)

    def test_service_cluster(self):
        self._run_test(test_service_cluster, LOCAL_INI_PARAMETERS)

    def test_outputspec(self):
        self._run_test(test_outputspec, LOCAL_INI_PARAMETERS)

    def test_time_interval(self):
        self._run_test(test_time_interval, LOCAL_INI_PARAMETERS)

    def test_client_filter(self):
        self._run_test(test_client_filter, LOCAL_INI_PARAMETERS)

    def test_zero_buffer_size(self):
        ini_parameters = copy.deepcopy(LOCAL_INI_PARAMETERS)
        # This is to check that setting operational monitoring buffer size
        # to zero results with operational data not being sent to the
        # operational monitoring daemon.
        ini_parameters["op-monitor-buffer.size"] = 0
        self._run_test(test_zero_buffer_size, ini_parameters)

    def _run_test(self, testcase_module_name, ini_parameters):
        # Wait before starting the test case to avoid getting operational monitoring data
        # of previous testcases in the result of the operational data requests made in
        # this test.
        time.sleep(1)

        exit_status = 0
        with configure_and_restart_opmonitor(
                (CLIENT_SECURITY_SERVER_ADDRESS, PRODUCER_SECURITY_SERVER_ADDRESS),
                SSH_USER, ini_parameters):
            try:
                getattr(testcase_module_name, "run")(
                    CLIENT_SECURITY_SERVER_ADDRESS, PRODUCER_SECURITY_SERVER_ADDRESS,
                    SSH_USER, REQUEST_TEMPLATE_DIR)

            except Exception as e:
                print("An exception occurred: %s" % (e, ))
                traceback.print_exc()
                exit_status = 1
                # Let the context manager restore the configuration and restart the
                # servers before we exit.

        if exit_status != 0:
            sys.exit(1)

def _get_initial_ini_parameters(
        server_addresses: Tuple, ssh_user: str, target_ini_parameter_keys: Tuple):
    """ Helper for getting the values of the target ini parameters from the given servers."""
    initial_parameters = dict()

    for param_key in target_ini_parameter_keys:
        param_parts = param_key.split('.')
        ini_section = param_parts[0]
        parameter = param_parts[1]
        command = "sudo crudini --get %s %s %s" % (LOCAL_INI_PATH, ini_section, parameter, )
        for server_address in server_addresses:
            user_and_server = common.generate_user_and_server(server_address, ssh_user)
            param_and_server = (param_key, server_address)
            try:
                param_value = subprocess.check_output(["ssh", user_and_server, command, ])
                value = param_value.strip().decode('utf-8')
                initial_parameters[param_and_server] = value
            except subprocess.CalledProcessError as e:
                if e.returncode == 1:
                    print("No existing value was found for the parameter '%s' " \
                          "in security server %s \n" % (param_key, server_address))
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
    activity = None
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
                print("\n%s the configuration parameters of the " \
                    "operational monitoring %s in security server %s" % 
                    (activity, target, server_address))

                command = None
                if mode == "restore" and initial_value is None:
                    print("Removing '%s'" % (param, ))
                    command = "sudo crudini --del %s %s %s" % (
                            LOCAL_INI_PATH, ini_section, parameter, )
                else:
                    value_to_set = value
                    if mode == "restore":
                        value_to_set = initial_value
                    print("Setting '%s' to '%s'" % (param, value_to_set,))
                    command = "sudo crudini --set %s %s %s %s" % (
                            LOCAL_INI_PATH, ini_section, parameter, value_to_set, )
                user_and_server = common.generate_user_and_server(
                        server_address, ssh_user)
                try:
                    subprocess.check_call(["ssh", user_and_server, command, ])
                except subprocess.CalledProcessError as e:
                    print(e)
                    # XXX This will leave the configuration files in an undefined state.
                    sys.exit(1)

        if opmonitor_needs_restart:
            common.restart_service(server_address, "opmonitor", ssh_user)
            service_restarted = True
        if proxy_needs_restart:
            common.restart_service(server_address, "proxy", ssh_user)
            service_restarted = True

    # In case any services were restarted, wait a bit so the requests to the 
    # operational monitoring daemon succeed again.
    if service_restarted:
        print("Sleeping for %d seconds after the restart of services" % (
            SERVICE_RESTART_SLEEP_SECONDS, ))
        time.sleep(SERVICE_RESTART_SLEEP_SECONDS)

@contextmanager
def configure_and_restart_opmonitor(
        server_addresses: Tuple, ssh_user: str, local_ini_parameters: dict):
    """ A wrapper to be used with the with-keyword upon specific configuration needs.

    Before the wrapped test is run, the configuration of the operational monitoring
    daemon and/or the proxy is changed and the services are restarted in all the
    given servers, if necessary.
    After the test has been run, the initial parameters (if any were found) are
    restored, otherwise they are removed, and the services are restarted again, if any
    initial changes were made.

    NOTE: When using this function with the with-keyword, DO NOT call sys.exit before
    you are done with the with-block.
    """
    # First put aside the original parameters that we are about to change.
    initial_parameters = _get_initial_ini_parameters(
            server_addresses, ssh_user, local_ini_parameters.keys())

    # Now edit the parameters if necessary.
    _configure_ini_parameters(
            server_addresses, local_ini_parameters, initial_parameters, ssh_user, "edit")

    # Pass control to the caller for running the test case.
    yield

    # Now restore the parameters if necessary.
    _configure_ini_parameters(
            server_addresses, local_ini_parameters, initial_parameters, ssh_user, "restore")

def _resolve_address(name_or_address):
    """ Resolve the name or address given by the user to a valid IPv4 address. """
    try:
        addr_info = socket.getaddrinfo(
                name_or_address, port=80, family=socket.AddressFamily.AF_INET)
        # The function returns a list of 5-tuples with the address as a 2-tuple
        return addr_info[0][4][0]
    except Exception as e:
        print("The server name '%s' cannot be resolved" % (name_or_address, ))
        raise

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument("--client-security-server", required=True,
            dest="client_security_server")
    argparser.add_argument("--producer-security-server", required=True,
            dest="producer_security_server")
    argparser.add_argument("--ssh-user", required=False,
            dest="ssh_user")
    argparser.add_argument("--service-restart-sleep-seconds",
            type=int, dest="service_restart_sleep_seconds")
    # Capture the rest of the arguments for passing to unittest.
    argparser.add_argument('unittest_args', nargs='*')
    args = argparser.parse_args()
    
    CLIENT_SECURITY_SERVER_ADDRESS = _resolve_address(args.client_security_server)
    PRODUCER_SECURITY_SERVER_ADDRESS = _resolve_address(args.producer_security_server)
    SSH_USER = args.ssh_user
    if args.service_restart_sleep_seconds:
        SERVICE_RESTART_SLEEP_SECONDS = args.service_restart_sleep_seconds

    # Pass the rest of the command line arguments to unittest.
    unittest_args = [sys.argv[0]] + args.unittest_args
    unittest.main(argv=unittest_args, verbosity=2)
