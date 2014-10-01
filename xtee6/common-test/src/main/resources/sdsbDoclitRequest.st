<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope 
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:sdsb="$sdsbNamespace$"
        xmlns:id="http://x-road.eu/xsd/identifiers">
    <SOAP-ENV:Header>
        <sdsb:client id:objectType="MEMBER">
            <id:sdsbInstance>$header.client.sdsbInstance$</id:sdsbInstance>
            <id:memberClass>$header.client.memberClass$</id:memberClass>
            <id:memberCode>$header.client.memberCode$</id:memberCode>
            $if(header.client.subsystemCode)$
            <id:subsystemCode>$header.client.subsystemCode$</id:subsystemCode>
            $endif$
        </sdsb:client>
        <sdsb:service id:objectType="SERVICE">
            <id:sdsbInstance>$header.service.sdsbInstance$</id:sdsbInstance>
            <id:memberClass>$header.service.memberClass$</id:memberClass>
            <id:memberCode>$header.service.memberCode$</id:memberCode>
            $if(header.service.subsystemCode)$
            <id:subsystemCode>$header.service.subsystemCode$</id:subsystemCode>
            $endif$
            <id:serviceCode>$header.service.serviceCode$</id:serviceCode>
            $if(header.service.serviceVersion)$
            <id:serviceVersion>$header.service.serviceVersion$</id:serviceVersion>
            $endif$
        </sdsb:service>
        <sdsb:userId>EE37702211234</sdsb:userId>
        <sdsb:id>$header.id$</sdsb:id>
        $if(header.async)$
        <sdsb:async>true</sdsb:async>
        $endif$
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <ns1:$header.service.serviceCode$ xmlns:ns1="http://consumer.ee.x-rd.net">
            <request>
                $request:{each|<$each.tagName$>$each.value$</$each.tagName$>}; separator="\n"$
            </request>
        </ns1:$header.service.serviceCode$>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
