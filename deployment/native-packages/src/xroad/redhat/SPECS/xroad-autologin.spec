%include %{_specdir}/common.inc
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-autologin
Version:    %{xroad_version}
Release:    %{rel}%{?snapshot}%{?dist}
BuildArch:  noarch
Summary:    X-Road autologin utility
Group:      Applications/Internet
License:    MIT
BuildRequires:      systemd
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
Requires:   xroad-proxy = %version-%release, expect, systemd

%description
Optional utility that automatically enters the software token pin code on xroad-signer start

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/autologin
mkdir -p %{buildroot}%{_unitdir}
cp -a %{srcdir}/common/autologin/* %{buildroot}/usr/share/xroad/autologin
cp -a %{_sourcedir}/autologin/%{name}.service %{buildroot}%{_unitdir}

%clean
rm -rf %{buildroot}

%files
%defattr(750,root,xroad,751)
/usr/share/xroad/autologin
%attr(664,root,root) %{_unitdir}/%{name}.service

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

%post

%systemd_post xroad-autologin.service

%preun
%systemd_preun xroad-autologin.service

%postun
%systemd_postun_with_restart xroad-autologin.service

%changelog
