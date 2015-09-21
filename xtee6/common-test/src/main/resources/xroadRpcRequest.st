<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
        xmlns:xroad="$xroadNamespace$"
        xmlns:id="http://x-road.eu/xsd/identifiers">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>$header.client.xRoadInstance$</id:xRoadInstance>
            <id:memberClass>$header.client.memberClass$</id:memberClass>
            <id:memberCode>$header.client.memberCode$</id:memberCode>
            $if(header.client.subsystemCode)$
            <id:subsystemCode>$header.client.subsystemCode$</id:subsystemCode>
            $endif$
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>$header.service.xRoadInstance$</id:xRoadInstance>
            <id:memberClass>$header.service.memberClass$</id:memberClass>
            <id:memberCode>$header.service.memberCode$</id:memberCode>
            $if(header.service.subsystemCode)$
            <id:subsystemCode>$header.service.subsystemCode$</id:subsystemCode>
            $endif$
            <id:serviceCode>$header.service.serviceCode$</id:serviceCode>
            $if(header.service.serviceVersion)$
            <id:serviceVersion>$header.service.serviceVersion$</id:serviceVersion>
            $endif$
        </xroad:service>
        <xroad:userId>EE37702211234</xroad:userId>
        <xroad:id>$header.id$</xroad:id>
        $if(header.async)$
        <xroad:async>true</xroad:async>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
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
