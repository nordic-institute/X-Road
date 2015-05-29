
#xtee55-center
mkdir -p packages/xtee55-center/usr/share/xroad/jlib/
cp xtee55_clients_importer/xtee55_clients_importer-0.1.gem packages/xtee55-center/usr/share/xroad/jlib/

#xtee55-proxy
mkdir -p packages/xtee55-proxy/usr/share/xroad/jlib/
cp servicemediator/build/libs/servicemediator-0.1.jar  packages/xtee55-proxy/usr/share/xroad/jlib/
cp monitoragent/build/libs/monitoragent-0.1.jar  packages/xtee55-proxy/usr/share/xroad/jlib/
cp clientmediator/build/libs/clientmediator-0.1.jar packages/xtee55-proxy/usr/share/xroad/jlib/
cp servicemediator/build/libs/servicemediator-0.1.jar packages/xtee55-proxy/usr/share/xroad/jlib/
cp serviceimporter/build/libs/serviceimporter-0.1.jar packages/xtee55-proxy/usr/share/xroad/jlib/

mkdir -p packages/xtee55-proxy/usr/share/xroad/scripts/
cp  scripts/serviceimporter.sh  scripts/serviceexporter.sh scripts/promote_v6_xroad.sh scripts/deactivate_v6_xroad.sh scripts/activate_v6_xroad.sh scripts/import_internal_sslkey.sh scripts/export_internal_sslkey.sh scripts/check_v6_xroad.sh scripts/modify_inifile.py packages/xtee55-proxy/usr/share/xroad/scripts/

#xtee55-common
mkdir -p packages/xtee55-common/usr/share/xroad/lib/
cp serviceimporter/src/main/c/libxlock.so packages/xtee55-common/usr/share/xroad/lib/
