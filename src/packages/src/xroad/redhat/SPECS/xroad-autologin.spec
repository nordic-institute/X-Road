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
Obsoletes: aux-xroad-autologin < 1.4-1

%define src %{_topdir}/../../../../autologin/source

%description
Optional utility that automatically enters the software token pin code on xroad-signer start

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/autologin
mkdir -p %{buildroot}%{_unitdir}
cp -a %{src}/common/* %{buildroot}/usr/share/xroad/autologin
cp -a %{src}/redhat/%{name}.service %{buildroot}%{_unitdir}

%clean
rm -rf %{buildroot}

%files
%defattr(750,root,xroad,751)
/usr/share/xroad/autologin
%attr(664,root,root) %{_unitdir}/%{name}.service

%pre
version_lt () {
    newest=$( ( echo "$1"; echo "$2" ) | sort -V | tail -n1)
    [ "$1" != "$newest" ]
}
if [ $1 -gt 1 ] ; then
    # upgrade
    installed_version_full=$(rpm -q xroad-autologin --queryformat="%{VERSION}-%{RELEASE}")
    incoming_version_full=$(echo "%{version}-%{release}")
    if [[ "$incoming_version_full" == 7* ]]; then
        last_supported_version=$(echo "$installed_version_full" | awk -F. '{print $1"."($2+2)}')
        incoming_version=$(echo "$incoming_version_full" | awk -F. '{print $1"."$2}')
        if version_lt $last_supported_version $incoming_version ; then
          echo "This package can be upgraded up to version $last_supported_version.x"
          exit 1
        fi
    fi
fi

%post
if [[ "$1" -eq 1 && -e /etc/aux-xroad-autologin ]]; then
    mv /etc/aux-xroad-autologin /etc/xroad/autologin
fi

%systemd_post xroad-autologin.service

%preun
%systemd_preun xroad-autologin.service

%postun
%systemd_postun_with_restart xroad-autologin.service

%changelog
