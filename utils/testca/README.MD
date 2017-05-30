# Test-CA with TSA and OCSP

This directory contains a collection of scripts to set up an openssl-based test-CA environment for signing certificates and providing TSA and OCSP services during development. The scripts are created for Ubuntu 14.04.

**Note that the test-CA created by the scripts is for testing purposes only and is not meant to provide a secure certification authority.**

---------------------------------------------

## Contents of the testca directory

`etc/init` - upstart-jobs for the TSA and OCSP

`etc/nginx` - nginx configuration for proxying TSA and OCSP requests

`home/ca/CA` - scripts for creating the test-CA environment and for signing certificates locally

`home/ca/TSA` - the TSA server

`usr` - scripts for signing certificates


---------------------------------------------

## Preliminary requirements

1. Install nginx-light on the target machine
2. Create the following users on the target machine
	- `ca`
	- `ocsp`
3. Copy the following directories from the `testca` directory to target machine root
	- `etc`
	- `home`
	- `usr`
4. Add user `ocsp` to group `ca`
5. Grant `ca` ownership and all permissions to files under `/home/ca/CA`
6. Grant read permission for group `ca` to files under `/home/ca/CA`
7. Create a file called `ocsp.log` under `/var/log`
8. Grant `ca` ownership and group `ca` read and write permissions for `ocsp.log`

---------------------------------------------

## Creating the CA environment

1. As `ca`, run `init.sh` under `/home/ca/CA`
	- this prompts you for information for the creation of the CA, OCSP and TSA certificates respectively
	- the entered values can be selected freely, the only limitation being that the value combination must be unique
2. Enter values to at least the following prompts:
	- Country
	- Organization
	- Common name

Entering recognizable and meaningful values helps to distinguish the certification authorities when registering them on the central server.

---------------------------------------------

## Starting the TSA and OCSP jobs

Both jobs use nginx as a proxy to redirect the requests to listened ports:

- requests (POST) to `port 8888` go to `8889` for the openssl process started by the OCSP job
- requests (GET, POST) to `port 8899` go to `localhost:9999` for the python server started by the TSA job


1. Before starting the jobs, restart the nginx service to apply the proxy changes
2. start the jobs by calling `sudo /sbin/start ocsp` and `sudo /sbin/start tsa`

The jobs are located in `/etc/init` and are run as `ocsp`.

The signing script `/usr/local/bin/sign` restarts the ocsp service after signing a certificate so that the certification database updates are available for verification (signing requires root access)

If there are issues running the TSA or OCSP jobs, logging information can be found in the upstart-job logs under `/var/log/upstart/<jobname>.log`. Additional information on the OCSP service status is logged in `/var/log/ocsp.log`.

## Configuring the central server to use the test-CA

After the jobs have been successfully started, the test-CA is ready to be used in the test environment.

To configure the central server to use the test-CA:

1. Import the CA, TSA and OCSP certificates from `/home/ca/CA/certs` to the central server
2. Configure the CA to use the test-CA OCSP through `port 8888` on the test-CA machine
3. Configure the TSA to `port 8899` on the test-CA machine
