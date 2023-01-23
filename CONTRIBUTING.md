# Contributing to X-Road

[![Go to X-Road Community Slack](https://img.shields.io/badge/Go%20to%20Community%20Slack-grey.svg)](https://jointxroad.slack.com/)
[![Get invited](https://img.shields.io/badge/No%20Slack-Get%20invited-green.svg)](https://x-road.global/community)

First off, thanks for taking the time to contribute! ❤️ 

The guidelines described in this document apply to the X-Road core and all the official
[X-Road extensions](https://x-road.global/xroad-extensions) (e.g., X-Road Metrics, MISP2).

All types of contributions are encouraged and valued. See the [Table of Contents](#table-of-contents) for different ways to help and details about how this project handles them. Please make sure to read the relevant section before making your contribution. It will make it a lot easier for us maintainers and smooth out the experience for all involved. The community looks forward to your contributions. 🎉

## Table of Contents

- [I Have a Question](#i-have-a-question)
- [I Want To Contribute](#i-want-to-contribute)
  - [Legal Notice](#legal-notice)
    - [Review the CLA document](#review-the-cla-document)
    - [Sign the CLA](#sign-the-cla)
  - [Enhancement Requests and Error Reports](#enhancement-requests-and-error-reports)
    - [Submitting a Bug](#submitting-a-bug)
    - [Submitting an Enhancement Request](#submitting-an-enhancement-request)
  - [Security Issues And Vulnerabilities](#security-issues-and-vulnerabilities)
  - [Your First Code Contribution](#your-first-code-contribution)
  - [Submitting a Pull Request](#submitting-a-pull-request)
    - [Pull Request Checklist](#pull-request-checklist)
- [Styleguides](#styleguides)
  - [Branching Pattern](#branching-pattern)
  - [Pull Requests](#pull-requests)
  - [Commit Messages](#commit-messages)
  - [Tagging](#tagging)
  - [Java](#java)

## I Have a Question

Before you ask a question, it is best to check the existing resources first:

- [X-Road documentation portal](https://docs.x-road.global);
- [Knowledge Base](https://nordic-institute.atlassian.net/wiki/spaces/XRDKB);
- [List of X-Road resources](https://x-road.global/resources).

If you then still feel the need to ask a question and need clarification, please submit a question to the [X-Road community Slack](https://jointxroad.slack.com/)
or open an [issue](https://github.com/nordic-institute/X-Road/issues/new).

If you're not a member of the X-Road community Slack yet, you can join [here](https://x-road.global/community).

## I Want To Contribute

### Legal Notice

We appreciate community contributions to X-Road open source code repositories
managed by NIIS. By signing a [contributor licence agreement](https://en.wikipedia.org/wiki/Contributor_License_Agreement),
we ensure that the community is free to use your contributions.

#### Review the CLA document

The NIIS Contributor Licence Agreement (CLA) document is available as
a [Word](https://github.com/nordic-institute/X-Road-development/blob/master/docs/NIIS_Contributor_Licence_Agreement.docx) and
[PDF](https://github.com/nordic-institute/X-Road-development/blob/master/docs/NIIS_Contributor_Licence_Agreement.pdf) document.

#### Sign the CLA

When you contribute to X-Road open source project on GitHub with a new pull
request, it will be checked whether you have signed the CLA. If required, the
pull request will be commented on with further instructions. The CLA must be
received by NIIS prior to approval of the pull request. The CLA covers any and
all submissions that the contributor now, or in the future, submits to the
project. Therefore, it is enough to sign the CLA once before the first
contribution, and not with every contribution.

The CLA can be signed digitally using a
[qualified electronic signature](https://ec.europa.eu/cefdigital/wiki/display/CEFDIGITAL/Introduction+to+e-signature).
A digitally signed CLA must be emailed to `x-road@niis.org`. Contributors from 
Belgium, Estonia, Finland, Iceland, Latvia, Lithuania, Poland, Portugal and Spain
may use the [Dokobit](https://www.dokobit.com/) e-signing service that supports
eID providers of the listed countries.

Alternatively, the CLA can be printed out, signed manually and sent to NIIS
by post:

    MTÜ Nordic Institute for Interoperability Solutions
    Hobujaama 4
    10151 Tallinn
    Estonia

### Enhancement Requests and Error Reports

X-Road enhancement requests and error reports can be submitted to the [X-Road Service
Desk](https://nordic-institute.atlassian.net/servicedesk/customer/portal/4). In order 
to access the X-Road Service Desk, [sign up](https://id.atlassian.com/signup) for an 
account and request access to the [X-Road Backlog](https://nordic-institute.atlassian.net/jira/software/c/projects/XRDDEV/boards/6/backlog).
Alternatively, you can use your existing account, sign in and request for access. Once 
your request has been approved, you're able to access the [X-Road Service Desk](https://nordic-institute.atlassian.net/servicedesk/customer/portal/4) 
and the [X-Road Backlog](https://nordic-institute.atlassian.net/jira/software/c/projects/XRDDEV/boards/6/backlog). 
Approving the  request typically requires 1-2 business days.

#### Submitting a Bug

Before submitting a bug:

- Make sure that you are using the latest version.
- Check the [Knowledge Base](https://nordic-institute.atlassian.net/wiki/spaces/XRDKB) for a list of common questions and problems.
- Ask the community on the [X-Road community Slack](https://jointxroad.slack.com/) if the problem is a known issue or a feature. Also, check the Slack history for previous questions on the same topic.
- Perform a cursory search on the [X-Road](https://nordic-institute.atlassian.net/browse/XRDDEV),
  [X-Road Metrics](https://nordic-institute.atlassian.net/browse/OPMONDEV) or [MISP2](https://nordic-institute.atlassian.net/browse/MISPDEV) backlogs depending on the software, 
  to see if the problem has already been reported. If it has and the issue is still open, add a comment to the existing issue instead of opening a new one.

When submitting a bug, explain the problem and include additional details to help maintainers reproduce the problem:

- Summary of the problem.
- Software version.
- Host OS and version.
- More detailed description of the problem that includes:
  - Steps to reproduce the issue.
  - Expected result.
  - Actual result.
- Related log files.

#### Submitting an Enhancement Request

Before submitting an enhancement request:

- Make sure that you are using the latest version.
- Read the software specific documentation for [X-Road](https://docs.x-road.global), [X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/tree/master/docs)
  or [MISP2](https://github.com/nordic-institute/misp2/tree/develop/docs) carefully and find out if the functionality is already covered, maybe by an individual configuration.
- Perform a search on the [X-Road](https://nordic-institute.atlassian.net/browse/XRDDEV), [X-Road Metrics](https://nordic-institute.atlassian.net/browse/OPMONDEV) or
  [MISP2](https://nordic-institute.atlassian.net/browse/MISPDEV) backlogs to see if the enhancement has already been suggested. If it has, add a comment to the existing issue instead of opening a new one.
- Find out whether your idea fits with the scope and aims of the project. It's up to you to make a strong case to convince the project's developers of the merits of this feature. Keep in mind that we want features that will be useful to the majority of our users and not just a small subset. If you're just targeting a minority of users, consider writing an add-on or an extension.

The evaluation process of the enhancement requests is described [here](https://github.com/nordic-institute/X-Road-development/blob/master/DEVELOPMENT_MODEL.md#3-change-management).

### Security Issues And Vulnerabilities

Security issues and vulnerabilities are reported privately to the [X-Road Service
Desk](https://nordic-institute.atlassian.net/servicedesk/customer/portal/4) using the
`Report a software problem` request type.
[Sign up](https://id.atlassian.com/signup) for an account and
get access to the [X-Road Service Desk](https://nordic-institute.atlassian.net/servicedesk/customer/portal/4).

Another alternative to report security issues and vulnerabilities is the X-Road bug bounty program that is run on the Intigrity platform. [Visit the program details](https://app.intigriti.com/programs/niis/x-road/detail) to get started.

### Your First Code Contribution

Unsure where to begin contributing to X-Road? You can start by looking through these `good first issue` and `help wanted` issues:

- [Good first issues for X-Road](https://github.com/nordic-institute/X-Road/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
- [Help wanted issues for X-Road](https://github.com/nordic-institute/X-Road/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)
- [Good first issues for X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
- [Help wanted issues for X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)

X-Road can be developed locally. For instructions on how to do this, see the [build instructions](src/BUILD.md). Also,
completing the X-Road Academy [Core Developer training](https://academy.x-road.global/courses/x-road-core-developer) is
strongly recommended.

### Submitting a Pull Request

To ease the review work and to make clearer what changes are done, a pull request should contain one feature or bug fix. The larger the pull request is, the more complex it is to review. Pull requests are generally reviewed and accepted on first-come, first-served (FCFS) basis. Also, it's recommended to start the [CLA process](#sign-the-cla) already before opening a pull request.

If a pull request implements a new feature or a bigger change in an existing feature, it's strongly recommended to submit an enhancement request in advance and indicate in the enhancement request that the implementation will be provided too. In that way, it's possible to ensure in advance that the pull request will be approved.

Also, in case you're planning to implement an existing backlog item, let NIIS know about your plans in advance to avoid duplicate work.

In case a pull request includes changes in the architecture, it's strongly recommended to discuss the changes with NIIS in advance. Agreeing on the details of the changes upfront will likely speed up the approval process.

#### Pull Request Checklist

Before opening a pull request, it's recommended to review the following checklist and make sure that all the requirements are met.

- Are the features OK to be accepted to the project?
- Does the PR meet the style guides?
- Is the source code available?
- Are all the required dependencies available?
- No merge conflicts?
- Is there enough test coverage?
  - During the pull request review process it's checked that the test coverage is equal or higher than in the previous version.
- SonarQube checks pass?
  - During the pull request review process it's checked that SonarQube shows no bugs or code smells of severity blocker or critical.
- Does the build and the test cases work?
- Does the packaging work (Ubuntu & RHEL)?
- Can the software be installed on a clean system (Ubuntu & RHEL)?
- Can the software version be upgraded from two previous versions?
- Has the documentation been updated?
- Is the code licensing OK?
- Has the contributor delivered a signed CLA?

## Styleguides

### Branching Pattern

Branching pattern follows the [Gitflow model](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow). 
Two long-running, perpetual branches – `master` and `develop` – together with additional branches – `feature`, `beta`, `released minor version` and `bugfix` – are used.

- `master` branch is used to release X-Road software into production.
- `develop` branch is used to accumulate features for the next big release.
- `feature` branches are used to work on features (or closely related sets of features) to enhance X-Road core software.
  - `feature` branches are named using the id of the feature's backlog item, e.g., `XRDDEV-123` for JIRA tickets or
    `ISSUE123` for GitHub issues.
- `beta` branches are used to prepare new production releases.
- `released minor version` branches are used to track all patch versions released related to a specific minor version.
  - for example, `release-7.0` branch contains version `7.0.0` and all its bugfix releases (e.g., `7.0.1`, `7.0.2`, ..., `7.0.5`, etc.).
- `bugfix` branches are used to prepare patch releases.

The latest development version is always available in the `develop` branch and the latest stable version in the `master` branch.

### Pull Requests

Pull requests made against the `X-Road/develop` branch MUST follow these conventions:

- Pull request name format is `<TYPE>: <ISSUE_ID> <SHORT_DESCRIPTION>`, for example: `feat: XRDDEV-1669 Allow overriding startup parameters`
  - `TYPE` = conventional commit type. One of: build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test
  - `ISSUE_ID` = id of the feature's / bug's backlog item. If the pull request is not related to any backlog item,
    `ISSUE_ID` can be omitted. If the issue is a GitHub issue, it can be `ISSUE1669 Allow overriding startup parameters`
    instead.
  - `SHORT_DESCRIPTION` = short description of the changes included in the pull request.
- The pull request's description field must contain more detailed information about the changes. Any relevant additional information should also be provided here.

### Commit Messages

Git commit messages MUST follow [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/#summary) guidelines.

The commit message should be structured as follows:

```
<type>[optional scope]: <subject>

[optional body]

[optional footer(s)]
```

The message should be formatted as follows:

- Separate subject from body with a blank line.
- Do not end the subject line with a period.
- Capitalize the subject line and each paragraph.
- Use the imperative mood in the subject line.
- Wrap lines at 72 characters.
- Use the body to explain what and why you have done something. In most cases, you can leave out details about how a change has been implemented.

If a commit refers to an issue, the issue ID must be referenced. For example:

```
$ git commit -m "fix: Subject line
 
More detailed description.

Refs: XRDDEV-123"
```

If the commit is not related to any backlog item, the issue ID can be omitted.

### Tagging

The versions merged to `X-Road/master` branch are tagged with annotated tags. E.g.

`git tag -a 7.0.0 -m "X-Road 7.0.0"`

The versions merged to `X-Road/develop` branch are not tagged.

### Java

[Checkstyle](http://checkstyle.sourceforge.net/) MUST NOT issue any errors with the configuration defined in 
[here](https://github.com/nordic-institute/X-Road/blob/develop/src/config/checkstyle/checkstyle.xml).
