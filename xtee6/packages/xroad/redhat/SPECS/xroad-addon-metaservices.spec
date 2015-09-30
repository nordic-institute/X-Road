# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-addon-metaservices
Version:    6.7
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road AddOn: metaservices
Group:      Applications/Internet
License:    Proprietary
Requires:   xroad-proxy >= %version

%define src %{_topdir}/..

%description
AddOn for metaservice responders

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -a %{src}/addon/proxy/metaservice* %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{src}/../../addons/metaservice/build/libs/metaservice-1.0.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
/usr/share/xroad/jlib/addon/proxy/metaservice-1.0.jar
/usr/share/xroad/jlib/addon/proxy/metaservices.conf

%changelog

