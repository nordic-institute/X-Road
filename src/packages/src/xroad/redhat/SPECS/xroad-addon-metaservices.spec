%include %{_specdir}/common.inc
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
Requires:   xroad-proxy = %version-%release
#Requires(post): yq
Provides: deprecated()

%define src %{_topdir}/..

%description
This is a transitional package. It can safely be removed.

%prep

%build

%install

%clean
rm -rf %{buildroot}

%files

%pre -p /bin/bash
%upgrade_check

%post
%set_yaml_property_function
if [ "$1" -gt 1 ] ; then
  set_yaml_property ".xroad.proxy.addon.metaservices.enabled" "true" "/etc/xroad/conf.d/proxy-override.yaml"
fi

%changelog

