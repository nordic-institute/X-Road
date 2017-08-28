# X-Road: Autologin User Guide

Version: 1.0  
Doc. ID: UG-AUTOLOGIN


| Date        | Version     | Description                                                                                             
|-------------|-------------|---------------------------------------------------------------------------------------------------------
| 23.8.2017   | 1.0         | Initial version             

# Autologin

An utility which automatically enters the PIN code after `xroad-signer` has started.

## Usage

1. Install the package
  * Ubuntu: apt install xroad-autologin
  * RedHat: yum install xroad-autologin

2. If storing the PIN code on the server in plaintext is acceptable, create file `/etc/xroad/autologin` that contains the PIN code. 
  * File should be readable by user `xroad`
  * If `/etc/xroad/autologin` does not exists, and you have not implemented `custom-fetch-pin.sh`, the service will not start
3. If you do not want to store PIN code in plaintext, implement bash script 
`/usr/share/xroad/autologin/custom-fetch-pin.sh`
  * The script needs to output the PIN code to stdout
  * Script should be readable and executable by user `xroad`
  * Script should exit with exit code
    * 0 if it was able to fetch PIN code successfully
    * 127 if it was not able to fetch PIN code, but this is not an actual error that should cause the service to fail (default implementation uses this if `/etc/xroad/autologin` does not exist)
    * other exit codes in error situations that should cause the service to fail
  ```shell
  #!/bin/bash
  PIN_CODE=$(curl https://some-address)
  echo "${PIN_CODE}"
  exit 0
  ```

## Implementation details

* Creates a new service `xroad-autologin`
* Service is started after `xroad-signer` has started
* On Ubuntu, service calls `/usr/share/xroad/autologin/autologin.expect` directly
* On RHEL, service calls wrapper script `/usr/share/xroad/autologin/xroad-autologin-retry.sh` which in turn calls `autologin.expect`
  * Wrapper script handles retries in error situations. On Ubuntu, Upstart can take care of this.
* Service tries to enter the PIN code using script `signer-console`
  * If the PIN was correct or incorrect, it exits
  * If an error occurred (for example because `xroad-signer` has not yet fully started), it keeps retrying indefinitely

