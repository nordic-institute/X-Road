<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope 
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xrd="http://x-road.ee/xsd/x-road.xsd">
    <SOAP-ENV:Header>
        <xrd:consumer>$header.client.memberCode$</xrd:consumer>
        <xrd:producer>$header.service.memberCode$</xrd:producer>
        <xrd:userId>EE30101010007</xrd:userId>
        <xrd:id>$header.id$</xrd:id>
        <xrd:service>$header.service.memberCode$.$header.service.serviceCode$$if(header.service.serviceVersion)$.$header.service.serviceVersion$$endif$</xrd:service>
        <xrd:issue/>
        $if(header.async)$
        <xrd:async>true</xrd:async>
        $endif$
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <ns4:$header.service.serviceCode$ xmlns:ns4="http://$header.service.memberCode$.ee.x-road.ee/producer/">
            <request>
                $request:{each|<$each.tagName$>$each.value$</$each.tagName$>}; separator="\n"$
            </request>
        </ns4:$header.service.serviceCode$>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>

