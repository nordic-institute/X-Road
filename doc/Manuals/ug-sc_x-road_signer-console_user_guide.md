# SIGNER CONSOLE USER GUIDE <!-- omit in toc -->

**X-ROAD 7**

Version: 2.9  
Doc. ID: UG-SC

---


## Version history <!-- omit in toc -->

 Date     | Version | Description                                                               | Author
 -------- |---------|---------------------------------------------------------------------------| --------------------
 20.11.2014 | 0.1     | First draft                                                               |
 20.11.2014 | 0.2     | Some improvements done                                                    |
 01.12.2014 | 1.0     | Minor corrections done                                                    |
 19.01.2015 | 1.1     | License information added                                                 |
 02.04.2015 | 1.2     | "sdsb" changed to "xroad"                                                 |
 30.06.2015 | 1.3     | Minor corrections done                                                    |
 09.09.2015 | 2.0     | Editorial changes made                                                    |
 14.09.2015 | 2.1     | Audit log added                                                           |
 20.09.2015 | 2.2     | Editorial changes made                                                    |
 06.09.2015 | 2.3     | Added certificate request format argument                                 |
 03.11.2015 | 2.4     | Added label parameter for key generation command                          |
 10.12.2015 | 2.5     | Editorial changes made                                                    |
 26.02.2021 | 2.6     | Convert documentation to markdown                                         | Caro Hautamäki
 01.03.2021 | 2.7     | Added [2.4.19 update-software-token-pin](#2419-update-software-token-pin) | Caro Hautamäki
 25.08.2021 | 2.8     | Update X-Road references from version 6 to 7                              | Caro Hautamäki
 01.06.2023 | 2.9     | Update references                                                         | Petteri Kivimäki

 
## Table of Contents <!-- omit in toc -->

<!-- toc -->
<!-- vim-markdown-toc GFM -->

- [License](#license)
- [1 Introduction](#1-introduction)
  - [1.1 References](#11-references)
- [2 Using the Signer console](#2-using-the-signer-console)
  - [2.1 Signer console options](#21-signer-console-options)
  - [2.2 Starting as interactive shell](#22-starting-as-interactive-shell)
  - [2.3 Executing single commands](#23-executing-single-commands)
  - [2.4 Available commands](#24-available-commands)
    - [2.4.1 list-tokens](#241-list-tokens)
    - [2.4.2 list-keys](#242-list-keys)
    - [2.4.3 list-certs](#243-list-certs)
    - [2.4.4 set-token-friendly-name](#244-set-token-friendly-name)
    - [2.4.5 set-key-friendly-name](#245-set-key-friendly-name)
    - [2.4.6 get-member-certs](#246-get-member-certs)
    - [2.4.7 activate-certificate](#247-activate-certificate)
    - [2.4.8 deactivate-certificate](#248-deactivate-certificate)
    - [2.4.9 delete-key](#249-delete-key)
    - [2.4.10 delete-certificate](#2410-delete-certificate)
    - [2.4.11 delete-certificate-request](#2411-delete-certificate-request)
    - [2.4.12 import-certificate](#2412-import-certificate)
    - [2.4.13 login-token](#2413-login-token)
    - [2.4.14 logout-token](#2414-logout-token)
    - [2.4.15 init-software-token](#2415-init-software-token)
    - [2.4.16 sign](#2416-sign)
    - [2.4.17 generate-key](#2417-generate-key)
    - [2.4.18 generate-cert-request](#2418-generate-cert-request)
    - [2.4.19 update-software-token-pin](#2419-update-software-token-pin)
- [3 Example: Certificate Import](#3-example-certificate-import)
- [4 Audit log](#4-audit-log)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction

The purpose of this document is to explain how to manage keys and certificates in signer from the command line using the signer console utility.

*Signer* is an X-Road component whose primary purpose is to process signing requests and produce signatures. Signer manages software and hardware (smart cards, HSMs) tokens and their keys and handles certificates associated with the keys.

*Signer console* is a command line utility for interacting with Signer. The utility provides commands for various operations and can execute a single command or be started as an interactive text-based shell.

### 1.1 References

1.  <a id="Ref_SPEC-AL" class="anchor"></a>\[SPEC-AL\] X-Road: Audit log events. Document ID: [SPEC-AL](../Architecture/spec-al_x-road_audit_log_events.md)

2.  <a id="Ref_JSON" class="anchor"></a>\[JSON\] Introducing JSON, <http://json.org/>

## 2 Using the Signer console

### 2.1 Signer console options

Signer console accepts the following options:
- `-h` or `-help` displays list of supported commands
- `-v` or `-verbose` displays more detailed execution output

### 2.2 Starting as interactive shell

To start the signer console as an interactive shell, type 
```bash
sudo -u xroad -i signer-console [<options>]
```

### 2.3 Executing single commands

To execute a single command, type
```bash
sudo -u xroad -i signer-console [<options>] <command> [<command arguments>]
```

### 2.4 Available commands

This section gives an overview of all available commands in signer console.

A command may have one or more arguments, and may or may not produce any output. If command has arguments, they are always mandatory. 

#### 2.4.1 list-tokens

**Description:** Lists all tokens.

**Arguments:** (none)

**Output:** List of all tokens, each line representing the following token information:
```
<id> (<status>, <read only|writable>, <available|unavailable>, <active|inactive>)
```

#### 2.4.2 list-keys

**Description:** Lists all keys on all tokens.

**Arguments:** (none)

**Output:** List of keys on all tokens, each line representing the following key information:
```
<id> (<usage>, <available|unavailable>)
```

#### 2.4.3 list-certs

**Description:** Lists all certificates and certificate requests on all keys.

**Arguments:** (none)

**Output:** List of certificates and certificate requests on all keys, each line representing the following 
```
<id> (<status>, <client id>)
```

#### 2.4.4 set-token-friendly-name

**Description:** Sets friendly name to the specified token.

**Arguments:**
* ***token id***: the identifier of the token. Use *[list-tokens](#241-list-tokens)* to look up token id-s.
* ***friendly name***: the name to set.

**Output:** (none)

#### 2.4.5 set-key-friendly-name

**Description:** Sets friendly name to the specified key.

**Arguments:**
* ***key id***: the identifier of the key. Use *[list-keys](#242-list-keys)* to look up key id-s.
* ***friendly name***: the name to set.

**Output:** (none)

#### 2.4.6 get-member-certs

**Description:** Returns certificates of a member.

**Arguments:**
* ***member id***: the identifier of the member, entered as `\"<instance> <class> <code>\"`

**Output:** List of certificates of the specified member.

#### 2.4.7 activate-certificate

**Description:** Activates the specified certificate.

**Arguments:**
* ***certificate id***: the identifier of the certificate. Use *[list-certs](#243-list-certs)* to look up certificate identifiers.

**Output:** (none)

#### 2.4.8 deactivate-certificate

**Description:** Deactivates the specified certificate.

**Arguments:**
* ***certificate id***: the identifier of the certificate. Use *[list-certs](#243-list-certs)* to look up certificate identifiers.

**Output:** (none)

#### 2.4.9 delete-key

**Description:** Deletes the specified key and all associated certificates and certificate requests.

**Arguments:**
* ***key id***: the identifier of the key. Use *[list-keys](#242-list-keys)* to look up key id-s.

**Output:** (none)

#### 2.4.10 delete-certificate

**Description:** Deletes the specified certificate from Signer.

**Arguments:**
* ***certificate id***: the identifier of the certificate. Use *[list-certs](#243-list-certs)* to look up certificate identifiers.

**Output:** (none)

#### 2.4.11 delete-certificate-request

**Description:** Lists all tokens.

**Arguments:**
* ***certificate request id***: the identifier of the certificate request. Use *[list-certs](#243-list-certs)* to look up certificate request identifiers.

**Output:** (none)

#### 2.4.12 import-certificate

**Description:** Imports a certificate from the specified file to a key. The certificate is imported to the key pair whose public key matches that of the certificate.

**Arguments:**
* ***file***: the relative or absolute file name of the certificate in PEM or DER format.
* ***member id***: the identifier of the member constructed from the C, O, CN fields of the certificates DN, entered as: `\"<instance> <class> <code>\"`

**Output:** Identifier of the key to which the certificate was imported.

#### 2.4.13 login-token

**Description:** Log in to the specified token.

**Arguments:**
* ***token id***: the identifier of the token. Use *[list-tokens](#241-list-tokens)* to look up token identifiers.

**Output:** (none)

#### 2.4.14 logout-token

**Description:** Log out of the specified token

**Arguments:**
* ***token id***: the identifier of the token. Use *[list-tokens](#241-list-tokens)* to look up token identifiers.

**Output:** (none)

#### 2.4.15 init-software-token

**Description:** Initialize the software token. A PIN is prompted that is used to log in to this token.

**Arguments:** (none)

**Output:** (none)

#### 2.4.16 sign

**Description:** Sign the specified character data using the specified key.

**Arguments:**
* ***key id***: the identifier of the key. Use *[list-keys](#242-list-keys)* to look up key identifiers.
* ***data***: character data to be signed as string

**Output:** signature byte array

#### 2.4.17 generate-key

**Description:** Generates a key on the specified token.

**Arguments:**
* ***token id***: the identifier of the token. Use *[list-tokens](#241-list-tokens)* to look up token identifiers.
* ***label***: the label of the key is set for SSCD devices.

**Output:** The id of the generated key.

#### 2.4.18 generate-cert-request

**Description:** Generates a certificate request under the specified key in Signer and saves it to a CSR file in current directory.

**Arguments:**
* ***key id***: the identifier of the key. Use *[list-keys](#242-list-keys)* to look up key identifiers.
* ***member id***: the identifier of the member that matches the subject name, entered as: `\"<instance> <class> <code>\"`
* ***usage***: key usage – either `s` (sign) or `a` (authentication)
* ***subject name***: the subject distinguished name, entered as: `C=<instance>,O=<class>,CN=<code>`
* ***format***: the format of the generated certificate request – either `der` or `pem`

**Output:** Name of the file where the certificate request was saved.

#### 2.4.19 update-software-token-pin

**Description:** Updates the software token's PIN code. First, the current PIN is prompted and after that the new PIN is prompted twice. 

**Arguments:** (none)

**Output:** (none)

## 3 Example: Certificate Import

The following usage example shows how to initialize a software token and import a certificate to signer.

1.  Initialize the software token
    ```bash
    signer-console init-software-token
    ```
    A PIN is prompted, this will be used to log in to the software token afterwards.

2.  Log in to the software token
    ```bash
    signer-console login-token 0
    ```
    Note, that the identifier of software token is always `0`.

3.  Generate a new key on the software token:
    ```bash
    signer-console generate-key 0
    ```
    Output is key id: 
    `F30D41B745FC072028956A3E9695416247248595`

4.  Create a certificate request:
    ```bash
    signer-console generate-cert-request F30D41B745FC072028956A3E9695416247248595 \"FOO BAR BAZ\" s "C=FOO,O=BAR,CN=BAZ" pem
    ```
    Output:
    `Saved to file F30D41B745FC072028956A3E9695416247248595.csr`

5.  Send the CSR to the Certificate Authority and get the certificate.
6.  Import the new certificate to signer
    ```bash
    signer-console import-certificate <PEM file> "SAVED" \"FOO BAR BAZ\"
    ```

## 4 Audit log

User actions events that are made by the signer-console utility and that change the system state or configuration are logged to the audit log. The actions are logged regardless of whether the outcome was a success or a failure. The complete list of the audit log events is described in \[[SPEC-AL](#Ref_SPEC-AL)\].

An audit log record contains
* the description of the user action,
* the date and time of the event,
* the user name of the user performing the action, and
* the data related to the event.

For example, logging in to the token produces the following log record:
```
2015-09-14T17:41:28+03:00 my-server-host INFO  [X-Road Signer Console] 2015-09-14 17:41:28+0300 - {"event":"Log into the token","user":"xroad","data":{"tokenId":"0"}}
```

The event is present in \[[JSON](#Ref_JSON)\] format, in order to ensure machine processability. The field `event` represents the description of the event, the field `user` represents the user name of the performer, and the field `data` represents data related with the event. The failed action event record contains an additional field `reason` for the error message. 

For example:
```
2015-09-14T17:43:07+03:00 my-server-host INFO  [X-Road Signer Console] 2015-09-14 17:43:07+0300 - {"event":"Log into the token failed","user":"xroad","reason":"Signer.PinIncorrect: PIN incorrect","data":{"tokenId":"0"}}
```

By default, in the X-Road security server and central server, audit log is located in the file
`/var/log/xroad/audit.log`

