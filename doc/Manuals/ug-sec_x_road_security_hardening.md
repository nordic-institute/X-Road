# X-Road: Security hardening guidelines <!-- omit in toc -->

Version: 0.8  
Doc. ID: UG-SEC

## Version history <!-- omit in toc -->

| Date       | Version | Description                                      | Author            |
|------------|---------|--------------------------------------------------|-------------------|
| 02.06.2023 | 0.1     | Initial version                                  | Ričardas Bučiūnas |
| 24.08.2023 | 0.2     | Minimum supported client Security Server version | Eneli Reimets     |
| 14.11.2023 | 0.3     | Publish global configuration over HTTPS          | Eneli Reimets     |
| 15.12.2023 | 0.4     | Minor updates                                    | Eneli Reimets     |
| 07.01.2025 | 0.5     | Update references                                | Petteri Kivimäki  |
| 09.01.2025 | 0.6     | Restructure heading levels                       | Raido Kaju        |
| 22.04.2026 | 0.7     | Remove RHEL 8 and add RHEL 10 support            | Eneli Reimets     |
| 17.08.2026 | 0.8     | Restructure by audience                          | Petteri Kivimäki  |

## Table of Contents <!-- omit in toc -->
<!-- toc -->

* [License](#license)
* [1. Introduction](#1-introduction)
    * [1.1 Target audience](#11-target-audience)
    * [1.2 How this guide is organised](#12-how-this-guide-is-organised)
    * [1.3 Terms and abbreviations](#13-terms-and-abbreviations)
    * [1.4 References](#14-references)
* [2. X-Road operator and Security Server administrator controls](#2-x-road-operator-and-security-server-administrator-controls)
    * [2.1 User management](#21-user-management)
        * [2.1.1 Configuring account lockout](#211-configuring-account-lockout)
            * [2.1.1.1 Considerations and risks](#2111-considerations-and-risks)
            * [2.1.1.2 Account lockout examples](#2112-account-lockout-examples)
        * [2.1.2 Configuring password policies](#212-configuring-password-policies)
            * [2.1.2.1 Considerations and risks](#2121-considerations-and-risks)
        * [2.1.3 Ensuring user account security](#213-ensuring-user-account-security)
    * [2.2 Admin UI](#22-admin-ui)
        * [2.2.1 Host header injection mitigation](#221-host-header-injection-mitigation)
* [3. Security Server administrator controls](#3-security-server-administrator-controls)
    * [3.1 Access control](#31-access-control)
        * [3.1.1 Minimum supported client Security Server version](#311-minimum-supported-client-security-server-version)
    * [3.2 Trusting the global configuration endpoint certificate](#32-trusting-the-global-configuration-endpoint-certificate)
* [4. X-Road operator controls](#4-x-road-operator-controls)
    * [4.1 Publishing global configuration over HTTPS](#41-publishing-global-configuration-over-https)
        * [4.1.1 Central Server TLS configuration](#411-central-server-tls-configuration)
        * [4.1.2 Configuration Proxy TLS configuration](#412-configuration-proxy-tls-configuration)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1. Introduction

You may want to harden the security of your X-Road instance by configuring additional security policies within your X-Road infrastructure.
The security measures that are introduced in this guide are common security policies that can be configured on operating system level.

### 1.1 Target audience

The intended audience of this User Guide are X-Road administrators (Central or Security server) who are responsible for X-Road instance set-up and/or everyday management of the X-Road infrastructure.

The guide addresses two distinct roles, whose responsibilities and available controls differ:

* the **X-Road operator**, the governing authority responsible for the Central Server and, where deployed, the Configuration Proxy — that is, for the security policy of the whole X-Road instance;
* the **Security Server administrator**, responsible for a single member's Security Server.

Some controls belong to both roles, some to only one, and the controls available to the operator differ between the two components they administer. Each section states the components it applies to.

### 1.2 How this guide is organised

The hardening measures are grouped into three main sections:

* Section [2](#2-x-road-operator-and-security-server-administrator-controls) — operating system and Admin UI hardening addressed to both roles.
* Section [3](#3-security-server-administrator-controls) — controls available to the administrator of an individual Security Server.
* Section [4](#4-x-road-operator-controls) — controls available to the governing authority operating the Central Server and the Configuration Proxy.

The section titles name the **role** the controls are addressed to, so that an administrator can tell at a glance which sections are theirs to read. Each main section then opens with an **Applies to** line naming the **components** those controls apply to, and a subsection repeats the line only where its scope is narrower than that of the section containing it.

The two are stated separately because they do not coincide: the X-Road operator administers two components that differ in what can be hardened. The Configuration Proxy has no Admin UI and no web application users, so the user management and Admin UI controls of section [2](#2-x-road-operator-and-security-server-administrator-controls) are addressed to the operator but apply only to the Central Server. Read the sections that name your role, and within them apply only what the **Applies to** line names for the components you run.

Every section also carries a stable anchor that does not change when sections are renumbered, so that other documents — in particular the X-Road threat model \[[ARC-TM](#Ref_ARC-TM)\] — can cite a control without the citation breaking at the next revision.

### 1.3 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.4 References

1. <a id="Ref_IG-CS" class="anchor"></a>\[IG-CS\] X-Road: Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md).
2. <a id="Ref_UG-CS" class="anchor"></a>\[UG-CS\] X-Road: Central Server User Guide. Document ID: [UG-CS](ug-cs_x-road_6_central_server_user_guide.md).
3. <a id="Ref_IG-SS" class="anchor"></a>\[IG-SS\] X-Road: Security Server Installation Guide. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md).
4. <a id="Ref_UG-SS" class="anchor"></a>\[UG-SS\] X-Road: Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md).
5. <a id="Ref_UG-SYSPAR" class="anchor"></a>\[UG-SYSPAR\] X-Road: System Parameters User Guide. Document ID: [UG-SYSPAR](ug-syspar_x-road_v6_system_parameters.md).
6. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).
7. <a id="Ref_UG-CP" class="anchor"></a>\[UG-CP\] X-Road: Configuration Proxy Manual. Document ID: [UG-CP](ug-cp_x-road_v6_configuration_proxy_manual.md).
8. <a id="Ref_ARC-TM" class="anchor"></a>\[ARC-TM\] X-Road Threat Model. Document ID: [ARC-TM](../Architecture/arc-tm_x-road_threat_model.md).

<a id="ug-sec-common-controls" class="anchor"></a>

## 2. X-Road operator and Security Server administrator controls

**Applies to:** Central Server, Security Server

The controls in this section harden the Admin UI and the operating system accounts that authenticate to it. They are configured on the host and are the responsibility of whoever administers that host — the X-Road operator for a Central Server, the Security Server administrator for a Security Server.

They do not apply to the Configuration Proxy, which has no Admin UI and no web application users; it is administered from the command line as described in the Configuration Proxy Manual \[[UG-CP](#Ref_UG-CP)\]. The only control in this guide that applies to the Configuration Proxy is section [4.1.2](#412-configuration-proxy-tls-configuration).

<a id="ug-sec-user-management" class="anchor"></a>

### 2.1 User management

X-Road uses the Linux Pluggable Authentication Modules (PAM) to authenticate users. This makes it easy to configure the account management to your liking. 
The example PAM configurations provided in this guide may or may not work on your system depending on your system and existing PAM configurations. 
Note that editing the PAM configurations will take effect immediately without the need to restart anything.

For configuring the following security policies for the X-Road components Admin UI in production, please refer to [The Linux-PAM System Administrator's Guide](https://fossies.org/linux/Linux-PAM-docs/doc/sag/Linux-PAM_SAG.pdf) for the full documentation on how to configure PAM.

<a id="ug-sec-lockout" class="anchor"></a>

#### 2.1.1 Configuring account lockout

Configuring an account lockout policy in your Admin UI authentication will provide an extra layer of defence against password guessing attacks, such as brute force attacks. 
After configuring the account lockout, when trying to log in to the Admin UI with a locked account, the login screen will display a generic login error without disclosing the reason or any other login information.

The PAM service to configure the account lockout to is `xroad`.

##### 2.1.1.1 Considerations and risks

After enabling the account lockout for the X-Road component, you should be aware that a user can lock out any other user's account if they know the correct username.

##### 2.1.1.2 Account lockout examples

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

<a id="ug-sec-password-policy" class="anchor"></a>

#### 2.1.2 Configuring password policies

Configuring a password policy in your Admin UI authentication will provide an additional layer of defence against password guessing attacks, such as password spraying.

User account passwords cannot be changed directly from the Admin UI, therefore the password policy must be configured on operating system user account level. 
The method of adding a new password policy varies significantly depending on your operating system, existing PAM configuration and authentication protocol.

For instruction on how to add password policies, please refer to your operating system's official documentation or customer support.

##### 2.1.2.1 Considerations and risks

In a strong password, it is advisable to have at least 16 characters at minimum. You can also add complexity requirements, such as numbers and special characters, but these requirements can make the passwords more difficult for users to remember. Further additional measures could be to add commonly known passwords into a blocklist.

<a id="ug-sec-account-security" class="anchor"></a>

#### 2.1.3 Ensuring user account security

Users of the web application are created by creating operating-system-level users. This means that a user can access the web application and the underlying operating system with the same credentials. 
Therefore, if user accounts in the web application were compromised, the attacker could use those credentials to log into the server via SSH if credential-based logging in is not disabled.

To harden the user account security, make sure that users are not allowed to access the server via SSH by default. The users needing SSH access are granted those rights separately.

1. Create a user group in which users are allowed to connect to the server via SSH while all other users are denied.
2. Add users which should have SSH access to newly created group.
3. Add the following line to `/etc/ssh/sshd_config`:

    ```bash
    AllowGroups <group_to_allow>
    ```

4. Restart the SSH service:

    ```bash
    sudo systemctl restart sshd
    ```

It is also recommended to disable SSH password login and allow key-based authentication only. Before this modification, add users' public keys to the server. Edit `/etc/ssh/sshd_config` and add the following lines:

```
ChallengeResponseAuthentication no
PasswordAuthentication no
```


Restart the SSH service once again:

```bash
sudo systemctl restart sshd
```

In addition, the users should be prevented from logging in to the system. This can be achieved by issuing the following command on Ubuntu:

```bash
usermod -s /bin/false user
```

On RHEL, the corresponding command is:

```bash
usermod -s /sbin/nologin user
```

The system administrator should also implement a monitoring and alerting system regarding anomalous logins.

<a id="ug-sec-admin-ui" class="anchor"></a>

### 2.2 Admin UI

Both the Central Server and the Security Server expose an Admin UI, and the control below applies to both.

<a id="ug-sec-host-header" class="anchor"></a>

#### 2.2.1 Host header injection mitigation

The host header specifies which website or web application should process an incoming HTTP request. The web server uses the value of this header to dispatch the request to the specified website or web application.

By default, this header allows any value which would be a security risk if Admin UI could be accessed by bad actors. To mitigate this issue it suggested to configure `allowed-hostnames` as described in [UG-SYSPAR](ug-syspar_x-road_v6_system_parameters.md). 
For Security server refer to [proxy-ui-api](ug-syspar_x-road_v6_system_parameters.md#39-management-rest-api-parameters-proxy-ui-api), for Central server refer to [admin-service](ug-syspar_x-road_v6_system_parameters.md#413-center-parameters-admin-service)

<a id="ug-sec-ss-controls" class="anchor"></a>

## 3. Security Server administrator controls

**Applies to:** Security Server

The controls in this section are configured on an individual Security Server by its administrator. They govern how that Security Server treats the parties it exchanges messages with and the configuration source it downloads from.

<a id="ug-sec-access-control" class="anchor"></a>

### 3.1 Access control

<a id="ug-sec-min-client-version" class="anchor"></a>

#### 3.1.1 Minimum Supported Client Security Server Version

To increase the security of the X-Road ecosystem, it is recommended to limit the minimum version of the client Security Server that is allowed to access a service.

On the service provider side, the Security Server administrator can limit the minimum client version by configuring the system parameter `server-min-supported-client-version` as described in [UG-SYSPAR](#Ref_UG-SYSPAR) section 3.2 Proxy parameters.

For example, setting the value `server-min-supported-client-version = 7.3.1` means that client Security Server version should be at least `7.3.1`:

```ini
[proxy]
server-min-supported-client-version = 7.3.1
```

<a id="ug-sec-globalconf-truststore" class="anchor"></a>

### 3.2 Trusting the global configuration endpoint certificate

Where the X-Road operator publishes the global configuration over HTTPS, as described in section [4.1](#41-publishing-global-configuration-over-https), the TLS certificate used by the global configuration endpoint must be signed by a trusted CA (one trusted by the JAVA installation).

If the certificate isn't trusted by the Security Server's JAVA installation by default, it can be manually added to the system truststore by following the steps below:

**Example on Ubuntu 20.04 / 22.04**

Copy the `.crt` file (PEM) into the `/usr/local/share/ca-certificates` folder.

Run `sudo update-ca-certificates`.

**Example on RHEL 9 / 10**

Copy the `.crt` file (PEM or DER) into the `/etc/pki/ca-trust/source/anchors` folder.

Run `sudo update-ca-trust extract`.

It is possible to disable the verification of the global configuration endpoint’s TLS certificate via system properties. The verification may be disabled in test and development environments. Instead, the verification must always be enabled in production environments. System parameters are specified in the [UG-SYSPAR](#Ref_UG-SYSPAR) section "Configuration Client parameters: [configuration-client]".

<a id="ug-sec-operator-controls" class="anchor"></a>

## 4. X-Road operator controls

**Applies to:** Central Server, Configuration Proxy

The controls in this section are configured by the governing authority on the Central Server and, where deployed, the Configuration Proxy. They apply to the X-Road instance as a whole rather than to an individual member.

The Central Server and the Configuration Proxy are supported on Ubuntu only, so this section contains no RHEL-specific instructions.

<a id="ug-sec-globalconf-https" class="anchor"></a>

### 4.1 Publishing global configuration over HTTPS

Starting from X-Road version 7.4, it is possible to publish global configuration over HTTPS using a TLS certificate issued by a trusted CA. The CA must be trusted by the Security Server's Java installation. See the Central Server User Guide [UG-CS](#Ref_UG-CS) for details.

The corresponding step on the Security Server side, adding the CA to the Security Server's truststore, is described in section [3.2](#32-trusting-the-global-configuration-endpoint-certificate) and is the responsibility of each Security Server administrator.

<a id="ug-sec-cs-tls" class="anchor"></a>

#### 4.1.1 Central Server TLS configuration

To configure the Central Server to use a certificate issued by a trusted CA for serving global configurations over HTTPS follow "Central Server Installation Guide" [IG-CS](#Ref_IG-CS) section "Configuring TLS Certificates".

<a id="ug-sec-confproxy-tls" class="anchor"></a>

#### 4.1.2 Configuration Proxy TLS configuration

To configure the Configuration Proxy to use a certificate issued by a trusted CA follow "Configuration Proxy Manual" [UG-CP](#Ref_UG-CP) section "Configuring TLS Certificates".
