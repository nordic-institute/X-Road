%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-message-log-archiver
BuildArch:          noarch
Version:            %{xroad_version}
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road message log archiver
Group:              Applications/Internet
License:            MIT
Requires:           xroad-base = %version-%release

%define src %{_topdir}/..

%description
X-Road message log archiver and cleanup CLI tool

%prep
rm -rf message-log-archiver
cp -a %{srcdir}/common/message-log-archiver .

%build

%install
cd message-log-archiver
cp -a * %{buildroot}

mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/jlib/message-log-archiver
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -p -r %{srcdir}/../../../../src/service/message-log-archiver/message-log-archiver-cli/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/message-log-archiver
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

ln -s /usr/share/xroad/jlib/message-log-archiver/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/message-log-archiver.jar

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/message-log-archiver.conf

%defattr(-,root,root,-)
%attr(550,root,xroad) /usr/share/xroad/bin/xroad-message-log-archiver

/usr/share/xroad/jlib/message-log-archiver.jar
/usr/share/xroad/jlib/message-log-archiver/
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

%post -p /bin/bash

%changelog
