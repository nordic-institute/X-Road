
![X-Road logo](xroad_logo_small.png)

## About the repository

This repository contains information about the X-Road, source codes, its development, installation and documentation.

## X-Road source code

[Source code](https://github.com/ria-ee/X-Road/tree/develop/src) of X-Road is open for all and it is licenced under MIT licence. 

## Introduction to X-Road

Short [X-Road introduction video](https://youtu.be/9PaHinkJlvA)

**What is X-Road?**

X-Road, the data exchange layer for information systems, is a technological and organizational environment enabling a secure Internet-based data exchange between information systems.

![X-Road overview](X-Road_overview.png)

X-Road Data Exchange Layer is a standardised, cohesive, collaborative, interoperable and secure data exchange layer that gives service providers a completely new kind of opportunity of making themselves visible in services directed at citizens, businesses and civil servants. Creating entities that combine many different services and data sources is easy and cost efficient.

* Improves the quality of existing services and products
* Enables new types of service innovations
* Savings in infrastructure, archiving and other costs
* Standardised data security and privacy protection
* Easy implementation, data access via interfaces – after connecting all included services are available

See [Data Exchange Layer X-Road](https://www.ria.ee/en/x-road.html) for more information about X-Road.

## Development of X-Road

**Backlog**

X-Road development backlog is in Github: [X-Road development backlog](https://github.com/vrk-kpa/xroad-joint-development/issues)

* Backlog is a list of technical 'features' that are or will be developed to X-Road core technology.

**Roadmap**

Future developments are coordinated by [X-Road Joint Development Roadmap](https://github.com/vrk-kpa/xroad-joint-development/blob/master/ROADMAP.md)

* X-Road Steering Group committee coordinates roadmap items.

**How to contribute?**

Submit a pull request to [X-Road source code Github repository](https://github.com/ria-ee/X-Road) or raise an issue to [X-Road Joint Development Github repository ](https://github.com/vrk-kpa/xroad-joint-development/issues)

* X-Road Working Group committee review all proposals and decides what enhancements will be included to future X-Road versions.

## X-Road installation

**Local installation**

X-Road central servers, configuration proxies and security servers can be automatically installed with the Ansible scripts found in ansible subdirectory. See instructions in [ansible/README.md](ansible/README.md)

**Development installation**

To setup a Virtualbox based development machine with X-Road servers running in LXD containers follow the instructions in [src/Vagrant.md](src/Vagrant.md)

**How to build X-Road?**

See instructions in [src/BUILD.md](src/BUILD.md)

## X-Road technical documentation

[Documentation table of contents](doc/README.md)

## Further information about X-Road

**More information about X-tee and Palveluväylä**

Estonia - [X-tee (in Estonian)](https://www.ria.ee/ee/x-tee.html)

Finland - [Palveluväylä (in Finnish)](https://esuomi.fi/palveluntarjoajille/palveluvayla/)

**Contact information**

Estonia - [X-tee contacts](https://www.ria.ee)

Finland - [Palveluväylä contacts](https://esuomi.fi/yhteystiedot/)

## Support disclaimer

Current repository is collaboration platform between Finnish and Estonian government for joint development of X-Road. Cooperation is organized by the [Nordic Institute for Interoperability Solutions (NIIS)](https://www.niis.org/).

Support for members (existing or potential) X-tee (support provided by [RIA](https://www.ria.ee)) and Palveluväylä (support provided by [VRK](https://www.vrk.fi)).

Consultational services for deploing independent X-Road instances should be obtained from enterprises with such services. Known organizations are:

* [Aktors OÜ](http://www.aktors.ee)
* [Cybercom Finland Oy](http://www.cybercom.fi)
* [Cybernetica AS](https://cyber.ee)
* [Gofore Oyj](https://www.gofore.com)
* [Roksnet Solutions OÜ](https://roksnet.com)
 
No support for X-Road deployment is provided here.
