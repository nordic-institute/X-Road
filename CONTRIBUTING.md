# Contributing to X-Road

[![Go to X-Road Community Slack](https://img.shields.io/badge/Go%20to%20Community%20Slack-grey.svg)](https://jointxroad.slack.com/)
[![Get invited](https://img.shields.io/badge/No%20Slack-Get%20invited-green.svg)](https://x-road.global/community)

First off, thanks for taking the time to contribute! ❤️ 

The guidelines described in this document apply to the X-Road core and all the official
[X-Road extensions](https://x-road.global/xroad-extensions) (e.g., X-Road Metrics).

All types of contributions are encouraged and valued. See the [Table of Contents](#table-of-contents) for different ways to help and details about how this project handles them. Please make sure to read the relevant section before making your contribution. It will make it a lot easier for the maintainers and smooth out the experience for all involved. The community looks forward to your contributions. 🎉

## Table of Contents

* [Code of Conduct](#code-of-conduct)
* [I Have a Question](#i-have-a-question)
* [I Want To Contribute](#i-want-to-contribute)
  * [Quick Start](#quick-start)  
  * [Legal Notice](#legal-notice)
  * [Enhancement Requests and Error Reports](#enhancement-requests-and-error-reports)
    * [Submitting a Bug](#submitting-a-bug)
    * [Submitting an Enhancement Request](#submitting-an-enhancement-request)
  * [Security Issues And Vulnerabilities](#security-issues-and-vulnerabilities)
  * [Your First Code Contribution](#your-first-code-contribution)
    * [Verifying Your Changes Locally](#verifying-your-changes-locally)
  * [Submitting a Pull Request](#submitting-a-pull-request)
    * [Pull Request Checklist](#pull-request-checklist)
* [Development Conventions](#development-conventions)
  * [Branching Pattern](#branching-pattern)
  * [Pull Requests](#pull-requests)
  * [Commit Messages](#commit-messages)

## Code of Conduct

Everyone participating in the X-Road project is expected to follow the
[X-Road Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold it. Unacceptable
behaviour can be reported to the community leaders responsible for enforcement through the
[NIIS contact form](https://www.niis.org/contact) or by email (`info@niis.org`).

## I Have a Question

Before you ask a question, it is best to check the existing resources first:

- [X-Road documentation portal](https://docs.x-road.global);
- [Knowledge Base](https://nordic-institute.atlassian.net/wiki/spaces/XRDKB);
- [List of X-Road resources](https://x-road.global/resources).

If you still need help, ask a question in the [X-Road community Slack](https://jointxroad.slack.com/) or open a GitHub [issue](https://github.com/nordic-institute/X-Road/issues/new).

If you're not a member of the X-Road community Slack yet, you can join [here](https://x-road.global/community).

## I Want To Contribute

### Quick Start

To contribute a code change:

1. Fork the X-Road repository and clone your fork.
2. Create a branch from `develop`.
3. Follow the [build instructions](src/BUILD.md) to set up your development environment.
4. Make your changes and add or update tests.
5. [Verify your changes locally](#verifying-your-changes-locally).
6. Commit your changes following the [commit message conventions](#commit-messages).
7. Push the branch to your fork.
8. Open a pull request against `develop`.
9. Complete the CLA process if this is your first contribution.

### Legal Notice

All contributors must sign the NIIS Contributor Licence Agreement (CLA) before their first contribution can be approved. You only need to sign the CLA once.

[Read and sign the NIIS Contributor Licence Agreement (CLA) →](CLA.md)

### Enhancement Requests and Error Reports

X-Road enhancement requests and error reports can be submitted directly to this GitHub repository:

* For enhancement requests, please create a discussion containing your proposal in the specific software repository in
  the `Ideas` category:
  * [X-Road](https://github.com/nordic-institute/X-Road/discussions/new?category=ideas)
  * [X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/discussions/new?category=ideas)
* To report an error, please create an issue in the specific software repository:
  * [X-Road](https://github.com/nordic-institute/X-Road/issues/new/choose)
  * [X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/issues/new/choose)

#### Submitting a Bug

Before submitting a bug:

* Make sure that you are using an officially supported version.
* Check the [Knowledge Base](https://nordic-institute.atlassian.net/wiki/spaces/XRDKB) for a list of common questions
  and problems.
* Ask the community on the [X-Road community Slack](https://jointxroad.slack.com/) if the problem is a known issue or a
  feature. Also, check the Slack history for previous questions on the same topic.
* Perform a cursory search on the [X-Road](https://nordic-institute.atlassian.net/browse/XRDDEV) or
  [X-Road Metrics](https://nordic-institute.atlassian.net/browse/OPMONDEV) backlogs depending on the software, to see
  if the problem has already been reported. If it has and the issue is still open, add a comment to the existing issue
  instead of opening a new one.
* Perform a cursory search on the [X-Road](https://github.com/nordic-institute/X-Road/issues) or
  [X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/issues) GitHub issues depending on the software,
  to see if the problem has already been reported. If it has and the issue is still open, add a comment to the existing
  issue instead of opening a new one.

When submitting a bug, explain the problem and include additional details to help maintainers reproduce the problem:

* Summary of the problem.
* Software version.
* Host OS and version.
* More detailed description of the problem that includes:
  * Steps to reproduce the issue.
  * Expected result.
  * Actual result.
* Related log files.

#### Submitting an Enhancement Request

Before submitting an enhancement request:

* Make sure that you are using the latest version.
* Read the software specific documentation for [X-Road](https://docs.x-road.global) or
  [X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/tree/master/docs) carefully and find out if the
  functionality is already covered, maybe by an individual configuration.
* Perform a search on the [X-Road](https://nordic-institute.atlassian.net/browse/XRDDEV) or
  [X-Road Metrics](https://nordic-institute.atlassian.net/browse/OPMONDEV) backlogs to see if the enhancement has
  already been suggested. If it has, add a comment to the existing issue instead of opening a new one.
* Perform a search on the [X-Road](https://github.com/nordic-institute/X-Road/discussions/categories/ideas) or
  [X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/discussions/categories/ideas) discussion boards
  ideas category to see if the enhancement has already been suggested. If it has, add a comment to the existing
  discussion instead of opening a new one.
* Find out whether your idea fits with the scope and aims of the project. It's up to you to make a strong case to
  convince the project's developers of the merits of this feature. Keep in mind that we want features that will be
  useful to the majority of our users and not just a small subset. If you're just targeting a minority of users,
  consider writing an add-on or an extension.

The evaluation process of the enhancement requests is described
[here](https://github.com/nordic-institute/X-Road-development/blob/master/DEVELOPMENT_MODEL.md#3-change-management).

### Security Issues And Vulnerabilities

**Never report a security vulnerability in a public issue, discussion or pull request.**

Security issues and vulnerabilities are reported privately as described in the
[X-Road Security and Vulnerability Disclosure Policy](SECURITY.md). The policy is the single source of truth on
supported versions, reporting channels, the information to include in a report, coordinated disclosure and good faith
security research.

### Your First Code Contribution

Unsure where to begin contributing to X-Road? You can start by looking through these `good first issue` and
`help wanted` issues:

* [Good first issues for X-Road](https://github.com/nordic-institute/X-Road/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
* [Help wanted issues for X-Road](https://github.com/nordic-institute/X-Road/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)
* [Good first issues for X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
* [Help wanted issues for X-Road Metrics](https://github.com/nordic-institute/X-Road-Metrics/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)

X-Road can be developed locally. For instructions on how to do this, see the [build instructions](src/BUILD.md) and the
[script reference](scripts/README.md) that maps common development tasks to the scripts implementing them. Also,
completing the X-Road Academy [Core Developer training](https://academy.x-road.global/courses/x-road-core-developer) is
strongly recommended.

#### Verifying Your Changes Locally

Run the following checks locally before opening a pull request. The pull request pipeline will run these checks 
again together with additional CI validations. All the commands below are run in the `src` directory.

**Install the Git hooks.** The repository ships a `commit-msg` hook that validates commit messages against the
[commit message conventions](#commit-messages). The hook is not active until it is installed:

```
./gradlew installGitHooks
```

**Add the licence header to new files.** Every source file must carry the MIT licence header, and the build fails
without it. The header is added automatically to Java sources:

```
./gradlew licenseFormatMain licenseFormatTest   # add missing headers
./gradlew licenseMain licenseTest               # verify headers
```

For the frontend sources, the equivalent commands are `pnpm -r run license-add` and `pnpm -r run license-check`.

**Run the checks.**

For faster, targeted verification:
```
./gradlew checkstyleMain checkstyleTest   # Java code style
./gradlew test                            # unit tests
```

Before opening a pull request, run the full build:
```
./gradlew build                           # full build with tests and style checks
```

**Run the frontend checks.** The admin user interfaces form a pnpm workspace rooted in `src`:

```
pnpm install --frozen-lockfile
pnpm -r run lint          # ESLint and Prettier
pnpm -r run type-check    # TypeScript
pnpm -r run test          # unit tests
```

### Submitting a Pull Request

To make changes easier to review and understand, a pull request should normally contain a single feature or bug fix.
Smaller pull requests are generally easier and faster to review.

Pull requests are generally reviewed on first-come, first-served (FCFS) basis. Also, it's recommended 
to complete the [CLA process](CLA.md#sign-the-cla) already before opening a pull request.

After you submit a pull request:

1. Automated CI checks are run.
2. A maintainer checks the CLA.
3. Once the CLA has been signed, a maintainer reviews the changes.
4. You may be asked to make changes.
5. Push additional commits to the same branch to update the pull request.
6. Resolve merge conflicts if the target branch changes.
7. Once the required checks and reviews pass, a maintainer merges the pull request.

If a pull request implements a new feature or a bigger change in an existing feature, it's recommended to
submit an enhancement request in advance and indicate in the enhancement request that the implementation will be
provided too. This helps confirm that the proposed change aligns with the project's direction before significant 
implementation work begins.

Also, in case you're planning to implement an existing backlog item, let NIIS know about your plans in advance to avoid
duplicate work.

Discuss significant architectural changes with NIIS before starting implementation. This helps establish 
agreement on the proposed approach and avoids unnecessary rework.

#### Pull Request Checklist

Before opening a pull request, please review the following checklist and verify that all the requirements are met.

- The pull request contains a single feature or bug fix.
- The pull request title follows the pull request naming convention.
- Commit messages follow the [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/#summary) guidelines.
- The build and the tests pass locally.
- Checkstyle and lint checks pass, and new files carry the MIT licence header.
- New and changed code is covered by tests; test coverage is not lower than before.
- Documentation has been updated where needed.
- The branch has no merge conflicts with the target branch.
- If this is your first contribution, the [Contributor Licence Agreement](CLA.md) has been signed and delivered to NIIS.

## Development Conventions

### Branching Pattern

Branching pattern follows the
[Gitflow model](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow).

For contributions, create a branch from `develop` and submit the pull request back to `develop`:

```
develop
  └── ISSUE-123
        └── Pull Request → develop
```

The latest development version is available in `develop`, while `master` contains the latest stable version.

### Pull Requests

Pull requests made against the `X-Road/develop` branch MUST follow these conventions:

* Pull request name format is `<TYPE>: [<ISSUE_ID>] <SHORT_DESCRIPTION>`, for example:
  `feat: XRDDEV-1669 Allow overriding startup parameters`
  * `TYPE` = conventional commit type. One of: build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test
  * `ISSUE_ID` = id of the feature's / bug's backlog item. If the pull request is not related to any backlog item,
    `ISSUE_ID` can be omitted. If the issue is a GitHub issue, it can be `ISSUE-1669 Allow overriding startup parameters`
    instead.
  * `SHORT_DESCRIPTION` = short description of the changes included in the pull request.
* The pull request's description field must contain more detailed information about the changes. Any relevant
  additional information should also be provided here.

### Commit Messages

Git commit messages MUST follow [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/#summary)
guidelines.

The commit message should be structured as follows:

```
<type>[optional scope]: <subject>

[optional body]

[optional footer(s)]
```

The message should be formatted as follows:

* Separate subject from body with a blank line.
* Do not end the subject line with a period.
* Capitalize the subject line and each paragraph.
* Use the imperative mood in the subject line.
* Wrap lines at 72 characters.
* Use the body to explain what and why you have done something. In most cases, you can leave out details about how a
  change has been implemented.

If a commit refers to an issue, the issue ID must be referenced. For example:

```
$ git commit -m "fix: Subject line
 
More detailed description.

Refs: XRDDEV-123"
```

If the commit is not related to any backlog item, the issue ID can be omitted.
