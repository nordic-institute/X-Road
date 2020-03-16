# X-Road operational monitoring integration tests

## Installation

The integration test suite has the following dependencies:

Python 3.x under Ubuntu 18.04

Additional dependencies:
```
sudo apt-get install python3-requests
sudo apt-get install python3-pip
sudo pip3 install typing
```

Expecting key-based SSH access to the security servers and passwordless
sudo inside the security servers.
Use `~/.ssh/config` for custom usernames and key paths.
Alternatively, an SSH user can be supplied with the `--ssh-user` command
line argument if the same user is suitable for running remote commands
on all the servers.

The general idea is to make various regular X-Road (SOAP) requests and
check operational data and health data using the respective SOAP
requests. Direct database access is not used for checking the results
of requests.

## X-Road instance configuration

The tests assume the following environment has been set up.

You will need to have 2 or 3 fully configured security servers.


First server (Server0) must have two members (Member0 and Member2) and
each of the members must have one subsystem (System0 and System2).

Second server (Server1) must have one member (Member1) which has two
subsystems (System1 and CentralMonitoringSystem).

Third server is optional and is used to test clustered services (the
same subsystem is registered in two security servers). It is possible
to simplify setup by registering Member2 into Server1, or to configure
test to use the Server0 for both nodes.
But for best test results third server (Server2) must have one member
(Member2) which has one subsystem (System2).


```
Server0
    Member0
        System0 (Producer)
    Member2
        System2 (Producer2)
Server1
    Member1
        System1 (Client)
        CentralMonitoringSystem
Server2
    Member2
        System2 (Producer2)
```

"System0" and "System2" are producers and must have 
[mock services](../xrd-mock-soapui) configured and the access rights
given to client subsystem "System1".

All security servers must allow both MEMBER and SUBSYSTEM requests
to be sent with HTTP connection type (without TLS authentication).

Central monitoring collector subsystem (CentralMonitoringSystem) must
be configured in central server.

It is required to allow /wsdl service in Security Server configuration.

IPv6 must be disabled on the Security Servers.

## Running tests

Running all the test cases:

```
# Set PYTHONUNBUFFERED so the output is not mangled.
PYTHONUNBUFFERED=true ./run_tests.py \
  --producer-security-server <name_or_address> \
  --producer-ss-name <server_name> \
  --producer-system <system_identifier> \
  --client-security-server <name_or_address> \
  --client-ss-name <server_code> \
  --client-system <system_identifier> \
  --client-monitor-system <system_name> \
  --producer2-security-server <name_or_address> \
  --producer2-ss-name <server_code> \
  --producer2-system <system_identifier> \
  --ssh-user <user>
```

For example:

```
PYTHONUNBUFFERED=true ./run_tests.py \
  --producer-server-address xrd0.ss.dns \
  --producer-server-code 00000000_1 \
  --producer-system INST/GOV/00000000/System0 \
  --client-server-address xrd1.ss.dns \
  --client-server-code 00000001_1 \
  --client-system INST/GOV/00000001/System1 \
  --client-monitor-system "Central monitoring" \
  --producer2-server-address xrd2.ss.dns \
  --producer2-server-code 00000002_1 \
  --producer2-system INST/COM/00000002/System2 \
  --ssh-user user
```

A single test case can be run similarly to the following example (note
that test_simple_store_and_query is not using clustered service or
central monitoring quieries and therefore `--producer2*` and
`--client-monitor-system` parameters are not required):

```
PYTHONUNBUFFERED=true ./run_tests.py \
  --producer-security-server xrd0.ss.dns \
  --producer-ss-name 00000000_1 \
  --producer-system INST/GOV/00000000/System0 \
  --client-security-server xrd1.ss.dns \
  --client-ss-name 00000001_1 \
  --client-system INST/GOV/00000001/System1 \
  --ssh-user riajenk \
  OperationalMonitoringIntegrationTest.test_simple_store_and_query
```

## Command line arguments
- `--producer-server-address` - IP or address of producers security
  server (Server0).
- `--client-server-address` - IP or address of clients security
  server (Server1).
- `--producer2-server-address` - IP or address of second producers
  security server (Server2).
- `--ssh-user` - user who is is suitable for running remote commands on
  all the servers.
- `--producer-system` - slash separated identifier of producer
  subsystem. For example: `INST/GOV/00000000/System0`.
- `--client-system` - slash separated identifier of client subsystem.
  For example: `INST/GOV/00000001/System1`.
- `--producer2-system` - slash separated identifier of second producer
  subsystem. For example: `INST/COM/00000002/System2`.
- `--client-monitor-system` - Central monitoring collector subsystem
  name. For example: `"Central monitoring"`.
- `--producer-server-code` - server code of producers security server.
- `--client-server-code` - server code of clients security server.
- `--producer2-server-code` - server code of second producers security
  server.
- `--service-restart-sleep-seconds` - amount of seconds to sleep after
  operational monitoring service was restarted.
- `--proxy-restart-sleep-seconds` - amount of seconds to sleep after
  proxy was restarted. Note that sleep will always be at least as long
  as set with `--service-restart-sleep-seconds` parameter.
- Additional arguments are passed to python unittest module (for example
  the name of test to execute).
