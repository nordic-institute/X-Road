%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-addon-messageroom
Version:    %{xroad_version}
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road AddOn: messageroom
Group:      Applications/Internet
License:    MIT
Requires:   xroad-proxy = %version-%release

%define src %{_topdir}/..

%description
AddOn for Message Rooms

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/jlib/addon/proxy/
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -a %{srcdir}/common/addon/proxy/messageroom* %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/../../../addons/messageroom/build/libs/messageroom-1.0.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/
cp -p %{srcdir}/../../../3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
/usr/share/xroad/jlib/addon/proxy/messageroom-1.0.jar
/usr/share/xroad/jlib/addon/proxy/messageroom.conf
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

%changelog

