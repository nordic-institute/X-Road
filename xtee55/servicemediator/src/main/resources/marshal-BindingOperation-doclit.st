<wsdl:operation name="$name$">
<soap:operation soapAction="" style="document"/>
$xrdNodes:{node|$node.xml$};separator="\n"$
<wsdl:input>
<soap:body parts="body" use="literal"/>
<soap:header message="tns:standardheader" part="consumer" use="literal"/>
<soap:header message="tns:standardheader" part="producer" use="literal"/>
<soap:header message="tns:standardheader" part="userId" use="literal"/>
<soap:header message="tns:standardheader" part="id" use="literal"/>
<soap:header message="tns:standardheader" part="service" use="literal"/>
</wsdl:input>
<wsdl:output>
<soap:body parts="body" use="literal"/>
<soap:header message="tns:standardheader" part="consumer" use="literal"/>
<soap:header message="tns:standardheader" part="producer" use="literal"/>
<soap:header message="tns:standardheader" part="userId" use="literal"/>
<soap:header message="tns:standardheader" part="id" use="literal"/>
<soap:header message="tns:standardheader" part="service" use="literal"/>
</wsdl:output>
</wsdl:operation>
