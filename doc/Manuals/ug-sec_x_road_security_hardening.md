# X-Road: Security hardening guidelines <!-- omit in toc -->

Version: 0.8  
Doc. ID: UG-SEC

## Version history <!-- omit in toc -->

| Date       | Version | Description                                                                                                                        | Author            |
|------------|---------|------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| 02.06.2023 | 0.1     | Initial version                                                                                                                    | Ričardas Bučiūnas |
| 24.08.2023 | 0.2     | Minimum supported client Security Server version                                                                                   | Eneli Reimets     |
| 14.11.2023 | 0.3     | Publish global configuration over HTTPS                                                                                            | Eneli Reimets     |
| 15.12.2023 | 0.4     | Minor updates                                                                                                                      | Eneli Reimets     |
| 07.01.2025 | 0.5     | Update references                                                                                                                  | Petteri Kivimäki  |
| 09.01.2025 | 0.6     | Restructure heading levels                                                                                                         | Raido Kaju        |
| 22.04.2026 | 0.7     | Remove RHEL 8 and add RHEL 10 support                                                                                              | Eneli Reimets     |
| 17.08.2026 | 0.8     | Restructure by audience, add software token PIN policy, backup encryption, message log, audit log forwarding and deployment models | Petteri Kivimäki  |

## Table of Contents <!-- omit in toc -->
<!-- toc -->

* [License](#license)
* [1. Introduction](#1-introduction)
    * [1.1 Target audience](#11-target-audience)
    * [1.2 How this guide is organised](#12-how-this-guide-is-organised)
    * [1.3 Deployment models](#13-deployment-models)
    * [1.4 Terms and abbreviations](#14-terms-and-abbreviations)
    * [1.5 References](#15-references)
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
    * [2.3 Enforcing the software token PIN policy](#23-enforcing-the-software-token-pin-policy)
        * [2.3.1 Considerations and risks](#231-considerations-and-risks)
    * [2.4 Encrypting backups](#24-encrypting-backups)
        * [2.4.1 Considerations and risks](#241-considerations-and-risks)
    * [2.5 Forwarding the audit log](#25-forwarding-the-audit-log)
        * [2.5.1 Considerations and risks](#251-considerations-and-risks)
* [3. Security Server administrator controls](#3-security-server-administrator-controls)
    * [3.1 Access control](#31-access-control)
        * [3.1.1 Minimum supported client Security Server version](#311-minimum-supported-client-security-server-version)
    * [3.2 Trusting the global configuration endpoint certificate](#32-trusting-the-global-configuration-endpoint-certificate)
    * [3.3 Message log and data protection](#33-message-log-and-data-protection)
        * [3.3.1 Considerations and risks](#331-considerations-and-risks)
* [4. X-Road operator controls](#4-x-road-operator-controls)
    * [4.1 Publishing global configuration over HTTPS](#41-publishing-global-configuration-over-https)
        * [4.1.1 Central Server TLS configuration](#411-central-server-tls-configuration)
        * [4.1.2 Configuration Proxy TLS configuration](#412-configuration-proxy-tls-configuration)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1. Introduction

You may want to harden the security of your X-Road instance by configuring additional security policies within your X-Road infrastructure.

The measures in this guide are of three kinds: security policies configured at the operating system level, X-Road system parameters whose default is permissive or whose secure value depends on the deployment, and operational practices — such as key custody, retention and periodic verification — that no single setting can enforce.

For each measure the guide states the decision to be made and why it matters. Where the procedure for carrying it out is already documented in an installation or user guide, that guide is referenced rather than repeated here, so that there is one authoritative description of each procedure and this document does not diverge from it.

### 1.1 Target audience

The intended audience of this User Guide are X-Road administrators (Central or Security Server) who are responsible for X-Road instance set-up and/or everyday management of the X-Road infrastructure.

The guide addresses two distinct roles, whose responsibilities and available controls differ:

* the **X-Road operator**, the governing authority responsible for the operation of the Central Server and, where deployed, the Configuration Proxy;
* the **Security Server administrator**, responsible for the maintenance and operation of the Security Server.

Some controls belong to both roles, some to only one, and the controls available to the operator differ between the two components they administer. Each section states the components it applies to.

### 1.2 How this guide is organised

The hardening measures are grouped into three main sections:

* Section [2](#2-x-road-operator-and-security-server-administrator-controls) — hardening addressed to both roles: operating system accounts, the Admin UI, the token PIN policy, backups and the audit log.
* Section [3](#3-security-server-administrator-controls) — controls available to the administrator operating a Security Server.
* Section [4](#4-x-road-operator-controls) — controls available to the governing authority operating the Central Server and the Configuration Proxy.

The section titles name the **role** the controls are addressed to. Each section and subsection then states an **Applies to** line naming the **components** its controls apply to.

### 1.3 Deployment models

A Security Server can be installed on a Linux host from native packages, or run as a container using the Security Server Sidecar, either directly on Docker or in a Kubernetes cluster. The Central Server and the Configuration Proxy are available as native packages only.

This guide is the baseline for all of them. The controls it describes are properties of the X-Road software rather than of the platform underneath it, so the token PIN policy, backup encryption, message log protection, audit log forwarding and the controls in sections [3](#3-security-server-administrator-controls) and [4](#4-x-road-operator-controls) apply to a Security Server whatever it runs on.

A container deployment adds a platform that also has to be secured, and this guide does not cover it. Two further guides do, and both are additions to this one rather than alternatives to it:

* The Security Server Sidecar Security Guide \[[UG-SS-SEC-SIDECAR](#Ref_UG-SS-SEC-SIDECAR)\] covers the Docker host, the Docker daemon and the container runtime;
* The Kubernetes Security Server Sidecar Security User Guide \[[UG-K-SS-SEC-SIDECAR](#Ref_UG-K-SS-SEC-SIDECAR)\] covers the Kubernetes cluster — secrets, cluster access, network policies and pod security. A Kubernetes deployment still runs containers, so the Docker guide applies there as well.

Please note that following either the platform guide or this document alone does not provide comprehensive security guidance. To properly secure the software, both the applicable platform guide and this security hardening document should be reviewed.

### 1.4 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.5 References

1. <a id="Ref_IG-CS" class="anchor"></a>\[IG-CS\] X-Road: Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md).
2. <a id="Ref_UG-CS" class="anchor"></a>\[UG-CS\] X-Road: Central Server User Guide. Document ID: [UG-CS](ug-cs_x-road_6_central_server_user_guide.md).
3. <a id="Ref_IG-SS" class="anchor"></a>\[IG-SS\] X-Road: Security Server Installation Guide. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md).
4. <a id="Ref_UG-SS" class="anchor"></a>\[UG-SS\] X-Road: Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md).
5. <a id="Ref_UG-SYSPAR" class="anchor"></a>\[UG-SYSPAR\] X-Road: System Parameters User Guide. Document ID: [UG-SYSPAR](ug-syspar_x-road_v6_system_parameters.md).
6. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).
7. <a id="Ref_UG-CP" class="anchor"></a>\[UG-CP\] X-Road: Configuration Proxy Manual. Document ID: [UG-CP](ug-cp_x-road_v6_configuration_proxy_manual.md).
8. <a id="Ref_ARC-TM" class="anchor"></a>\[ARC-TM\] X-Road Threat Model. Document ID: [ARC-TM](../Architecture/arc-tm_x-road_threat_model.md).
9. <a id="Ref_MLAV" class="anchor"></a>\[MLAV\] X-Road: Messagelog Archive Verifier. [MLAV](../../src/tool/messagelog-archive-verifier/README.md).
10. <a id="Ref_SPEC-AL" class="anchor"></a>\[SPEC-AL\] X-Road: Audit Log Events. Document ID: [SPEC-AL](../Architecture/spec-al_x-road_audit_log_events.md).
11. <a id="Ref_UG-SS-SEC-SIDECAR" class="anchor"></a>\[UG-SS-SEC-SIDECAR\] X-Road: Security Server Sidecar Security Guide. Document ID: [UG-SS-SEC-SIDECAR](../Sidecar/security_server_sidecar_security_guide.md).
12. <a id="Ref_UG-K-SS-SEC-SIDECAR" class="anchor"></a>\[UG-K-SS-SEC-SIDECAR\] X-Road: Kubernetes Security Server Sidecar Security User Guide. Document ID: [UG-K-SS-SEC-SIDECAR](../Sidecar/kubernetes_security_guide.md).

<a id="ug-sec-common-controls" class="anchor"></a>

## 2. X-Road operator and Security Server administrator controls

**Applies to:** Central Server, Security Server, Configuration Proxy

The controls in this section are addressed to both roles. They are configured on the host and are the responsibility of whoever administers it — the X-Road operator for a Central Server or a Configuration Proxy, the Security Server administrator for a Security Server.

<a id="ug-sec-user-management" class="anchor"></a>

### 2.1 User management

**Applies to:** Central Server, Security Server

For a component installed directly on a Linux host, administrator accounts are managed by the host operating system. When a Security Server runs in a container, administrator accounts are instead managed by the operating system inside the container. These accounts belong to the container's operating system environment, not to the container host or cluster. See section [1.3](#13-deployment-models) for more information on deployment models.

X-Road uses Linux Pluggable Authentication Modules (PAM) to authenticate users. PAM allows the authentication and account management configuration to be adapted to the requirements of the deployment environment. The example PAM configurations provided in this guide may not work as-is in every environment, as their applicability depends on the operating system and existing PAM configuration. Changes to the PAM configuration take effect immediately and do not require a restart.

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

**Applies to:** Central Server, Security Server

Both the Central Server and the Security Server expose an Admin UI, and the control below applies to both.

<a id="ug-sec-host-header" class="anchor"></a>

#### 2.2.1 Host header injection mitigation

The host header specifies which website or web application should process an incoming HTTP request. The web server uses the value of this header to dispatch the request to the specified website or web application.

By default, this header allows any value which would be a security risk if Admin UI could be accessed by bad actors. To mitigate this issue it suggested to configure `allowed-hostnames` as described in [UG-SYSPAR](ug-syspar_x-road_v6_system_parameters.md). 
For Security server refer to [proxy-ui-api](ug-syspar_x-road_v6_system_parameters.md#39-management-rest-api-parameters-proxy-ui-api), for Central server refer to [admin-service](ug-syspar_x-road_v6_system_parameters.md#413-center-parameters-admin-service)

<a id="ug-sec-token-pin-policy" class="anchor"></a>

### 2.3 Enforcing the software token PIN policy

**Applies to:** Central Server, Security Server, Configuration Proxy

The software token PIN protects the private keys that the signer holds on a software token: the authentication and signing keys on a Security Server, and the global configuration signing keys on a Central Server and a Configuration Proxy.

X-Road ships with the PIN policy switched off. The system parameter `enforce-token-pin-policy` defaults to `false` in the `[signer]` section on all three components, so a software token PIN of any length and composition is accepted. This is a deliberate exception to the deny-by-default principle that X-Road otherwise follows \[[ARC-TM](#Ref_ARC-TM)\], and it is recommended to enable the policy.

Some country-specific meta-packages set the parameter already. For example, the Finnish and Estonian Security Server packages both ship `enforce-token-pin-policy = true`, so on those installations the policy is in force without further configuration. Check the effective value before assuming it is unset. The parameter is described for each component in [UG-SYSPAR](#Ref_UG-SYSPAR) sections "Signer parameters: [signer]".

To enable it, add the following to `/etc/xroad/conf.d/local.ini` and then restart signer:

```ini
[signer]
enforce-token-pin-policy = true
```

When the policy is enforced, a software token PIN must be

* at least 10 characters long, and
* composed of characters from at least three of the four character classes: lower-case letters, upper-case letters, digits, and special characters.

Only printable ASCII characters are accepted. A PIN containing any character outside that range is rejected regardless of its length.

#### 2.3.1 Considerations and risks

The PIN policy is enforced only when a PIN is set or changed. Enabling the policy on a running system does not affect an existing PIN. To apply the policy to the PIN currently in use, the PIN must be changed after the policy is enabled. Changing the existing PIN is therefore a required part of enabling this control.

The policy governs software tokens only. Where keys are held on an SSCD or a hardware security module, the PIN or passphrase rules are those of the device, and `enforce-token-pin-policy` has no effect on them. Note that Security Server authentication keys are supported on a software token only, so they are always protected by the software token PIN.

<a id="ug-sec-backup-encryption" class="anchor"></a>

### 2.4 Encrypting backups

**Applies to:** Central Server, Security Server

A backup contains most of `/etc/xroad` together with a database dump. This includes software token private keys and internal TLS key material. A single backup therefore contains enough information to reconstruct the server's identity and use its keys. A backup that is moved outside the host or stored in a location accessible to unauthorised users can expose this key material. Backups must therefore be protected to the same level as the sensitive keys and configuration they contain.

Some paths are deliberately excluded, among them the OpenPGP keyring in `/etc/xroad/gpghome` — the keyring holding the keys used to sign and encrypt the backup in the first place. It is therefore not enough to keep backups: the keyring has to be preserved separately, or a backup cannot be decrypted after the server it came from is rebuilt or replaced.

Backups are always signed and the signature is verified on restore, so their integrity is protected out of the box. Encryption is a separate setting and is **off** by default: `backup-encryption-enabled` defaults to `false`. It is recommended to enable it on both the Central Server and the Security Server, and to set at least one additional recipient in `backup-encryption-keyids`. Without one, a backup is encrypted only to the server's own key, which lives in the keyring that the backup does not contain — so an additional recipient whose private key is held off the server is what makes an encrypted backup recoverable once that server is gone.

For where the parameters are set, how to generate an additional key pair, how to import and trust it in the `/etc/xroad/gpghome` keyring, and how to decrypt a backup, see:

* Security Server — \[[UG-SS](#Ref_UG-SS)\] section "Backup Encryption Configuration";
* Central Server — \[[UG-CS](#Ref_UG-CS)\] section "Backup Encryption Configuration";
* parameter reference — \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

#### 2.4.1 Considerations and risks

`backup-encryption-keyids` is an important security setting. It supports recovery by allowing multiple keys to decrypt backups, but every key on the list can decrypt every backup created after the key is added. If an unauthorised recipient is added to the list, they can decrypt subsequent backups and access the server's key material. The backup process will not report this as unusual. Therefore, treat the list as a privileged setting, keep changes to it under change control, and review the list whenever administrator access changes.

X-Road applies no strength or validity checks to the keys named in the list. Confirming that each key is strong enough, that its fingerprint is the expected one, and that its private key is held securely away from the server is the administrator's responsibility.

Encryption protects the backup file, but the storage location must also be secured. Backups downloaded through the Admin UI or copied to external or long-term storage are no longer protected by the security controls of the host. Therefore, the storage location, transfer channel, and access to the backups must be protected to the same standard as the server itself.

Enabling encryption changes the restore path. Confirm that a backup taken after the change can actually be decrypted and restored before relying on it, and repeat that check periodically.

<a id="ug-sec-audit-log" class="anchor"></a>

### 2.5 Forwarding the audit log

**Applies to:** Central Server, Security Server

The audit log records every change an administrator makes to the system state or configuration through the Admin UI or the management REST API, whether the attempt succeeds or fails, together with the user name, authentication type used, and a correlation identifier. The events are enumerated in \[[SPEC-AL](#Ref_SPEC-AL)\]. The audit log provides a record of who changed what and when.

When the audit log is stored only on the host, its integrity depends on the security of the host. Anyone who gains administrative access—whether an intruder or an administrator acting outside their expected role—can alter or delete the local audit log, potentially removing evidence of the actions that led to the compromise. Forwarding the audit log to an independent system, such as a SIEM or central log server, protects the record from someone who gains control of the X-Road host and makes it more reliable as evidence.

**Forward continuously rather than archiving periodically.** \[[UG-SS](#Ref_UG-SS)\] and \[[UG-CS](#Ref_UG-CS)\] recommend archiving the audit log to external storage or a log server to save disk space and preserve the log in case of a system failure. From a security perspective, logs should be forwarded as soon as they are created. Any audit records that remain only on the host can still be altered or deleted by an attacker who gains control of the host. Therefore, forward audit records as they are written instead of copying log files on a schedule.

**Protect the forwarding channel.** The audit log contains user names, X-Road identifiers, API URLs, and information about why actions failed. When forwarding the audit log, use an authenticated and encrypted transport to protect the records from disclosure and allow the receiving system to verify their origin. The transport should also provide reliable delivery to prevent audit records from being silently lost in transit. X-Road writes audit records through syslog, so log forwarding is configured using rsyslog. The location of the relevant configuration is described in the "Changing the Configuration of the Audit Log" section of \[[UG-SS](#Ref_UG-SS)\] and \[[UG-CS](#Ref_UG-CS)\].

**Configure alerts for security-relevant events instead of only collecting them.** Audit logs provide limited security value if relevant events are not actively monitored. At a minimum, configure alerts for failed authentication and token login attempts; the creation, modification, and deletion of API keys; changes to user roles and permissions; and key and certificate operations. On a Central Server, also configure alerts for changes to the member and Security Server registry, approved certification and time-stamping authorities, and trusted anchors. These changes can affect the security policy of the entire X-Road instance. \[[SPEC-AL](#Ref_SPEC-AL)\] lists the complete set of audit events that can be monitored.

**Forward the host's operating system audit logs as well.** The X-Road audit log does not record changes made outside the Admin UI and management REST API. These include installing or upgrading the software, creating operating system users or changing their permissions, and editing configuration files directly. Such actions can be performed by anyone with sufficient access to the host and may be security-relevant. Therefore, operating system audit logs should be collected and forwarded alongside the X-Road audit log to provide a more complete audit trail.

#### 2.5.1 Considerations and risks

The audit log records configuration changes, not data access. For example, it shows when a service or access right was added, but not which messages were later exchanged using that service or access right. Message exchanges are recorded in the message log. The two logs serve different purposes, and an investigation usually requires both.

The audit log is not a record of personal data, but it is still sensitive. User names identify technical accounts rather than individuals, while identifiers and URLs describe members, subsystems, and administrative operations.

The audit log also reveals security-relevant information about the deployment. It shows which accounts exist, what they do, and which actions succeed or fail. This information can be useful to an attacker. The integrity of the audit log must also be protected because its value as evidence depends on the records being trustworthy.

<a id="ug-sec-ss-controls" class="anchor"></a>

## 3. Security Server administrator controls

**Applies to:** Security Server

The controls in this section are configured separately on each Security Server by its administrator. They define how the Security Server interacts with the parties it exchanges messages with and how it handles the configuration it downloads from its configuration source.

<a id="ug-sec-access-control" class="anchor"></a>

### 3.1 Access control

**Applies to:** Security Server

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

**Applies to:** Security Server

Where the X-Road operator publishes the global configuration over HTTPS, as described in section [4.1](#41-publishing-global-configuration-over-https), the TLS certificate used by the global configuration endpoint must be signed by a trusted CA (one trusted by the JAVA installation).

If the certificate isn't trusted by the Security Server's JAVA installation by default, it can be manually added to the system truststore by following the steps below:

**Example on Ubuntu 20.04 / 22.04**

Copy the `.crt` file (PEM) into the `/usr/local/share/ca-certificates` folder.

Run `sudo update-ca-certificates`.

**Example on RHEL 9 / 10**

Copy the `.crt` file (PEM or DER) into the `/etc/pki/ca-trust/source/anchors` folder.

Run `sudo update-ca-trust extract`.

It is possible to disable the verification of the global configuration endpoint’s TLS certificate via system properties. The verification may be disabled in test and development environments. Instead, the verification must always be enabled in production environments. System parameters are specified in the [UG-SYSPAR](#Ref_UG-SYSPAR) section "Configuration Client parameters: [configuration-client]".

<a id="ug-sec-message-log" class="anchor"></a>

### 3.3 Message log and data protection

**Applies to:** Security Server

By default, the Security Server logs the messages it exchanges in full, including the business payload. Messages are first stored in the database and then archived in files on disk. If the messages contain personal data or other confidential information, the message log can become the largest concentration of such data on the server. This data is stored at rest and retained after the message exchange has completed. The security exposure and evidential value of the message log depend mainly on four factors: how much information is logged, whether the logged information is encrypted, how long and where it is retained, and whether its integrity is verified.

**Log no more than is needed**. Message logging has three modes: full logging, metadata-only logging, and no logging. Only full logging produces records with evidential value. Full and metadata-only logging can be configured for the Security Server as a whole or separately for each subsystem, while disabling logging entirely is a Security Server-level setting. If a service does not require evidential records, consider using metadata-only logging. This keeps the business payload out of both the database and archive files, providing stronger protection than storing the payload and relying on encryption. Make the logging decision separately for each subsystem rather than applying the same setting to the entire Security Server, and review the decision when new services are added. The logging modes and their configuration are described in the "Message Log" section of \[[UG-SS](#Ref_UG-SS)\].

**Encrypt what is logged.** When the business payload is logged, two separate encryption settings protect it at different stages. Both are disabled by default:

* `messagelog-encryption-enabled` encrypts message payloads stored in the database;
* `archive-encryption-enabled` encrypts the archive files, optionally under per-member keys when `archive-grouping` is enabled.

Enable both settings whenever message payloads are logged. This protects the payload both while it is stored in the database and after it has been moved to archive files. The keystore, key identifiers, GnuPG keyring and per-member key mapping are described in \[[UG-SS](#Ref_UG-SS)\] sections "Message Log Encryption" and "Archive Encryption and Grouping". The related parameters are documented in \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

**Keep it no longer than is needed, and not on the Security Server.** Records are archived to the local file system on the archiver's schedule. The `keep-records-for` parameter instead controls how long timestamped and archived records remain in the database before they are deleted. Local archive storage should be treated as temporary working space, not as long-term storage. Move archive files to appropriately managed external storage and apply a retention policy that reflects how long the records are actually needed. Retention is both a storage and a data protection control: records that have been deleted can no longer be disclosed.

Retention requirements depend on the data contained in the records, not on the Security Server that stores them. When several information systems share a Security Server, their records may have different retention periods or different requirements for what may be retained. Therefore, a single retention policy for the entire Security Server may not meet the requirements of every information system. Define retention requirements separately for each information system. Where the requirements differ, use `archive-grouping` by member or subsystem so that each group of archives can be moved, retained, and deleted according to its own schedule. Configure grouping before archives begin to accumulate, as separating them afterwards is considerably more difficult.

**Verify the integrity of archived records.** Each archive includes a linking information file containing a hash chain that covers the containers in the archive and continues from the preceding archive. This makes modifications to the archived records detectable. Verify archive integrity regularly and after each transfer to external storage, where the archives are no longer protected by the Security Server. The archive verification tool and instructions for using it are described in \[[MLAV](#Ref_MLAV)\].

#### 3.3.1 Considerations and risks

Enabling encryption does not affect records that already exist. Message payloads logged before message log encryption is enabled remain in the database in plaintext, and archives created before archive encryption is enabled remain unencrypted. Encryption therefore protects only records created after it is enabled. Earlier records must be managed through appropriate retention and deletion.

Message log encryption depends on a keystore whose password is stored in plaintext in a configuration file using the `messagelog-keystore-password` parameter. The protection provided by message log encryption therefore depends on protecting both the configuration file and the keystore. Neither is protected by message log encryption itself.

Per-member archive keys determine who can decrypt an archive. When `archive-grouping` is used with per-member keys, an archive encrypted with a member's key cannot be decrypted without the corresponding key, including by the Security Server administrator. Before enabling this configuration, establish how the keys will be managed and ensure that the resulting archives can be successfully decrypted. Otherwise, archives may be created that nobody with access to the Security Server can decrypt.

Archive verification checks both the contents of each archive and the continuity between archives. The hash chain of each archive continues from the last hash step of the preceding archive. Archives must therefore be verified in order, and the last hash step must be retained for verifying the next archive. Once an archive is deleted, it can no longer be verified. If its last hash step was not recorded before deletion, subsequent archives cannot be verified either. Therefore, verify archives and retain the required hash information before deleting them.

Moving an archive does not remove the responsibility to protect its contents. An archive contains the same message content that was stored in the database. Unless archive encryption is enabled, that content leaves the Security Server unencrypted. The required protection depends on the data and its classification, regardless of where the archive is stored. The transfer channel, external storage, and access to the archives must therefore meet the security requirements defined by that classification. The same principle applies to retention.

By default, every processed message is time-stamped. The `acceptable-timestamp-failure-period` parameter defines how long the Security Server continues exchanging messages while asynchronous time-stamping is failing. A lower value prioritises evidential value by stopping message exchange sooner, while a higher value prioritises availability by allowing message exchange to continue for longer. Setting `acceptable-timestamp-failure-period` to `0` disables this check. In that case, messages can continue to be exchanged and logged indefinitely without being time-stamped. This deliberately weakens the evidential value of the message log and should not be used in production.

<a id="ug-sec-operator-controls" class="anchor"></a>

## 4. X-Road operator controls

**Applies to:** Central Server, Configuration Proxy

The controls in this section are configured by the governing authority on the Central Server and, where deployed, the Configuration Proxy. They apply to the X-Road instance as a whole rather than to an individual member.

The Central Server and the Configuration Proxy are supported on Ubuntu only, so this section contains no RHEL-specific instructions.

<a id="ug-sec-globalconf-https" class="anchor"></a>

### 4.1 Publishing global configuration over HTTPS

**Applies to:** Central Server, Configuration Proxy

Starting from X-Road version 7.4, it is possible to publish global configuration over HTTPS using a TLS certificate issued by a trusted CA. The CA must be trusted by the Security Server's Java installation. See the Central Server User Guide [UG-CS](#Ref_UG-CS) for details.

The corresponding step on the Security Server side, adding the CA to the Security Server's truststore, is described in section [3.2](#32-trusting-the-global-configuration-endpoint-certificate) and is the responsibility of each Security Server administrator.

<a id="ug-sec-cs-tls" class="anchor"></a>

#### 4.1.1 Central Server TLS configuration

**Applies to:** Central Server

To configure the Central Server to use a certificate issued by a trusted CA for serving global configurations over HTTPS follow "Central Server Installation Guide" [IG-CS](#Ref_IG-CS) section "Configuring TLS Certificates".

<a id="ug-sec-confproxy-tls" class="anchor"></a>

#### 4.1.2 Configuration Proxy TLS configuration

**Applies to:** Configuration Proxy

To configure the Configuration Proxy to use a certificate issued by a trusted CA follow "Configuration Proxy Manual" [UG-CP](#Ref_UG-CP) section "Configuring TLS Certificates".
