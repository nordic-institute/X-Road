# X-Road: Security hardening guidelines <!-- omit in toc -->

Version: 0.4  
Doc. ID: UG-SEC

## Version history <!-- omit in toc -->

| Date       | Version | Description                                      | Author            |
|------------|---------|--------------------------------------------------|-------------------|
| 02.06.2023 | 0.1     | Initial version                                  | Ričardas Bučiūnas |
| 24.08.2023 | 0.2     | Minimum supported client Security Server version | Eneli Reimets     |
| 14.11.2023 | 0.3     | Publish global configuration over HTTPS          | Eneli Reimets     |
| 15.12.2023 | 0.4     | Minor updates                                    | Eneli Reimets     |

## Table of Contents <!-- omit in toc -->
<!-- toc -->

* [License](#license)
* [1. Introduction](#1-introduction)
    * [1.1 Target Audience](#11-target-audience)
    * [1.2 Terms and abbreviations](#12-terms-and-abbreviations)
    * [1.3 References](#13-references)
* [2. User Management](#2-user-management)
    * [2.1 Configuring account lockout](#21-configuring-account-lockout)
        * [2.1.1 Considerations and risks](#211-considerations-and-risks)
        * [2.1.2 Account lockout examples](#212-account-lockout-examples)
    * [2.2 Configuring password policies](#22-configuring-password-policies)
        * [2.2.1 Considerations and risks](#221-considerations-and-risks)
    * [2.3 Ensuring User Account Security](#23-ensuring-user-account-security)
* [3. Admin UI (Central Server and Security Server)](#3-admin-ui-central-server-and-security-server)
    * [3.1 Host header injection mitigation](#31-host-header-injection-mitigation)
* [4. Access control](#4-access-control)
    * [4.1 Minimum Supported Client Security Server Version](#41-minimum-supported-client-security-server-version)
* [5. Publish global configuration over HTTPS](#5-publish-global-configuration-over-https)
    * [5.1 Central Server TLS configuration](#51-central-server-tls-configuration)
    * [5.2 Configuration Proxy TLS configuration](#52-configuration-proxy-tls-configuration)
    * [5.3 Security Server TLS configuration](#53-security-server-tls-configuration)
  
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

# 1. Introduction

You may want to harden the security of your X-Road instance by configuring additional security policies within your X-Road infrastructure.
The security measures that are introduced in this guide are common security policies that can be configured on operating system level.

## 1.1 Target Audience

The intended audience of this User Guide are X-Road administrators (Central or Security server) who are responsible for X-Road instance set-up and/or everyday management of the X-Road infrastructure.

## 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

## 1.3 References

1. <a id="Ref_IG-CS" class="anchor"></a>\[IG-CS\] X-Road: Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md).
2. <a id="Ref_UG-CS" class="anchor"></a>\[UG-CS\] X-Road: Central Server User Guide. Document ID: [UG-CS](ug-cs_x-road_6_central_server_user_guide.md).
3. <a id="Ref_IG-SS" class="anchor"></a>\[IG-SS\] X-Road: Security Server Installation Guide. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md).
4. <a id="Ref_UG-SS" class="anchor"></a>\[UG-SS\] X-Road: Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md).
5. <a id="Ref_UG-SYSPAR" class="anchor"></a>\[UG-SYSPAR\] X-Road: System Parameters User Guide. Document ID: [UG-SYSPAR](ug-syspar_x-road_v6_system_parameters.md).
6. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).
7. <a id="Ref_UG-CP" class="anchor"></a>\[UG-CP\] X-Road: Configuration Proxy Manual. Document ID: [UG-CP](ug-cp_x-road_v6_configuration_proxy_manual.md).

## 2 User management

X-Road uses the Linux Pluggable Authentication Modules (PAM) to authenticate users. This makes it easy to configure the account management to your liking. 
The example PAM configurations provided in this guide may or may not work on your system depending on your system and existing PAM configurations. 
Note that editing the PAM configurations will take effect immediately without the need to restart anything.

For configuring the following security policies for the X-Road components Admin UI in production, please refer to [The Linux-PAM System Administrator's Guide](http://www.linux-pam.org/Linux-PAM-html/Linux-PAM_SAG.html) for the full documentation on how to configure PAM.

### 2.1 Configuring account lockout

Configuring an account lockout policy in your Admin UI authentication will provide an extra layer of defence against password guessing attacks, such as brute force attacks. 
After configuring the account lockout, when trying to log in to the Admin UI with a locked account, the login screen will display a generic login error without disclosing the reason or any other login information.

The PAM service to configure the account lockout to is `xroad`.

#### 2.1.1 Considerations and risks

After enabling the account lockout for the X-Road component, you should be aware that a user can lock out any other user's account if they know the correct username.

#### 2.1.2 Account lockout examples

The example configurations will lock the user's account, preventing login to the Admin UI for 15 minutes (I.e. 900 seconds) after they provide a wrong password in the Admin UI login three (3) consecutive times. This configuration also affects the root account.

**Example on Ubuntu**

Create a new configuration `/etc/pam.d/xroad` with the following content:
```shell
auth        required          pam_tally2.so deny=3 even_deny_root unlock_time=900 file=/var/lib/xroad/tallylog
@include    common-auth    
account     required          pam_tally2.so
@include    common-account
password    required          pam_deny.so    
session     required          pam_deny.so    
```

**Example on RHEL**

On RHEL systems, the `/etc/pam.d/xroad` file ships with the installation package so you need to modify the existing file. Replace the `/etc/pam.d/xroad` contents with the following:
```shell
#%PAM-1.0
auth       required     pam_tally2.so deny=3 even_deny_root unlock_time=900 file=/var/lib/xroad/tallylog
auth       required     pam_unix.so
account    required     pam_tally2.so
account    required     pam_unix.so
password   required     pam_deny.so
password   required     pam_warn.so
session    required     pam_deny.so
```

### 2.2 Configuring password policies

Configuring a password policy in your Admin UI authentication will provide an additional layer of defence against password guessing attacks, such as password spraying.

User account passwords cannot be changed directly from the Admin UI, therefore the password policy must be configured on operating system user account level. 
The method of adding a new password policy varies significantly depending on your operating system, existing PAM configuration and authentication protocol.

For instruction on how to add password policies, please refer to your operating system's official documentation or customer support.

#### 2.2.1 Considerations and risks

In a strong password, it is advisable to have at least 16 characters at minimum. You can also add complexity requirements, such as numbers and special characters, but these requirements can make the passwords more difficult for users to remember. Further additional measures could be to add commonly known passwords into a blocklist.

### 2.3 Ensuring User Account Security

Users of the web application are created by creating operating-system-level users. This means that a user can access the web application and the underlying operating system with the same credentials. 
Therefore, if user accounts in the web application were compromised, the attacker could use those credentials to log into the server via SSH if credential-based logging in is not disabled.

To harden the user account security, make sure that users are not allowed to access the server via SSH by default. The users needing SSH access are granted those rights separately.

1. Create a user group in which users are allowed to connect to the server via SSH while all other users are denied.
2. Add users which should have SSH access to newly created group.
3. Add the following line to `/etc/ssh/sshd_config`:
     
        AllowGroups <group_to_allow>

4. Restart the SSH service:

        sudo systemctl restart sshd

It is also recommended to disable SSH password login and allow key-based authentication only. Before this modification, add users' public keys to the server. Edit `/etc/ssh/sshd_config` and add the following lines:

    ChallengeResponseAuthentication no
    PasswordAuthentication no

Restart the SSH service once again:

    sudo systemctl restart sshd

In addition, the users should be prevented from logging in to the system. This can be achieved by issuing the following command on Ubuntu:

    usermod -s /bin/false user

On RHEL, the corresponding command is:

    usermod -s /sbin/nologin user

The system administrator should also implement a monitoring and alerting system regarding anomalous logins.

## 3. Admin UI (Central Server and Security Server)

### 3.1 Host header injection mitigation

The host header specifies which website or web application should process an incoming HTTP request. The web server uses the value of this header to dispatch the request to the specified website or web application.

By default, this header allows any value which would be a security risk if Admin UI could be accessed by bad actors. To mitigate this issue it suggested to configure `allowed-hostnames` as described in [UG-SYSPAR](ug-syspar_x-road_v6_system_parameters.md). 
For Security server refer to [proxy-ui-api](ug-syspar_x-road_v6_system_parameters.md#39-management-rest-api-parameters-proxy-ui-api), for Central server refer to [admin-service](ug-syspar_x-road_v6_system_parameters.md#413-center-parameters-admin-service)

## 4. Access control

### 4.1 Minimum Supported Client Security Server Version

To increase the security of the X-Road ecosystem, it is recommended to limit the minimum version of the client Security Server that is allowed to access a service.

On the service provider side, the Security Server administrator can limit the minimum client version by configuring the system parameter `server-min-supported-client-version` as described in [UG-SYSPAR](#Ref_UG-SYSPAR) section 3.2 Proxy parameters.

For example, setting the value `server-min-supported-client-version = 7.3.1` means that client Security Server version should be at least `7.3.1`:

	[proxy]
	server-min-supported-client-version = 7.3.1

## 5. Publish global configuration over HTTPS

Starting from X-Road version 7.4, it is possible to publish global configuration over HTTPS using a TLS certificate issued by a trusted CA. The CA must be trusted by the Security Server's Java installation. See the Central Server User Guide [UG-CS](#Ref_UG-CS) for details.

### 5.1 Central Server TLS configuration

To configure the Central Server to use a certificate issued by a trusted CA for serving global configurations over HTTPS follow "Central Server Installation Guide" [IG-CS](#Ref_IG-CS) section "Configuring TLS Certificates".

### 5.2 Configuration Proxy TLS configuration

To configure the Configuration Proxy to use a certificate issued by a trusted CA follow "Configuration Proxy Manual" [UG-CP](#Ref_UG-CP) section "Configuring TLS Certificates".

### 5.3 Security Server TLS configuration

The TLS certificate used by the global configuration endpoint must be signed by a trusted CA (one trusted by the JAVA installation).

If the certificate isn't trusted by the Security Server's JAVA installation by default, it can be manually added to the system truststore by following the steps below:

**Example on Ubuntu 20.04 / 22.04**

Copy the `.crt` file (PEM) into the `/usr/local/share/ca-certificates` folder.

Run `sudo update-ca-certificates`.

**Example on RHEL 7 / 8**

Copy the `.crt` file (PEM or DER) into the `/etc/pki/ca-trust/source/anchors` folder.

Run `sudo update-ca-trust extract`.

It is possible to disable the verification of the global configuration endpoint’s TLS certificate via system properties. The verification may be disabled in test and development environments. Instead, the verification must always be enabled in production environments. System parameters are specified in the [UG-SYSPAR](#Ref_UG-SYSPAR) section "Configuration Client parameters: [configuration-client]".
