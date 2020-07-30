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

%verifyscript

%post

%postun
%systemd_postun_with_restart nginx.service

echo xroad-nginx.spec.postun 1
service nginx status | head -3
sleep 5

service nginx restart

echo xroad-nginx.spec.postun 2
service nginx status | head -3
sleep 5

%changelog
