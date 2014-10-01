<wsdl:binding xmlns:tns="$type.namespaceURI$" name="$name$" type="tns:$type.localPart$">
<soap:binding style="rpc" transport="http://schemas.xmlsoap.org/soap/http"></soap:binding>
$ops:{op|$op.xml$};separator="\n"$
</wsdl:binding>
