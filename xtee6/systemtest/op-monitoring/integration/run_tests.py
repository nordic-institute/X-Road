#!/usr/bin/env python3

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

import sys
import copy
import time
import argparse
import unittest
import subprocess

from contextlib import contextmanager
from typing import Tuple

sys.path.append('.')
from testcases import *

CLIENT_SECURITY_SERVER_ADDRESS = None
PRODUCER_SECURITY_SERVER_ADDRESS = None
REQUEST_TEMPLATE_DIR = "templates"

OP_MONITOR_LOCAL_INI_PATH = "/etc/xroad/conf.d/local.ini"
OP_MONITOR_INI_SECTION = "op-monitor"

# The default offset is a minute to support heavy loads, but we want to be able to
# get the operational data records pertaining to our tests without waiting
# too long.
OP_MONITOR_INI_PARAMETERS = {
    "records-available-timestamp-offset-seconds": 0,
}
# TODO: set op-monitor-buffer.sending-interval-seconds to 1

class OperationalMonitoringIntegrationTest(unittest.TestCase):

    def test_simple_store_and_query(self):
        self._run_test(test_simple_store_and_query, OP_MONITOR_INI_PARAMETERS)

    def test_soap_fault(self):
        self._run_test(test_soap_fault, OP_MONITOR_INI_PARAMETERS)

    def test_get_metadata(self):
        self._run_test(test_get_metadata, OP_MONITOR_INI_PARAMETERS)

    def test_metaservices(self):
        self._run_test(test_metaservices, OP_MONITOR_INI_PARAMETERS)

    def test_attachments(self):
        self._run_test(test_attachments, OP_MONITOR_INI_PARAMETERS)

    def test_health_data(self):
        ini_parameters = copy.deepcopy(OP_MONITOR_INI_PARAMETERS)
        # Let the health statistics period be reset in a reasonable period in
        # the context of the tests so we can check that the values are reset,
        # too.
        ini_parameters["health-statistics-period-seconds"] = 10
        self._run_test(test_health_data, ini_parameters)

    def test_limited_operational_data_response(self):
        ini_parameters = copy.deepcopy(OP_MONITOR_INI_PARAMETERS)
        # This is to check if sending operational data in multiple batches works
        # as expected.
        ini_parameters["max-records-in-payload"] = 2
        ini_parameters["records-available-timestamp-offset-seconds"] = 2
        self._run_test(test_limited_operational_data_response, ini_parameters)

    def test_service_cluster(self):
        self._run_test(test_service_cluster, OP_MONITOR_INI_PARAMETERS)

    def test_outputspec(self):
        self._run_test(test_outputspec, OP_MONITOR_INI_PARAMETERS)

    def test_time_interval(self):
        self._run_test(test_time_interval, OP_MONITOR_INI_PARAMETERS)

    def _run_test(self, testcase_module_name, ini_parameters):
        # Wait before starting the test case to avoid getting operational monitoring data
        # of previous testcases in the result of the operational data requests made in
        # this test.
        time.sleep(7)

        exit_status = 0
        with configure_and_restart_opmonitor(
                (CLIENT_SECURITY_SERVER_ADDRESS, PRODUCER_SECURITY_SERVER_ADDRESS ),
                ini_parameters):
            try:
                getattr(testcase_module_name, "run")(
                    CLIENT_SECURITY_SERVER_ADDRESS, PRODUCER_SECURITY_SERVER_ADDRESS,
                    REQUEST_TEMPLATE_DIR)

            except Exception as e:
                print("An exception occurred: %s" % (e, ))
                exit_status = 1
                # Let the context manager restore the configuration and restart the
                # servers before we exit.

        if exit_status != 0:
            sys.exit(1)

@contextmanager
def configure_and_restart_opmonitor(
        server_addresses: Tuple, local_ini_parameters: dict):
    """ A wrapper to be used with the with-keyword upon specific configuration needs.

    Before the wrapped test is run, the configuration of the operational monitoring
    daemon is changed and the daemon is restarted in all the given servers, if
    necessary.
    After the test has been run, the initial parameters (if any were found) are
    restored, otherwise they are removed, and the daemon is restarted again, if any
    initial changes were made.

    NOTE: When using this function with the with-keyword, DO NOT call sys.exit before
    you are done with the with-block.
    """

    initial_parameters = dict()

    # First put aside the original parameters that we are about to change.
    for param, value in local_ini_parameters.items():
        command = "sudo crudini --get %s %s %s" % (
            OP_MONITOR_LOCAL_INI_PATH, OP_MONITOR_INI_SECTION, param, )
        for server_address in server_addresses:
            try:
                param_value = subprocess.check_output(
                    ["ssh", server_address, command, ])
                param_and_server = (param, server_address)
                value = param_value.strip().decode('utf-8')
                initial_parameters[param_and_server] = value
            except subprocess.CalledProcessError as e:
                if e.returncode == 1:
                    print("\nNo existing value was found for the parameter '%s' " \
                          "in security server %s" % (param, server_address))
                    param_and_server = (param, server_address)
                    initial_parameters[param_and_server] = None
                    continue
                print(e)
                sys.exit(1)

    # Now edit the parameters if necessary.
    server_restarted = False
    for server_address in server_addresses:
        server_needs_restart = False
        for param, value in local_ini_parameters.items():
            initial_value = str(initial_parameters[(param, server_address)])
            if str(local_ini_parameters[param]) != initial_value:
                server_needs_restart = True
                print("\nEditing the configuration parameters of the " \
                    "operational monitoring daemon in security server %s" % 
                    (server_address))
                print("Setting '%s' to '%s'" % (param, value,))
                command = "sudo crudini --set %s %s %s %s" % (
                        OP_MONITOR_LOCAL_INI_PATH, OP_MONITOR_INI_SECTION,
                        param, value, )
                try:
                    subprocess.check_call(["ssh", server_address, command, ])
                except subprocess.CalledProcessError as e:
                    print(e)
                    # XXX This will leave the configuration files in an undefined state.
                    sys.exit(1)

        if server_needs_restart:
            print("\nRestarting the operational monitoring daemon in " \
                "security server %s" % (server_address))
            command = "sudo service xroad-opmonitor restart"
            server_restarted = True
            try:
                subprocess.check_call(["ssh", server_address, command, ])
            except subprocess.CalledProcessError as e:
                print(e)
                sys.exit(1)

    # In case any servers were restarted, wait a bit so the requests to the 
    # operational monitoring daemon succeed again.
    if server_restarted:
        time.sleep(2)

    # Pass control to the caller for running the test case.
    yield

    # Now restore the parameters if necessary.
    for server_address in server_addresses:
        server_needs_restart = False
        for param, value in local_ini_parameters.items():
            initial_value = initial_parameters[(param, server_address)]
            if str(local_ini_parameters[param]) != str(initial_value):
                server_needs_restart = True
                print("\nRestoring the configuration parameters of the " \
                      "operational monitoring daemon in security server %s" % 
                      (server_address))
                command = None
                if initial_value is None:
                    print("Removing '%s'" % (param, ))
                    command = "sudo crudini --del %s %s %s" % (
                            OP_MONITOR_LOCAL_INI_PATH, OP_MONITOR_INI_SECTION,
                            param, )
                else:
                    print("Setting '%s' to '%s'" % (param, initial_value,))
                    command = "sudo crudini --set %s %s %s %s" % (
                            OP_MONITOR_LOCAL_INI_PATH, OP_MONITOR_INI_SECTION,
                            param, initial_value, )
                try:
                    subprocess.check_call(["ssh", server_address, command, ])
                except subprocess.CalledProcessError as e:
                    print(e)
                    # XXX This will leave the configuration files in an undefined state.
                    sys.exit(1)

        if server_needs_restart:
            print("\nRestarting the operational monitoring daemon in " \
                  "security server %s" % (server_address))
            command = "sudo service xroad-opmonitor restart"
            try:
                subprocess.check_call(["ssh", server_address, command, ])
            except subprocess.CalledProcessError as e:
                print(e)
                sys.exit(1)

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument("--client-security-server", required=True,
            dest="client_security_server")
    argparser.add_argument("--producer-security-server", required=True,
            dest="producer_security_server")
    # Capture the rest of the arguments for passing to unittest.
    argparser.add_argument('unittest_args', nargs='*')
    args = argparser.parse_args()
    
    CLIENT_SECURITY_SERVER_ADDRESS = args.client_security_server
    PRODUCER_SECURITY_SERVER_ADDRESS = args.producer_security_server

    # Pass the rest of the command line arguments to unittest.
    unittest_args = [sys.argv[0]] + args.unittest_args
    unittest.main(argv=unittest_args, verbosity=2)
