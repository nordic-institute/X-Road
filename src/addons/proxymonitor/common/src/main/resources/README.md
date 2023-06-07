# monitoring wsdl
The files in this directory:
- `monitoring.wsdl`: wsdl defining the monitoring service
- `monitoring.xsd`: schema defining the monitoring datatypes
- `xroad.xsd`: local copy of the schema definition from http://x-road.eu/xsd/xroad.xsd,
with a couple of modifications:
  - added
`<xs:import namespace="http://www.w3.org/XML/1998/namespace" schemaLocation="http://www.w3.org/2001/xml.xsd" />`
to make wsdl validator accept the use of xml:lang attribute
  - added global `securityServer` element, which is used in the updated xroad protocol

To use this wsdl, copy the wsdl and schemas from this directory
(monitoring.wsdl, monitoring.xsd and xroad.xsd) along with the following
imported schema to the same directory:
- `identifiers.xsd`
  - from `xroad/src/common/common-util/src/main/resources/identifiers.xsd`
