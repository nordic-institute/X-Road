# Security and Vulnerability Disclosure Policy

[![Go to X-Road Community Slack](https://img.shields.io/badge/Go%20to%20Community%20Slack-grey.svg)](https://jointxroad.slack.com/)
[![Get invited](https://img.shields.io/badge/No%20Slack-Get%20invited-green.svg)](https://x-road.global/community)

## Supported Versions

The latest version and two preceding versions of X-Road are officially supported by NIIS. The supported versions are defined at the `MAJOR.MINOR` level so the release of patch versions (`MAJOR.MINOR.PATCH`) does not affect the support.

| Version | Supported |
|----------|-----------|
| Latest major.minor release | ✅ |
| Previous major.minor release | ✅ |
| Second previous major.minor release | ✅ |
| Older releases | ❌ |

## Security Updates

NIIS provides security fixes, mitigation guidance and other remediation measures for supported versions of X-Road where appropriate.

Users are encouraged to apply security updates without undue delay and to keep their deployments on supported versions.

## Role of NIIS

The [Nordic Institute for Interoperability Solutions (NIIS)](https://www.niis.org/) acts as the steward of the X-Road project and coordinates security vulnerability handling for X-Road Core and [official X-Road extensions](https://x-road.global/xroad-extensions) maintained by NIIS.

Operators of X-Road ecosystems remain responsible for the security of their own deployments, infrastructure, operating environments and operational processes.

## Scope

This policy applies to software components maintained by NIIS as part of the X-Road project:

- X-Road Core software
- [Official X-Road extensions](https://x-road.global/xroad-extensions) maintained by NIIS

Security issues affecting deployment-specific configurations, infrastructure, third-party extensions or integrations maintained by other organizations should be reported to the responsible operators or maintainers.

## Secure Development

NIIS integrates security activities throughout the development and maintenance of X-Road. These include code review, automated testing, dependency and vulnerability monitoring, dependency and software supply-chain controls, and generation of Software Bills of Materials (SBOMs).

Security issues identified through these activities are assessed and addressed according to NIIS vulnerability management processes.

## Reporting a Vulnerability

NIIS encourages the voluntary reporting of vulnerabilities and other security issues that may affect X-Road. If you believe you have discovered a security vulnerability in X-Road, please report it privately through one of the following channels:

- [X-Road Service Desk](https://nordic-institute.atlassian.net/servicedesk/customer/portal/4)
  - Use the `Report a software problem` request type.
  - [Sign up](https://id.atlassian.com/signup) for an account and get access to the [X-Road Service Desk](https://nordic-institute.atlassian.net/servicedesk/customer/portal/4).
- [X-Road Bug Bounty Program](https://nordic-institute.atlassian.net/wiki/spaces/XRDBUGBOUNTY)
  - Additional information regarding eligible vulnerability types, scope, rewards, disclosure practices and safe-harbor provisions is available through the X-Road Bug Bounty Program documentation.

NIIS treats vulnerability reports as confidential and shares information only as necessary to investigate, remediate and coordinate disclosure of the issue.

Please do not disclose security vulnerabilities publicly before NIIS has had an opportunity to investigate and coordinate remediation.

### Information to Include

To help us assess and address the issue efficiently, please include as much of the following information as possible:

- Affected component
- Affected version(s)
- Description of the vulnerability
- Steps required to reproduce the issue
- Proof-of-concept code or screenshots, if available
- Potential impact
- Suggested mitigation, if known

Reports that do not provide sufficient information to reproduce the issue may not be actionable.

## Vulnerability Handling

NIIS works to develop and distribute remediation measures, security updates and mitigation guidance for vulnerabilities affecting supported versions of X-Road.

Reported vulnerabilities are documented, reviewed, assessed and prioritized according to their severity, exploitability and potential impact on X-Road ecosystems.

NIIS may request additional information from the reporter during the assessment process.

NIIS aims to acknowledge vulnerability reports in a timely manner and may provide status updates during the investigation and remediation process.

## Coordinated Vulnerability Disclosure

NIIS follows a coordinated vulnerability disclosure process.

We ask security researchers and reporters to refrain from public disclosure until:

- The vulnerability has been investigated;
- Appropriate remediation or mitigation measures have been identified; and
- A coordinated disclosure plan has been agreed where appropriate.

NIIS works with reporters and relevant stakeholders to validate vulnerabilities, develop fixes, coordinate disclosures and communicate remediation guidance.

## Security Advisories

When vulnerabilities are confirmed and disclosure is appropriate, NIIS may publish security advisories describing:

- Affected versions
- Impact and severity
- Available mitigations
- Fixed versions
- Additional guidance for operators

Where appropriate, NIIS obtains or references CVE identifiers for publicly disclosed vulnerabilities.

Security-related information may be communicated through:

- X-Road release notes
- GitHub Security Advisories
- X-Road documentation
- Other official X-Road communication channels

## Vulnerability Information Sharing

NIIS promotes the sharing of information about discovered vulnerabilities within the X-Road and broader open-source communities where doing so supports effective remediation and risk management. NIIS may share vulnerability information with:

- Affected stakeholders
- Relevant ecosystem participants
- Security researchers involved in the disclosure process
- Competent authorities where required by applicable legislation

Information sharing is conducted in a manner that supports coordinated vulnerability disclosure and minimizes risks to affected users.

## Good Faith Security Research

NIIS welcomes responsible security research that helps improve the security of X-Road.

Researchers are expected to:

- Act in good faith
- Avoid privacy violations
- Avoid service disruption
- Avoid destruction or modification of data
- Refrain from public disclosure until coordinated disclosure activities have been completed

Additional safe-harbor provisions applicable to participants in the X-Road Bug Bounty Program are described in the [program documentation](https://nordic-institute.atlassian.net/wiki/spaces/XRDBUGBOUNTY/pages/188252382/Safe+Harbor).

## Cooperation with Authorities

NIIS cooperates with competent market surveillance authorities in accordance with applicable regulatory requirements, including by supporting the mitigation of cybersecurity risks affecting X-Road and providing relevant cybersecurity policy documentation upon reasoned request.

Where required by applicable legislation, NIIS reports actively exploited vulnerabilities and severe security incidents affecting network and information systems provided by NIIS for the development of X-Road to the competent authorities.

NIIS' primary establishment is in Estonia. The Computer Security Incident Response Team (CSIRT) designated as coordinator is:

CERT-EE
Email: `cert@cert.ee`
Website: https://www.ria.ee/en/cyber-security/handling-cyber-incidents-cert-ee

## Questions

If you have questions regarding the security of X-Road or this policy, please contact the [X-Road Service Desk](https://nordic-institute.atlassian.net/servicedesk/customer/portal/4) or email `security@niis.org`.