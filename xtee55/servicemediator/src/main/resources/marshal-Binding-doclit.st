<wsdl:binding xmlns:tns="$type.namespaceURI$" name="$name$" type="tns:$type.localPart$">
<soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http" />
$ops:{op|$op.xml$};separator="\n"$
</wsdl:binding>
