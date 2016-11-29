#!/usr/bin/env python3

# Wrapper script for running load tests using various XRoad request templates.

# FIXME: for each simulation, check the operational database for records with
# all the message ID-s generated during the simulation.

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
GATLING_OUTPUT_DIR = "load_test_results_" + str(int(time.time()))

# This is the raw output file of Gatling that we can analyze for mean response
# times etc. It will be written to the configured output directory.
GATLING_SIMULATION_LOG_FILE = "simulation.log"

# All the message ID-s generated for the requests will be stored in the following
# file for further analysis.
# NOTE: If you change this constant, it must be changed in SimulationUtil.scala
# as well.
GENERATED_MESSAGE_ID_FILE = "generated_message_ids"

SIMULATION_SETUP_SOURCE_FILE = "SimulationSetup.scala"

GATLING_COMMAND = \
        "gatling.sh -m -sf . -rf %s -s opmonitor.loadtesting.XRoadLoadSimulation" % (
                GATLING_OUTPUT_DIR, )

# The address of the target security server must be given on the command line.
argparser = argparse.ArgumentParser()
argparser.add_argument("--client-security-server", required=True,
        dest="client_security_server")
args = argparser.parse_args()

CLIENT_SECURITY_SERVER_ADDRESS = args.client_security_server

# The results system property corresponds to the -rf option by Gatling and will
# be available in Scala source.
JAVA_OPTS="-Dclient_security_server_url=%s -Dresults=%s" % (
        CLIENT_SECURITY_SERVER_ADDRESS,
        GATLING_OUTPUT_DIR,)

env = os.environ.copy()
env["JAVA_OPTS"] = JAVA_OPTS

# Create the output directory and the file for writing the generated message
# IDs upon each request sent.
os.system("mkdir -p " + GATLING_OUTPUT_DIR)
os.system("touch %s" % (
    os.path.join(GATLING_OUTPUT_DIR, GENERATED_MESSAGE_ID_FILE)))

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
    print("The output files were written to " + GATLING_OUTPUT_DIR)
    sys.exit(0)
except Exception as e:
    print("An error occured when compiling or running the load tests: %s" % (e, ))
    sys.exit(1)
