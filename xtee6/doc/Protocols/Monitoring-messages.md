## Fetching security server metrics

### Request

Fetching security server metrics uses the X-Road protocol. The `getSecurityServerMetrics` request requires a `securityServer` header element so that the request can be routed to a specific security server. 

```xml
<SOAP-ENV:Envelope 
	xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" 
	xmlns:id="http://x-road.eu/xsd/identifiers" 
	xmlns:xrd="http://x-road.eu/xsd/xroad.xsd"
	xmlns:m="http://x-road.eu/xsd/monitoring">
	
    <SOAP-ENV:Header>
    
        <xrd:client id:objectType="MEMBER">
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
        </xrd:client>
        <xrd:service id:objectType="SERVICE">
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
            <id:serviceCode>getSecurityServerMetrics</id:serviceCode>
        </xrd:service>
        <xrd:securityServer>
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
            <id:serverCode>fdev-ss1.i.palveluvayla.com</id:serverCode>
        </xrd:securityServer>
        
        <xrd:id>ID11234</xrd:id>
        <xrd:protocolVersion>4.0</xrd:protocolVersion>
        
    </SOAP-ENV:Header>
    
    <SOAP-ENV:Body>
        <m:getSecurityServerMetrics/>
    </SOAP-ENV:Body>
    
</SOAP-ENV:Envelope
```

### Response


```xml
<SOAP-ENV:Envelope 
    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:id="http://x-road.eu/xsd/identifiers"
    xmlns:m="http://x-road.eu/xsd/monitoring"
    xmlns:xrd="http://x-road.eu/xsd/xroad.xsd">
    
   <SOAP-ENV:Header>
      <xrd:client id:objectType="MEMBER">
         <id:xRoadInstance>fdev</id:xRoadInstance>
         <id:memberClass>GOV</id:memberClass>
         <id:memberCode>1710128-9</id:memberCode>
      </xrd:client>
      <xrd:service id:objectType="SERVICE">
         <id:xRoadInstance>fdev</id:xRoadInstance>
         <id:memberClass>GOV</id:memberClass>
         <id:memberCode>1710128-9</id:memberCode>
         <id:serviceCode>getSecurityServerMetrics</id:serviceCode>
      </xrd:service>
      <xrd:securityServer>
         <id:xRoadInstance>fdev</id:xRoadInstance>
         <id:memberClass>GOV</id:memberClass>
         <id:memberCode>1710128-9</id:memberCode>
         <id:serverCode>fdev-ss1.i.palveluvayla.com</id:serverCode>
      </xrd:securityServer>
      <xrd:id>ID11234</xrd:id>
      <xrd:protocolVersion>4.0</xrd:protocolVersion>
      <xrd:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">mChpBRMvFlBBSNKeOxAJQBw4r6XdHZFuH8BOzhjsxjjOdaqXXyPXwnDEdq/NkYfEqbLUTi1h/OHEnX9F5YQ5kQ==</xrd:requestHash>
   </SOAP-ENV:Header>
   <SOAP-ENV:Body>
      <m:getSecurityServerMetricsResponse>
         <m:metricSet>
            <m:name>SERVER:fdev/GOV/1710128-9/fdev-ss1.i.palveluvayla.com</m:name>
            <m:stringMetric>
               <m:name>proxyVersion</m:name>
               <m:value>6.7.7-1.20151201075839gitb72b28e</m:value>
            </m:stringMetric>
            <m:metricSet>
               <m:name>systemMetrics</m:name>
               <m:stringMetric>
                  <m:name>OperatingSystem</m:name>
                  <m:value>Linux version 3.13.0-70-generic</m:value>
               </m:stringMetric>
               <m:numericMetric>
                  <m:name>TotalPhysicalMemory</m:name>
                  <m:value>2097684480</m:value>
               </m:numericMetric>
               <m:numericMetric>
                  <m:name>TotalSwapSpace</m:name>
                  <m:value>2097684480</m:value>
               </m:numericMetric>
            </m:metricSet>
            ...          
         </m:metricSet>
      </m:getSecurityServerMetricsResponse>
   </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### Response Schema

```xml
<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://www.w3.org/2001/XMLSchema"
        xmlns:tns="http://x-road.eu/xsd/monitoring" xmlns:xs="http://www.w3.org/2001/XMLSchema"
        targetNamespace="http://x-road.eu/xsd/monitoring"
        elementFormDefault="qualified">
    <xs:complexType name="MetricType" abstract="true">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="NumericMetricType">
        <xs:complexContent>
            <xs:extension base="tns:MetricType">
                <xs:sequence>
                    <xs:element name="value" type="xs:decimal"/>
                </xs:sequence>
            </xs:extension>
        </xs:complexContent>
    </xs:complexType>
    <xs:complexType name="StringMetricType">
        <xs:complexContent>
            <xs:extension base="tns:MetricType">
                <xs:sequence>
                    <xs:element name="value" type="xs:string"/>
                </xs:sequence>
            </xs:extension>
        </xs:complexContent>
    </xs:complexType>
    <xs:complexType name="HistogramMetricType">
        <xs:complexContent>
            <xs:extension base="tns:MetricType">
                <xs:sequence>
                    <xs:element name="updated" type="xs:dateTime"/>
                    <xs:element name="min" type="xs:decimal"/>
                    <xs:element name="max" type="xs:decimal"/>
                    <xs:element name="mean" type="xs:decimal"/>
                    <xs:element name="median" type="xs:decimal"/>
                    <xs:element name="stddev" type="xs:decimal"/>
                </xs:sequence>
            </xs:extension>
        </xs:complexContent>
    </xs:complexType>
    <xs:complexType name="MetricSetType">
        <xs:complexContent>
            <xs:extension base="tns:MetricType">
                <xs:sequence>
                    <xs:choice maxOccurs="unbounded">
                        <xs:element name="metricSet" type="tns:MetricSetType"/>
                        <xs:element name="numericMetric" type="tns:NumericMetricType"/>
                        <xs:element name="stringMetric" type="tns:StringMetricType"/>
                        <xs:element name="histogramMetric" type="tns:HistogramMetricType"/>
                    </xs:choice>
                </xs:sequence>
            </xs:extension>
        </xs:complexContent>
    </xs:complexType>
    <xs:element name="getSecurityServerMetricsResponse">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="metricSet" type="tns:MetricSetType"/>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
    <xs:complexType name="getSecurityServerMetricsType">
        <xs:sequence/>
    </xs:complexType>
    <xs:element name="getSecurityServerMetrics" type="tns:getSecurityServerMetricsType"/>
</schema>
```





