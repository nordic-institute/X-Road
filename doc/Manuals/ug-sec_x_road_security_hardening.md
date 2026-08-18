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

* the **X-Road operator**, the governing authority responsible for the Central Server and, where deployed, the Configuration Proxy — that is, for the security policy of the whole X-Road instance;
* the **Security Server administrator**, responsible for a single member's Security Server.

Some controls belong to both roles, some to only one, and the controls available to the operator differ between the two components they administer. Each section states the components it applies to.

### 1.2 How this guide is organised

The hardening measures are grouped into three main sections:

* Section [2](#2-x-road-operator-and-security-server-administrator-controls) — hardening addressed to both roles: operating system accounts, the Admin UI, the token PIN policy, backups and the audit log.
* Section [3](#3-security-server-administrator-controls) — controls available to the administrator of an individual Security Server.
* Section [4](#4-x-road-operator-controls) — controls available to the governing authority operating the Central Server and the Configuration Proxy.

The section titles name the **role** the controls are addressed to, so that an administrator can tell at a glance which sections are theirs to read. Each section and subsection then states an **Applies to** line naming the **components** its controls apply to. The line is repeated on every subsection rather than left to be inferred from the section above it, so that a reader arriving at a subsection directly — from the table of contents, from a search, or from a link in another document — sees its scope without having to look elsewhere. The innermost subsections, which only elaborate on the control they sit under, carry the line only where their scope is narrower than that control's.

The two are stated separately because they do not coincide: the X-Road operator administers two components that differ in what can be hardened. The Configuration Proxy has no Admin UI and no web application users, so the user management and Admin UI controls of section [2](#2-x-road-operator-and-security-server-administrator-controls) are addressed to the operator but apply only to the Central Server, while the token PIN policy in the same section applies to the Configuration Proxy too. Read the sections that name your role, and within them apply only what the **Applies to** line names for the components you run.

Every section also carries a stable anchor that does not change when sections are renumbered, so that other documents — in particular the X-Road threat model \[[ARC-TM](#Ref_ARC-TM)\] — can cite a control without the citation breaking at the next revision.

### 1.3 Deployment models

A Security Server can be installed on a Linux host from native packages, or run as a container using the Security Server Sidecar, either directly on Docker or in a Kubernetes cluster. The Central Server and the Configuration Proxy are available as native packages only.

This guide is the baseline for all of them. The controls it describes are properties of the X-Road software rather than of the platform underneath it, so the token PIN policy, backup encryption, message log protection, audit log forwarding and the controls in sections [3](#3-security-server-administrator-controls) and [4](#4-x-road-operator-controls) apply to a Security Server whatever it runs on.

A container deployment adds a platform that also has to be secured, and this guide does not cover it. Two further guides do, and both are additions to this one rather than alternatives to it:

* The Security Server Sidecar Security Guide \[[UG-SS-SEC-SIDECAR](#Ref_UG-SS-SEC-SIDECAR)\] covers the Docker host, the Docker daemon and the container runtime;
* The Kubernetes Security Server Sidecar Security User Guide \[[UG-K-SS-SEC-SIDECAR](#Ref_UG-K-SS-SEC-SIDECAR)\] covers the Kubernetes cluster — secrets, cluster access, network policies and pod security. A Kubernetes deployment still runs containers, so the Docker guide applies there as well.

Two consequences are worth stating plainly. Applying a platform guide alone leaves the X-Road controls in this document unapplied, because nothing in either platform guide sets a token PIN policy, enables backup encryption or forwards an audit log. And section [2.1](#21-user-management) assumes a Linux host that administrators log in to; where a Security Server runs as a container, the equivalent concerns — how administrator credentials are supplied to the container, and who may reach the container runtime or the cluster — are addressed in the platform guides instead.

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

Not every control reaches every component. For example, sections [2.1](#21-user-management) and [2.2](#22-admin-ui) harden the Admin UI and the operating system accounts that authenticate to it, and section [2.4](#24-encrypting-backups) covers backups; none of the three applies to the Configuration Proxy, which has no Admin UI, no web application users and no backup mechanism, and which is administered from the command line as described in the Configuration Proxy Manual \[[UG-CP](#Ref_UG-CP)\]. Section [2.3](#23-enforcing-the-software-token-pin-policy) protects key material held by the signer and applies to all three components.

<a id="ug-sec-user-management" class="anchor"></a>

### 2.1 User management

**Applies to:** Central Server, Security Server

This section assumes a component installed on a Linux host whose administrators have operating system accounts on it. Where a Security Server runs as a container instead, the accounts described here belong to the host or to the cluster rather than to the Security Server; see section [1.3](#13-deployment-models).

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

To enable it, add the following to `/etc/xroad/conf.d/local.ini` and then restart signer:

```ini
[signer]
enforce-token-pin-policy = true
```

When the policy is enforced, a software token PIN must be

* at least 10 characters long, and
* composed of characters from at least three of the four character classes: lower-case letters, upper-case letters, digits, and special characters.

Only printable ASCII characters are accepted. A PIN containing any character outside that range is rejected regardless of its length.

Some country-specific meta-packages set the parameter already. The Finnish and Estonian Security Server packages both ship `enforce-token-pin-policy = true`, so on those installations the policy is in force without further configuration. Check the effective value before assuming it is unset. The parameter is described for each component in [UG-SYSPAR](#Ref_UG-SYSPAR) sections "Signer parameters: [signer]".

#### 2.3.1 Considerations and risks

The policy is applied when a PIN is set or changed. It is not applied to a PIN that already exists, so enabling the parameter on a running system does not strengthen the PIN currently in use — the PIN has to be changed for the policy to take effect. Changing it is part of enabling this control, not an optional follow-up.

The policy governs software tokens only. Where keys are held on an SSCD or a hardware security module, the PIN or passphrase rules are those of the device, and `enforce-token-pin-policy` has no effect on them. Note that Security Server authentication keys are supported on a software token only, so they are always protected by the software token PIN.

A longer PIN is harder to type, and the token PIN is not stored on disk by default: it is held in memory only while the token is logged in and is cleared when the server restarts, so the token has to be logged in again by hand after every restart. Where that is automated with the autologin add-on, the PIN is read at start-up from a file or from a source of the administrator's choosing. In that case the protection of the PIN depends on how that source is secured, and a strong PIN in a world-readable file is no stronger than the file.

<a id="ug-sec-backup-encryption" class="anchor"></a>

### 2.4 Encrypting backups

**Applies to:** Central Server, Security Server

A backup contains most of `/etc/xroad` together with a database dump. That includes the software token private keys and the internal TLS key material, so a single backup file is enough to reconstruct the server's identity and to use its keys. A backup that leaves the host, or that can be read by anyone with access to the backup storage, is worth as much to an attacker as the key material it contains.

Some paths are deliberately excluded, among them the OpenPGP keyring in `/etc/xroad/gpghome` — the keyring holding the keys used to sign and encrypt the backup in the first place. It is therefore not enough to keep backups: the keyring has to be preserved separately, or a backup cannot be decrypted after the server it came from is rebuilt or replaced.

Backups are always signed and the signature is verified on restore, so their integrity is protected out of the box. Encryption is a separate setting and is **off** by default: `backup-encryption-enabled` defaults to `false`. It is recommended to enable it on both the Central Server and the Security Server, and to set at least one additional recipient in `backup-encryption-keyids`. Without one, a backup is encrypted only to the server's own key, which lives in the keyring that the backup does not contain — so an additional recipient whose private key is held off the server is what makes an encrypted backup recoverable once that server is gone.

The procedure is documented in full elsewhere and is not repeated here. For where the parameters are set, how to generate an additional key pair, how to import and trust it in the `/etc/xroad/gpghome` keyring, and how to decrypt a backup, see:

* Security Server — \[[UG-SS](#Ref_UG-SS)\] section "Backup Encryption Configuration";
* Central Server — \[[UG-CS](#Ref_UG-CS)\] section "Backup Encryption Configuration";
* parameter reference — \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

#### 2.4.1 Considerations and risks

`backup-encryption-keyids` is security-relevant in both directions. It is normally thought of as a recovery measure, but every key listed on it can decrypt every backup taken afterwards. An unauthorised recipient added to the list turns each later backup into a readable copy of the server's key material, and the backup process itself will not report anything unusual. Treat the list as a privileged setting: keep it under change control and review it whenever administrator access changes.

X-Road applies no strength or validity checks to the keys named in the list. Confirming that each key is strong enough, that its fingerprint is the expected one, and that its private key is held securely away from the server is the administrator's responsibility.

Encryption protects the backup file, not the place it is kept. Backups downloaded through the Admin UI or copied to external or long-term storage leave the protection of the host behind, so the storage location, the transfer channel, and who may retrieve a backup all need to be controlled to the same standard as the server itself.

Enabling encryption changes the restore path. Confirm that a backup taken after the change can actually be decrypted and restored before relying on it, and repeat that check periodically. An encrypted backup that cannot be decrypted is a loss of availability, which for backups is as damaging as a loss of confidentiality.

<a id="ug-sec-audit-log" class="anchor"></a>

### 2.5 Forwarding the audit log

**Applies to:** Central Server, Security Server

The audit log records every change an administrator makes to the system state or configuration through the Admin UI or the management REST API, whether the attempt succeeded or failed, together with the user name, the authentication type used and a correlation identifier. The events are enumerated in \[[SPEC-AL](#Ref_SPEC-AL)\]. It is the record of who changed what, and on a Central Server it covers the registry and trust decisions that the whole ecosystem relies on.

Kept only on the host, that record is no more trustworthy than the host. Anyone who obtains administrative access — an intruder, or an administrator acting outside their remit — can alter or delete the local file, and the actions that led to the access disappear with it. Forwarding the audit log to an independent system such as a SIEM or a central log server places the record beyond the reach of whoever controls the X-Road host, which is what makes it dependable as evidence.

**Forward continuously rather than archiving periodically.** \[[UG-SS](#Ref_UG-SS)\] and \[[UG-CS](#Ref_UG-CS)\] recommend archiving the audit log to external storage or a log server to save disk space and to survive a crash. For security purposes the timing is what matters: anything not yet sent can still be altered on the host, and that gap is exactly when an attacker is active. Relay records as they are written instead of copying files on a schedule.

**Protect the forwarding channel.** The audit log carries user names, X-Road identifiers, API URLs and the reasons actions failed. Forward it over an authenticated and encrypted transport, and prefer a reliable one, so that records are neither disclosed nor silently dropped on the way and the receiving system can rely on their origin. X-Road writes the audit log through syslog, so this is rsyslog configuration; where that configuration lives is described in \[[UG-SS](#Ref_UG-SS)\] and \[[UG-CS](#Ref_UG-CS)\] section "Changing the Configuration of the Audit Log".

**Alert on the events that matter, do not only collect them.** A log nobody reads detects nothing. At a minimum, alert on failed authentication and failed token log-ins, on the creation, modification and deletion of API keys, on changes to user roles and permissions, and on key and certificate operations. On a Central Server, add changes to the member and Security Server registry, to approved certification and time-stamping authorities, and to trusted anchors, since those alter the security policy of the entire instance. \[[SPEC-AL](#Ref_SPEC-AL)\] lists the complete set of events to select from.

**Forward the host's own audit trail as well.** Changes made outside the Admin UI and the management REST API are not in the X-Road audit log at all — installing and upgrading the software, creating operating system users and granting them permissions, and editing configuration files directly. Those are precisely the actions of someone who already has access to the host. Operating system level auditing has to be collected and forwarded alongside the X-Road audit log for the record to be complete.

#### 2.5.1 Considerations and risks

The audit log records configuration changes, not data access. It will show that a service or an access right was added, but not which messages were subsequently exchanged under it; that is what the message log holds. Neither log answers the other's questions, and an investigation usually needs both.

The audit log is not a record of personal data, but it is sensitive in its own right. The user names in it are technical accounts rather than the names of individuals, and the identifiers and URLs describe members, subsystems and administrative operations rather than people. What it does expose is the administrative surface of the deployment: which accounts exist, what they do, and which of their actions succeed and fail. That is useful to anyone preparing an attack, and its integrity is what its value as evidence rests on. Forwarding the log to a SIEM does not hand that responsibility to the SIEM's operator; it extends it to a second system that now holds the same record.

Correlating events across hosts depends on their clocks. The correlation identifier links records belonging to the same request on one server, but reconstructing a sequence that spans a Security Server, a Central Server and the SIEM relies on those systems agreeing about the time. Keep the hosts synchronised to a trusted time source and monitor for drift, or the order of events in an investigation cannot be trusted.

<a id="ug-sec-ss-controls" class="anchor"></a>

## 3. Security Server administrator controls

**Applies to:** Security Server

The controls in this section are configured on an individual Security Server by its administrator. They govern how that Security Server treats the parties it exchanges messages with and the configuration source it downloads from.

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

By default the Security Server logs the messages it exchanges in full, bodies included, first to its database and then to archive files on disk. Where those messages carry personal data or other confidential content, the message log becomes the largest concentration of that data on the server, held at rest and retained after the exchange itself has finished. How much exposure that creates, and how much the records are worth as evidence, comes down to four decisions: how much is logged, whether what is logged is encrypted, how long and where it is kept, and whether its integrity is checked.

**Log no more than is needed.** Message logging has three modes — full logging, metadata-only logging, and no logging — and only full logging produces records with evidential value. Full and metadata logging can be set for the Security Server as a whole or per subsystem; disabling logging altogether is a Security Server-level setting. Where a service does not need evidential value, logging metadata only keeps the message bodies out of the database and the archives entirely, which protects them more effectively than any encryption setting can. Make this choice per subsystem rather than once for the whole server, and revisit it when services are added. The modes and their configuration are described in \[[UG-SS](#Ref_UG-SS)\] section "Message Log".

**Encrypt what is logged.** Where bodies are logged, two independent encryption settings apply and both are off by default:

* `messagelog-encryption-enabled` encrypts message bodies in the database;
* `archive-encryption-enabled` encrypts the archive files, optionally under per-member keys when `archive-grouping` is used.

Enabling both is recommended wherever message bodies are logged. The keystore, key identifiers, GnuPG keyring and per-member key mapping are described in \[[UG-SS](#Ref_UG-SS)\] sections "Message Log Encryption" and "Archive Encryption and Grouping", and the parameters in \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

**Keep it no longer than is needed, and not on the Security Server.** Records are archived to the local file system on the archiver's schedule. The `keep-records-for` parameter instead controls how long timestamped and archived records remain in the database before they are deleted. Local archive storage is working space, not long-term storage: archive files should be moved to external storage that is managed for them. Retention is a data protection control as much as a storage one, because a record that no longer exists cannot be disclosed.

Retention requirements follow the data in the records, not the server that happens to hold them. Where several information systems share one Security Server, their records may be subject to different retention periods and different rules about what may be kept at all, so a single retention decision taken for the whole server will be wrong for some of them. Establish the requirement per information system, and where the requirements differ, use `archive-grouping` by member or subsystem so that each group can be moved, retained and deleted on its own schedule. Grouping has to be in place before the archives accumulate; separating them afterwards is considerably harder.

**Check that what is kept is intact.** Each archive carries a linking information file whose hash chain covers the containers inside it and continues the chain from the preceding archive, so tampering with an archive is detectable. Verify archives on a schedule, and after each transfer to external storage, where the Security Server no longer protects them. The verifier and its usage are described in \[[MLAV](#Ref_MLAV)\].

#### 3.3.1 Considerations and risks

Enabling encryption does not change records that already exist. Message bodies logged before the change remain in the database in plaintext and archives written before it remain unencrypted, so switching encryption on protects future traffic only. Earlier records have to be addressed through retention instead.

Message log encryption depends on a keystore whose password is given by `messagelog-keystore-password`, in plaintext, in a configuration file. The protection of the message bodies is therefore no stronger than the protection of that file and of the keystore it points to, and neither of those is protected by the encryption itself.

Per-member archive keys change who can read an archive. Where grouping is used with per-member keys, an archive encrypted to a member's key cannot be read without that key, including by the Security Server administrator. Arrange key custody and confirm that an archive can actually be decrypted before enabling it, or the archives will be written in a form nobody at hand can open.

Archive verification checks both the contents within each archive and continuity across archives. Each archive's hash chain continues from the last hash step of the one before it, so archives have to be verified in order and that last hash step retained for the next verification. Deleting an archive means it can no longer be verified at all, and unless its last hash step was recorded first, no later archive can be verified either — which is a reason to verify before deleting anything, not after.

Moving archives to external storage moves the data, not the duty to protect it. An archive carries the same message content as the database it came from, so unless archive encryption is enabled that content leaves the host in the clear. What protection it needs is set by the data itself and its classification, not by where it happens to be held, so the transfer channel, the destination and access to it have to satisfy the requirements that follow from that classification — the same requirements that determine how the Security Server holding the data is protected. The retention rules follow the records in the same way, including the eventual deletion, which now has to happen somewhere the Security Server no longer reaches. See \[[UG-SS](#Ref_UG-SS)\] section "Transferring the Archive Files from the Security Server".

By default, every processed message is time-stamped, and `acceptable-timestamp-failure-period` sets how long the Security Server keeps exchanging messages while asynchronous time-stamping is failing. Lowering it favours evidential value over availability by stopping message exchange sooner; raising it does the reverse. Setting it to `0` removes the check altogether, so messages continue to be exchanged and logged indefinitely without being time-stamped. That is a deliberate weakening of the evidential value of the log and should not be done in production.

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
