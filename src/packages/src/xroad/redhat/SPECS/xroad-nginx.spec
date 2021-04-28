# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-nginx
Version:    %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road nginx component
Group:      Applications/Internet
License:    MIT
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
BuildRequires: systemd
Requires:  systemd
Requires:  nginx >= 1.5.10
Requires: xroad-base = %version-%release

%define src %{_topdir}/..

%description
X-Road nginx component for other X-Road packages

%prep
rm -rf nginx
cp -a %{srcdir}/common/nginx .
cd nginx
rm -rf etc/rcS.d

%build

%install
cd nginx
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/ssl
mkdir -p %{buildroot}/etc/xroad/nginx
mkdir -p %{buildroot}/etc/nginx/conf.d
mkdir -p %{buildroot}/usr/share/xroad/lib

ln -s /etc/xroad/nginx/nginx-secure-addons.conf %{buildroot}/etc/nginx/conf.d/xroad-securing.conf

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%dir /etc/xroad
%config /etc/xroad/nginx/nginx-secure-addons.conf
%config /etc/xroad/ssl/rfc3526group15.pem

%defattr(-,root,root,-)
/etc/nginx/conf.d/xroad-securing.conf

%pre
version_lt () {
    newest=$( ( echo "$1"; echo "$2" ) | sort -V | tail -n1)
    [ "$1" != "$newest" ]
}
if [ $1 -gt 1 ] ; then
    # upgrade
    installed_version_full=$(rpm -q xroad-nginx --queryformat="%{VERSION}-%{RELEASE}")
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

%verifyscript

%post

%changelog
