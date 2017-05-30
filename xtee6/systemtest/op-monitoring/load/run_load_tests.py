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

# Wrapper script for running load tests using various XRoad request templates.

# The load tests use the Gatling framework. Versions 2.2.2 and 2.2.3 have been
# used for testing.
# NOTE: Make sure the gatling.sh script is in your path -- shell aliases do not
# work.

import os
import sys
import time
import argparse
import subprocess

# Use a different output directory for each load test run so we can compare
# the results. We pass this value to gatling and set it as a system property
# so the same value is available to Gatling and in our Scala source.
REPORTS_OUTPUT_DIR = os.path.abspath("load_test_results_" + str(int(time.time())))

# Because Gatling may be installed globally, the output directory of the compiled
# binary files must be overridden.
BUILD_OUTPUT_DIR = os.path.abspath(".")

# All the message ID-s generated for the requests will be stored in the following
# file for further analysis.
# NOTE: If you change this constant, it must be changed in SimulationUtil.scala
# as well.
GENERATED_MESSAGE_ID_FILE = "generated_message_ids"

SIMULATION_SETUP_SOURCE_FILE = "SimulationSetup.scala"

# Read the command line arguments and override the defaults if applicable.
argparser = argparse.ArgumentParser()
argparser.add_argument("--client-security-server", required=True,
        dest="client_security_server")
argparser.add_argument("--class-output-dir", dest="class_output_dir",
        help="The directory for compiled binary classes, defaults to the current directory.")
argparser.add_argument("--results-output-dir", dest="results_output_dir",
        help="The destination base directory for the generated reports, defaults to" \
                " the current directory. The reports of each simulation will be written" \
                " to a subdirectory load_test_results_<timestamp>.")
args = argparser.parse_args()

CLIENT_SECURITY_SERVER_ADDRESS = args.client_security_server
if args.class_output_dir:
    BUILD_OUTPUT_DIR = args.class_output_dir
if args.results_output_dir:
    REPORTS_OUTPUT_DIR = args.results_output_dir

GATLING_COMMAND = \
        "gatling.sh -m --simulations-folder . --binaries-folder %s " \
        "--results-folder %s -s opmonitor.loadtesting.XRoadLoadSimulation" % (
                BUILD_OUTPUT_DIR,
                REPORTS_OUTPUT_DIR,
)

# The results system property corresponds to the -rf option by Gatling and will
# be available in Scala source.
JAVA_OPTS="-Dclient_security_server_url=%s -Dresults=%s" % (
        CLIENT_SECURITY_SERVER_ADDRESS,
        REPORTS_OUTPUT_DIR,
)

env = os.environ.copy()
env["JAVA_OPTS"] = JAVA_OPTS

# Create the output directory and the file for writing the generated message
# IDs upon each request sent.
os.system("mkdir -p " + REPORTS_OUTPUT_DIR)
os.system("touch %s" % (
    os.path.join(REPORTS_OUTPUT_DIR, GENERATED_MESSAGE_ID_FILE)))

# Insert the simulation setup if found in the environment.
simulation_setup = env.get("SIMULATION_SETUP")
if simulation_setup:
    print("The following simulation setup was read from the contents of $SIMULATION_SETUP:\n")
    print(simulation_setup)
    with open(SIMULATION_SETUP_SOURCE_FILE, "w") as f:
        f.write(simulation_setup)

# Now compile and run the load tests.
try:
    subprocess.check_call(GATLING_COMMAND, env=env, shell=True)
    print("The report was written to " + REPORTS_OUTPUT_DIR)
    sys.exit(0)
except Exception as e:
    print("An error occured when compiling or running the load tests: %s" % (e, ))
    sys.exit(1)
