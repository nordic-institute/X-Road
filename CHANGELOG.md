# Change Log

## 6.7.0 - 2015-09-29
- Partial support for RHEL 7
  - Security server can be installed on RHEL 7
  - rpm packages for security server 
    - xroad-securityserver-fi (meta-package for Finnish instances), xroad-securityserver, xroad-proxy, xroad-common, xroad-jetty9, xroad-addon-messagelog, xroad-addon-metaservices
    - Note. optional package xroad-addon-hwtokens is not included in this release
- Member Code/Identifier Extractor for Finnish instance (PVAYLADEV-94)
  - Member Code/Identifier Extractor Method: ee.ria.xroad.common.util.FISubjectClientIdDecoder.getSubjectClientId
  - Signing certificate subject DN format supported by the decoder: C=FI,O=<instanceIdentifier>, OU=<memberClass>, CN=<memberCode> (e.g. C=FI, O=FI-DEV, OU=PUB, CN=1234567-8)
- Configurable key size for signing and authentication RSA keys (PVAYLADEV-28)
  - New configuration parameter signer.key-length (default 2048)
- Configurable certificate signing request signing algorithm (PVAYLADEV-29)
  - New configuration parameter signer.csr-signature-algorithm (default: SHA1withRSA)
- New security server metapackage with default configuration for Finnish instance
  - xroad-securityserver-fi
  - uses SHA256withRSA as signer.csr-signature-algorithm
- Fixed atomic save to work between separate file systems (PVAYLADEV-125)
  - OS temp directory and X-Road software can now reside on different file systems
