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
cp -a %{srcdir}/common/addon/wsdlvalidator/usr %{buildroot}
mkdir -p %{buildroot}/usr/share/xroad/wsdlvalidator/jlib/
cp %{srcdir}/../../../addons/wsdlvalidator/build/libs/wsdlvalidator-1.0.jar %{buildroot}/usr/share/xroad/wsdlvalidator/jlib/

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
%attr(750,root,xroad) /usr/share/xroad/wsdlvalidator/bin/wsdlvalidator_wrapper.sh
/usr/share/xroad/wsdlvalidator

%post
#parameters:
#1 file_path
#2 old_section
#3 old_key
#4 new_section
#5 new_key
function migrate_conf_value {
    MIGRATION_VALUE="$(crudini --get "$1" "$2" "$3" 2>/dev/null || true)"
    if [ "${MIGRATION_VALUE}" ];
        then
            crudini --set "$1" "$4" "$5" "${MIGRATION_VALUE}"
            echo Configuration migration: "$2"."$3" "->" "$4"."$5"
            crudini --del "$1" "$2" "$3"
    fi
}

if [ $1 -eq 1 ] ; then
    # Initial installation
    crudini --set /etc/xroad/conf.d/local.ini proxy-ui-api wsdl-validator-command /usr/share/xroad/wsdlvalidator/bin/wsdlvalidator_wrapper.sh
fi

if [ $1 -gt 1 ] ; then
    # upgrade -> migrate
    migrate_conf_value /etc/xroad/conf.d/local.ini proxy-ui wsdl-validator-command proxy-ui-api wsdl-validator-command
fi

%systemd_post xroad-jetty.service

%postun
if [ $1 -eq 0 ] ; then
    # not an upgrade, but a real removal
    crudini --del /etc/xroad/conf.d/local.ini proxy-ui-api wsdl-validator-command
fi
%systemd_postun_with_restart xroad-proxy-ui-api.service

%changelog
