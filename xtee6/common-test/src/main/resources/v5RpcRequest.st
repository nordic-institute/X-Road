<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
        SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:SOAP-ENC="http://schemas.xmlsoap.org/soap/encoding/"
        xmlns:xtee="http://x-tee.riik.ee/xsd/xtee.xsd">
    <SOAP-ENV:Header>
        <xtee:asutus xsi:type="xsd:string">$header.client.memberCode$</xtee:asutus>
        <xtee:andmekogu xsi:type="xsd:string">$header.service.memberCode$</xtee:andmekogu>
        <xtee:isikukood xsi:type="xsd:string">EE30101010007</xtee:isikukood>
        <xtee:id xsi:type="xsd:string">$header.id$</xtee:id>
        <xtee:nimi xsi:type="xsd:string">$header.service.memberCode$.$header.service.serviceCode$.$header.service.serviceVersion$</xtee:nimi>
        <xtee:toimik xsi:type="xsd:string"/>
        $if(header.async)$
        <xtee:asynkroonne>true</xtee:asynkroonne>
        $endif$
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <ns4:$header.service.serviceCode$ xmlns:ns4="http://producers.$header.service.memberCode$.xtee.riik.ee/producer/$header.service.memberCode$">
            <keha>
                $request:{each|<$each.tagName$>$each.value$</$each.tagName$>}; separator="\n"$
            </keha>
        </ns4:$header.service.serviceCode$>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
