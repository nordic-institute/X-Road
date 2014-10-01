<wsdl:operation name="$name$">
<wsdl:documentation>
$doc:{elem|$elem.xml$};separator="\n"$
</wsdl:documentation>
<wsdl:input xmlns:inputns="$input.namespaceURI$" message="inputns:$input.localPart$"/>
<wsdl:output xmlns:outputns="$output.namespaceURI$" message="outputns:$output.localPart$"/>
</wsdl:operation>
