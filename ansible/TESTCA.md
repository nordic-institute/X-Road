# Test-CA with TSA and OCSP

**Note**. The test CA is for _testing and development purposes only_. It is not a secure certification authority and therefore, it's not suitable for production use.

The `roles/xroad-ca/` directory contains a collection of scripts to set up an openssl-based test-CA environment for signing certificates and providing TSA and OCSP services during development. 
The scripts require Ubuntu 20.04 or Ubuntu 22.04.

You can initialize the test-CA server automatically [with Ansible](README.md). This initializes a new test-CA server if one is listed in `ca_servers` category.

Alternatively, you can initialize the server manually. This is described in chapters 2-4. If you use Ansible, skip those chapters.

Chapters 1 and 5-7 describe usage of the initialized CA server and are useful for both manual and ansible installation.

To customize test-CA DN details for Ansible installation, specify parameters in a file in group_vars ([read more](README.md) about `group_vars`).

Example group_vars configuration file with all of the test-CA parameters:

```
xroad_ca_o: "Customized Test"
xroad_ca_cn: "Customized Test CA CN"
xroad_ca_ocsp_o: "Customized Test"
xroad_ca_ocsp_cn: "Customized Test OCSP CN"
xroad_ca_tsa_o: "Customized Test"
xroad_ca_tsa_cn: "Customized Test TSA CN"
```

---------------------------------------------

## 1. Contents of the roles/xroad-ca directory

* `files/lib/systemd/system` - systemd services for the TSA and OCSP
* `files/etc/nginx` - nginx configuration for proxying TSA and OCSP requests
* `files/home/ca/CA` - CA configuration, and scripts for signing certificates locally
* `files/home/ca/TSA` - the TSA server
* `files/usr` - scripts for signing certificates
* `templates/init.sh` - script for creating the test-CA environment

---------------------------------------------

## 2. Preliminary requirements

(Ansible does these steps automatically)

1. Install nginx-light on the target machine (`sudo apt install nginx-light`)
2. Create the following users on the target machine:
    - `ca`
    - `ocsp`
3. Copy the following directories from the `roles/xroad-ca/files` directory to the target machine root:
    - `etc`
    - `home`
    - `usr`
    - `lib`
4. Copy `roles/xroad-ca/templates/init.sh` to `/home/ca/CA/`
5. Add user `ocsp` to group `ca`
6. Grant `ca` ownership and all permissions to files under `/home/ca/CA`
7. Grant read permission for group `ca` to files under `/home/ca/CA`
8. Grant read + execute permissions for group `ca` to directories under `/home/ca/CA`
9. Create a file called `ocsp.log` under `/var/log`
10. Grant `ca` ownership and group `ca` read and write permissions for `ocsp.log`
11. Fill in parameters for CA, OCSP and TSA distinguished names (DN) in `/home/ca/CA/init.sh`:
```
# dn parameters
DN_CA_O="{{ xroad_ca_o }}"
DN_CA_CN="{{ xroad_ca_cn }}"
DN_OCSP_O="{{ xroad_ca_ocsp_o }}"
DN_OCSP_CN="{{ xroad_ca_ocsp_cn }}"
DN_TSA_O="{{ xroad_ca_tsa_o }}"
DN_TSA_CN="{{ xroad_ca_tsa_cn }}"
```
CA, OCSP and TSA distinguished name field values can be selected freely,
the only limitation being that the value combination should be unique for each three.

Using recognizable and meaningful values helps to distinguish the certification authorities when registering them on the central server.

---------------------------------------------

## 3. Creating the CA environment

(Ansible does this step automatically)

1. As `ca`, run `init.sh` under `/home/ca/CA`

---------------------------------------------

## 4. (Re)start NGINX, CA, OCSP and TSA services

(Ansible does these steps automatically)

1. Before starting the jobs, restart the nginx service to apply the proxy changes (`sudo systemctl reload nginx`)
2. Start the jobs by calling `sudo systemctl start ca ocsp tsa`

---------------------------------------------

## 5. About the CA, TSA, and OCSP services

Both services use nginx as a proxy to redirect the requests to listened ports:

- POST requests to `port 8888` go to the python process started by the OCSP job
- GET requests to url `/testca` on `port 8888` go to the python process started by the CA job
- requests (GET, POST) to `port 8899` go to `localhost:9999` for the python server started by the TSA job

---------------------------------------------

## 6. Configuring the central server to use the test-CA
After the jobs have been successfully started, the test-CA is ready to be used in the test environment.

To configure the central server to use the test-CA:

1. Import the CA, TSA and OCSP certificates from `/home/ca/CA/certs` to the central server
   - `ee.ria.xroad.common.certificateprofile.impl.EjbcaCertificateProfileInfoProvider` can be used as the certificate profile info provider.
2. Configure the CA to use the test-CA OCSP through `port 8888` on the test-CA machine
3. Configure the TSA to `port 8899` on the test-CA machine

---------------------------------------------

## 7. Signing certificates

To sign a CSR, you have three options:

### 7.1 Use the web interface

There is a simple web interface running at `http://some-ca-server:8888/testca/` which can be used to sign requests.

It is also possible to use a command line http client (e.g. curl):
```
curl -Fcertreq=@auth_csr.pem http://some-ca-server:8888/testca/sign
```

### 7.2 Use command `sign` on a file

1. Upload the CSR to test CA server
2. Register the certificate using command `sign` (user needs to have sudo rights)
```
user@some-ca-server:~$ sign sign_csr_20161020_member_FI_GOV_889.der
Using configuration from CA.cnf
Check that the request matches the signature
Signature ok
Certificate Details:
        Serial Number: 6 (0x6)
        Validity
            Not Before: Oct 20 12:34:52 2016 GMT
            Not After : Oct 15 12:34:52 2036 GMT
        Subject:
            countryName               = FI
            organizationName          = FOONAME
            commonName                = 889
            serialNumber              = FI/cadev-ss1/GOV
        X509v3 extensions:
            X509v3 Basic Constraints:
                CA:FALSE
            X509v3 Key Usage: critical
                Non Repudiation
Certificate is to be certified until Oct 15 12:34:52 2036 GMT (7300 days)
Write out database with 1 new entries
Data Base Updated
```
The signed certificate is stored in `/home/ca/CA/newcerts/??.pem`, where ?? = serial number

### 7.3 Use commands `sign-sign` and `sign-auth` on piped data

You can use SSH to pipe the CSR and signed certificate remotely.
Pick either `sign-sign` or `sign-auth` based on the certificate type.

```
$ cat sign_csr_20161020_member_FI_GOV_901.der | ssh user@some-ca-server sign-sign > sign-cert-901.pem
Using configuration from CA.cnf
Check that the request matches the signature
Signature ok
Certificate Details:
        Serial Number: 4 (0x4)
        Validity
            Not Before: Oct 20 12:50:35 2016 GMT
            Not After : Oct 15 12:50:35 2036 GMT
        Subject:
            countryName               = FI
            organizationName          = 6
            commonName                = 901
            serialNumber              = FI/cadev-ss1/GOV
        X509v3 extensions:
            X509v3 Basic Constraints:
                CA:FALSE
            X509v3 Key Usage: critical
                Non Repudiation
Certificate is to be certified until Oct 15 12:50:35 2036 GMT (7300 days)
Write out database with 1 new entries
Data Base Updated
```

### 7.4 Certificate revocation

To revoke a signed certificate, you can use revoke.sh script from `/home/ca/CA`.
Run the script as a user with sudo rights, from `/home/ca/CA`.
revoke.sh takes the signed certificate filename (.pem) as a parameter.

Examples:
```
$ cd /home/ca/CA/
$ ./revoke.sh /someplace/auth-cert-200.pem
Using configuration from CA.cnf
ERROR:Already revoked, serial number 04

$ ./revoke.sh /home/ca/CA/newcerts/05.pem
Using configuration from CA.cnf
Revoking Certificate 05.
Data Base Updated
```

## 8. Troubleshooting

Systemd service logs can be viewed with journalctl -u service-name, e.g `journalctl -u ocsp`.

