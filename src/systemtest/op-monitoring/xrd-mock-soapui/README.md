# xrd-mock-soapui

This folder contains X-Road SoapUI mock service and SoapUI testing client.

## Mock service installation

SoapUI mock service installation is covered in separate document: [installation.md](installation.md).

## Maven

[mock-client-pom.xml](mock-client-pom.xml) file can be used to execute SoapUI tests with Maven.

**NB!** you need to have `soapui-settings.xml` and keystore files in the same directory as `mock-client-pom.xml`.
It is possible to use the same soapui settings and keystore files for both mock and testing client.
Refer to [installation.md](installation.md) for additional information on these files.

Execute test with `mvn clean test -f mock-client-pom.xml` optionally adding maven parameters `-D<param_name>=<param-value>` that override SoapUI test configuration.

Example:

`mvn clean test -f mock-client-pom.xml -DServerUrlHttp=http://xtee10.ci.kit/ -DServerUrlHttps=https://xtee10.ci.kit/ -DClientXRoadInstance=XTEE-CI-XM -DServiceXRoadInstance=XTEE-CI-XM`

The full list of parameters is:
- ServerUrlHttp - URL of security server or tested service. For example: `http://xtee6.ci.kit/`, `http://localhost:8086/xrd-mock`.
- ServerUrlHttps - protocol used. For example: `https://xtee6.ci.kit/`, `https://localhost:8443/xrd-mock`.
- ClientXRoadInstance - X-Road instance of client. For example: `XTEE-CI`
- ClientMemberClass - member class of client. For example: `COM`
- ClientMemberCode - member code of client. For example: `00000002`
- ClientSubsystemCode - subsystem code of client. For example: `MockSystem`
- ClientSubsystemCodeTLS - subsystem code of client that uses TLS. For example: `MockSystemTLS`
- ServiceXRoadInstance - X-Road instance of service. For example: `XTEE-CI`
- ServiceMemberClass - member class of service. For example: `COM`
- ServiceMemberCode - member code of service. For example: `00000002`
- ServiceSubsystemCode - subsystem code of service. For example: `MockSystem`
- ServiceSubsystemCodeTLS - subsystem code of service that uses TLS. For example: `MockSystemTLS`

## Jenkins integration

- Create new Maven project
- Checkout project from git or manually add common/xrd-mock-soapui folder to Jenkins workspace
- Under "Build" -> "Root POM" add `common/xrd-mock-soapui/mock-client-pom.xml`
- Under "Build" -> "Goals and options" add `clean test -DServerUrlHttp=http://xtee10.ci.kit/ -DServerUrlHttps=https://xtee10.ci.kit/
  -DClientXRoadInstance=XTEE-CI-XM -DServiceXRoadInstance=XTEE-CI-XM` (note that you might need to add more parameters)
