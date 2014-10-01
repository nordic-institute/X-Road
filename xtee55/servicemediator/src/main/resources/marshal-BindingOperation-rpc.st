<wsdl:operation name="$name$">
$xrdNodes:{node|$node.xml$};separator="\n"$
<wsdl:input>
<soap:body encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded"></soap:body>
<soap:header message="tns:standardpais" part="asutus"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="andmekogu"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="isikukood"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="id"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="nimi"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="toimik"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
</wsdl:input>
<wsdl:output>
<soap:body encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded"></soap:body>
<soap:header message="tns:standardpais" part="asutus"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="andmekogu"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="isikukood"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="id"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="nimi"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
<soap:header message="tns:standardpais" part="toimik"
encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
namespace="$tns$"
use="encoded">
</soap:header>
</wsdl:output>
</wsdl:operation>
