# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-addon-metaservices
Version:    %{xroad_version}
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road AddOn: metaservices
Group:      Applications/Internet
License:    MIT
Requires:   xroad-proxy >= %version

%define src %{_topdir}/..

%description
AddOn for metaservice responders

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/jlib/addon/proxy/
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -a %{src}/addon/proxy/metaservice* %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{src}/../../addons/metaservice/build/libs/metaservice-1.0.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{src}/../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/
cp -p %{src}/../../securityserver-LICENSE.info %{buildroot}/usr/share/doc/%{name}/

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
/usr/share/xroad/jlib/addon/proxy/metaservice-1.0.jar
/usr/share/xroad/jlib/addon/proxy/metaservices.conf
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/securityserver-LICENSE.info

%changelog

