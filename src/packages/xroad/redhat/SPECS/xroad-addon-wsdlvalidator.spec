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
Requires:   xroad-proxy = %version-%release
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd

%define src %{_topdir}/..

%description
Addon for wsdl validation

%prep

%build

%install
cp -a %{src}/addon/wsdlvalidator/usr %{buildroot}
cp %{src}/../../addons/wsdlvalidator/build/libs/wsdlvalidator-1.0.jar %{buildroot}/usr/share/xroad/wsdlvalidator/jlib/

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
/usr/share/xroad/wsdlvalidator
%attr(755,root,root) /usr/share/xroad/wsdlvalidator/bin/wsdlvalidator
%attr(750,root,xroad) /usr/share/xroad/wsdlvalidator/bin/wsdlvalidator_wrapper.sh

%post
crudini --set /etc/xroad/conf.d/local.ini proxy-ui wsdl-validator-command /usr/share/xroad/wsdlvalidator/bin/wsdlvalidator_wrapper.sh
%systemd_post xroad-jetty.service

%postun
crudini --del /etc/xroad/conf.d/local.ini proxy-ui wsdl-validator-command
%systemd_postun_with_restart xroad-jetty.service
%systemd_postun_with_restart xroad-proxy.service

%changelog
