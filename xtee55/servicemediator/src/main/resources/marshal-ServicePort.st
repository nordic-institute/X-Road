<wsdl:port xmlns:bindingns="$binding.namespaceURI$" binding="bindingns:$binding.localPart$" name="$name$">
<soap:address location="http://SECUREPROXY/cgi-bin/consumer_proxy"/>
$xrdNodes:{xrdNode|$xrdNode.xml$};separator="\n"$
</wsdl:port>
