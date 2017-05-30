// The MIT License
// Copyright (c) 2016 Estonian Information System Authority (RIA), Population Register Centre (VRK)
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package opmonitor.loadtesting;

object SimulationRequests {

final val SimpleXRoadRequest = """
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xroad="http://x-road.eu/xsd/xroad.xsd" xmlns:id="http://x-road.eu/xsd/identifiers" xmlns:swi="http://ws-i.org/profiles/basic/1.1/xsd" xmlns:repr="http://x-road.eu/xsd/representation.xsd">
    <SOAP-ENV:Header>kalamaja
        <xroad:client id:objectType="SUBSYSTEM">
            <id:xRoadInstance>XTEE-CI-XM</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>00000001</id:memberCode>
            <id:subsystemCode>System1</id:subsystemCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>XTEE-CI-XM</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>00000000</id:memberCode>
            <id:subsystemCode>Center</id:subsystemCode>
            <id:serviceCode>xroadGetRandom</id:serviceCode>
            <id:serviceVersion>v1</id:serviceVersion>
        </xroad:service>
        <repr:representedParty>
            <repr:partyClass>COM</repr:partyClass>
            <repr:partyCode>UNKNOWN_MEMBER</repr:partyCode>
        </repr:representedParty>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:id>${message_id_placeholder}</xroad:id>
        <xroad:userId>EE37702211230</xroad:userId>
        <xroad:issue>23423</xroad:issue>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:xroadGetRandom>
            <xroad:request>
                <xroad:seed>100</xroad:seed>
            </xroad:request>
        </xroad:xroadGetRandom>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
"""
final val XRoadRequestWith1MbBody = """
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xroad="http://x-road.eu/xsd/xroad.xsd" xmlns:id="http://x-road.eu/xsd/identifiers" xmlns:swi="http://ws-i.org/profiles/basic/1.1/xsd" xmlns:repr="http://x-road.eu/xsd/representation.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="SUBSYSTEM">
            <id:xRoadInstance>XTEE-CI-XM</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>00000001</id:memberCode>
            <id:subsystemCode>System1</id:subsystemCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>XTEE-CI-XM</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>00000000</id:memberCode>
            <id:subsystemCode>Center</id:subsystemCode>
            <id:serviceCode>xroadGetRandom</id:serviceCode>
            <id:serviceVersion>v1</id:serviceVersion>
        </xroad:service>
        <repr:representedParty>
            <repr:partyClass>COM</repr:partyClass>
            <repr:partyCode>UNKNOWN_MEMBER</repr:partyCode>
        </repr:representedParty>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:id>${message_id_placeholder}</xroad:id>
        <xroad:userId>EE37702211239</xroad:userId>
        <xroad:issue>345345</xroad:issue>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:xroadGetRandom>
            <xroad:request>
                <xroad:seed>${random_1_mb_contents}</xroad:seed>
            </xroad:request>
        </xroad:xroadGetRandom>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
"""

final val XRoadRequestWithMTOMAttachment = """
------=_Part_10_1777426800.1476273281168
Content-Type: application/xop+xml; charset=UTF-8; type="text/xml"
Content-Transfer-Encoding: 8bit
Content-ID: <rootpart@soapui.org>

<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xroad="http://x-road.eu/xsd/xroad.xsd" xmlns:id="http://x-road.eu/xsd/identifiers" xmlns:swi="http://ws-i.org/profiles/basic/1.1/xsd" xmlns:repr="http://x-road.eu/xsd/representation.xsd">
    <soapenv:Header>
        <xroad:client id:objectType="SUBSYSTEM">
            <id:xRoadInstance>XTEE-CI-XM</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>00000001</id:memberCode>
            <id:subsystemCode>System1</id:subsystemCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>XTEE-CI-XM</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>00000000</id:memberCode>
            <id:subsystemCode>Center</id:subsystemCode>
            <id:serviceCode>bodyMassIndex</id:serviceCode>
            <id:serviceVersion>v1</id:serviceVersion>
        </xroad:service>
        <repr:representedParty>
            <repr:partyClass>COM</repr:partyClass>
            <repr:partyCode>UNKNOWN_MEMBER</repr:partyCode>
        </repr:representedParty>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:id>6VYUw5JftV11DkzXiUJZgYKculKET1as</xroad:id>
        <xroad:userId>EE37702211asd</xroad:userId>
        <xroad:issue>456456</xroad:issue>
    </soapenv:Header>
    <soapenv:Body>
        <xroad:bodyMassIndex>
            <xroad:request>
                <xroad:height>170</xroad:height>
                <xroad:weight>62</xroad:weight>
            </xroad:request>
        </xroad:bodyMassIndex>
    </soapenv:Body>
</soapenv:Envelope>
------=_Part_10_1777426800.1476273281168
Content-Type: text/xml; charset=UTF-8; name=random_contents
Content-Transfer-Encoding: quoted-printable
Content-ID: <random_contents>
Content-Disposition: attachment; name="random_contents"; filename="random_contents"

${random_1_mb_contents}
------=_Part_10_1777426800.1476273281168--
"""
}
