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
Content-Type: text/xml; charset=UTF-8; name=soapui-settings.xml
Content-Transfer-Encoding: quoted-printable
Content-ID: <soapui-settings.xml>
Content-Disposition: attachment; name="soapui-settings.xml"; filename="soapui-settings.xml"

<?xml version=3D"1.0" encoding=3D"UTF-8"?>
<con:soapui-settings xmlns:con=3D"http://eviware.com/soapui/config"><con:se=
tting id=3D"WsdlSettings@excluded-types">&lt;con:entry xmlns:con=3D"http://=
eviware.com/soapui/config">schema@http://www.w3.org/2001/XMLSchema&lt;/con:=
entry></con:setting><con:setting id=3D"WsdlSettings@name-with-binding">true=
</con:setting><con:setting id=3D"HttpSettings@http_version">1.1</con:settin=
g><con:setting id=3D"HttpSettings@max_total_connections">2000</con:setting>=
<con:setting id=3D"HttpSettings@response-compression">true</con:setting><co=
n:setting id=3D"HttpSettings@leave_mockengine">true</con:setting><con:setti=
ng id=3D"UISettings@auto_save_projects_on_exit">true</con:setting><con:sett=
ing id=3D"UISettings@show_descriptions">true</con:setting><con:setting id=
=3D"WsdlSettings@xml-generation-always-include-optional-elements">true</con=
:setting><con:setting id=3D"WsaSettings@useDefaultRelatesTo">true</con:sett=
ing><con:setting id=3D"WsaSettings@useDefaultRelationshipType">true</con:se=
tting><con:setting id=3D"UISettings@show_startup_page">true</con:setting><c=
on:setting id=3D"UISettings@gc_interval">60</con:setting><con:setting id=3D=
"WsdlSettings@cache-wsdls">true</con:setting><con:setting id=3D"WsdlSetting=
s@pretty-print-response-xml">true</con:setting><con:setting id=3D"HttpSetti=
ngs@include_request_in_time_taken">true</con:setting><con:setting id=3D"Htt=
pSettings@include_response_in_time_taken">true</con:setting><con:setting id=
=3D"HttpSettings@start_mock_service">true</con:setting><con:setting id=3D"U=
ISettings@auto_save_interval">0</con:setting><con:setting id=3D"WsaSettings=
@soapActionOverridesWsaAction">true</con:setting><con:setting id=3D"WsaSett=
ings@overrideExistingHeaders">true</con:setting><con:setting id=3D"WsaSetti=
ngs@enableForOptional">true</con:setting><con:setting id=3D"VersionUpdateSe=
ttings@auto-check-version-update">true</con:setting><con:setting id=3D"Prox=
ySettings@autoProxy">true</con:setting><con:setting id=3D"ProxySettings@ena=
bleProxy">true</con:setting><con:setting id=3D"WSISettings@location">/home/=
marju/soapui/SoapUI-5.2.1/wsi-test-tools</con:setting><con:setting id=3D"UI=
Settings@disable_analytics">true</con:setting><con:setting id=3D"UISettings=
@analytics_opt_out_version">5.2</con:setting><con:setting id=3D"GlobalPrope=
rtySettings@properties">&lt;xml-fragment/></con:setting><con:setting id=3D"=
RecentProjects">&lt;entry key=3D"/home/marju/synced/cyber/dev/ria_seire/ria=
-xm-source/xtee6/systemtest/op-monitoring/mock/RiaMonitoringTestServiceMock=
-project.xml" value=3D"RiaMonitoringTestServiceMock" xmlns=3D"http://eviwar=
e.com/soapui/config"/></con:setting></con:soapui-settings>
------=_Part_10_1777426800.1476273281168--

"""
}
