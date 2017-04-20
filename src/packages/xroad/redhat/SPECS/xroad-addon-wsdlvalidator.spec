# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-addon-wsdlvalidator
Version:    %{xroad_version}
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road AddOn: wsdlvalidator
Group:      Applications/Internet
License:    MIT
Requires:   xroad-proxy >= %version
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd

%define src %{_topdir}/..

%description
Addon for wsdl validation

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/wsdlvalidator/bin/
mkdir -p %{buildroot}/usr/share/xroad/wsdlvalidator/doc/
mkdir -p %{buildroot}/usr/share/xroad/wsdlvalidator/etc/
mkdir -p %{buildroot}/usr/share/xroad/wsdlvalidator/jlib/
mkdir -p %{buildroot}/usr/share/xroad/wsdlvalidator/licenses/
cp -a %{src}/addon/wsdlvalidator/usr/share/xroad/wsdlvalidator/bin/* %{buildroot}/usr/share/xroad/wsdlvalidator/bin/
cp -a %{src}/addon/wsdlvalidator/usr/share/xroad/wsdlvalidator/doc/* %{buildroot}/usr/share/xroad/wsdlvalidator/doc/
cp -a %{src}/addon/wsdlvalidator/usr/share/xroad/wsdlvalidator/etc/* %{buildroot}/usr/share/xroad/wsdlvalidator/etc/
cp -a %{src}/addon/wsdlvalidator/usr/share/xroad/wsdlvalidator/jlib/* %{buildroot}/usr/share/xroad/wsdlvalidator/jlib/
cp -a %{src}/addon/wsdlvalidator/usr/share/xroad/wsdlvalidator/licenses/* %{buildroot}/usr/share/xroad/wsdlvalidator/licenses/

%clean
rm -rf %{buildroot}

%files
%defattr(755,root,root,-)
/usr/share/xroad/wsdlvalidator/bin/wsdlvalidator
/usr/share/xroad/wsdlvalidator/bin/wsdlvalidator_wrapper.sh
%defattr(-,root,root,-)
/usr/share/xroad/wsdlvalidator/doc/xroad6.wsdl
/usr/share/xroad/wsdlvalidator/etc/xroad6.properties
/usr/share/xroad/wsdlvalidator/jlib/cxf-core-3.0.1.jar
/usr/share/xroad/wsdlvalidator/jlib/cxf-manifest.jar
/usr/share/xroad/wsdlvalidator/jlib/cxf-rt-bindings-soap-3.0.1.jar
/usr/share/xroad/wsdlvalidator/jlib/cxf-rt-wsdl-3.0.1.jar
/usr/share/xroad/wsdlvalidator/jlib/cxf-tools-common-3.0.1.jar
/usr/share/xroad/wsdlvalidator/jlib/cxf-tools-validator-3.0.1.jar
/usr/share/xroad/wsdlvalidator/jlib/stax2-api-3.1.4.jar
/usr/share/xroad/wsdlvalidator/jlib/woodstox-core-asl-4.4.0.jar
/usr/share/xroad/wsdlvalidator/jlib/wsdl4j-1.6.3.jar
/usr/share/xroad/wsdlvalidator/jlib/xmlschema-core-2.1.0.jar
/usr/share/xroad/wsdlvalidator/licenses/asm.txt
/usr/share/xroad/wsdlvalidator/licenses/bsd.txt
/usr/share/xroad/wsdlvalidator/licenses/cdd1-1.0.txt
/usr/share/xroad/wsdlvalidator/licenses/epl-v10.html
/usr/share/xroad/wsdlvalidator/licenses/jaxen.txt
/usr/share/xroad/wsdlvalidator/licenses/jdom.txt
/usr/share/xroad/wsdlvalidator/licenses/MPL-1.1.txt
/usr/share/xroad/wsdlvalidator/licenses/oasis.txt
/usr/share/xroad/wsdlvalidator/licenses/ruby-mit.txt
/usr/share/xroad/wsdlvalidator/licenses/sl4j.txt
/usr/share/xroad/wsdlvalidator/licenses/w3c.html
/usr/share/xroad/wsdlvalidator/licenses/wsa.txt
/usr/share/xroad/wsdlvalidator/licenses/wsdl4j.txt
/usr/share/xroad/wsdlvalidator/licenses/wsdl.txt
/usr/share/xroad/wsdlvalidator/licenses/ws-policy.txt

%postun
%systemd_postun_with_restart xroad-proxy.service

%changelog
