<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<wsdl:definitions
    name="$name$"
    xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
    xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
    targetNamespace="$tns$">
<wsdl:types>
<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" targetNamespace="$tns$">
$schemaElements:{elem|$elem.xml$};separator="\n"$
</xsd:schema>
</wsdl:types>

$messages:{msg|$msg.xml$};separator="\n"$

$portTypes:{portType|$portType.xml$};separator="\n"$

$bindings:{binding|$binding.xml$};separator="\n"$

$services:{service|$service.xml$};separator="\n"$

</wsdl:definitions>
