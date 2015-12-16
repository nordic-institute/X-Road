
#xtee55-center
mkdir -p packages/xtee55-center/usr/share/xroad/jlib/
cp xtee55_clients_importer/xtee55_clients_importer-0.1.gem packages/xtee55-center/usr/share/xroad/jlib/

#xtee55-proxy
mkdir -p packages/xtee55-proxy/usr/share/xroad/jlib/
cp monitoragent/build/libs/monitoragent-0.1.jar  packages/xtee55-proxy/usr/share/xroad/jlib/
cp clientmediator/build/libs/clientmediator-0.1.jar packages/xtee55-proxy/usr/share/xroad/jlib/
cp confimporter/build/libs/confimporter-0.1.jar packages/xtee55-proxy/usr/share/xroad/jlib/

mkdir -p packages/xtee55-proxy/usr/share/xroad/scripts/
cp scripts/activate_v6_xroad.sh \
    scripts/deactivate_v6_xroad.sh \
    scripts/export_v6_internal_tls_key.sh \
    scripts/export_v6_internal_tls_key_wrapper.sh \
    scripts/import_v5_internal_tls_key.sh \
    scripts/import_v5_internal_tls_key_wrapper.sh \
    scripts/import_v5_acl_of_producer.sh \
    scripts/import_v5_acl_of_service.sh \
    scripts/import_v5_acl.sh \
    scripts/import_v5_clients.sh \
    scripts/modify_inifile.py \
    packages/xtee55-proxy/usr/share/xroad/scripts/

#xtee55-common
mkdir -p packages/xtee55-common/usr/share/xroad/lib/
cp mediator-common/src/main/c/libxlock.so packages/xtee55-common/usr/share/xroad/lib/
