
#xtee55-center
mkdir -p packages/xtee55-center/usr/share/sdsb/jlib/
cp xtee55_clients_importer/xtee55_clients_importer-0.1.gem packages/xtee55-center/usr/share/sdsb/jlib/

#xtee55-proxy
mkdir -p packages/xtee55-proxy/usr/share/sdsb/jlib/
cp servicemediator/build/libs/servicemediator-0.1.jar  packages/xtee55-proxy/usr/share/sdsb/jlib/
cp monitoragent/build/libs/monitoragent-0.1.jar  packages/xtee55-proxy/usr/share/sdsb/jlib/
cp clientmediator/build/libs/clientmediator-0.1.jar packages/xtee55-proxy/usr/share/sdsb/jlib/
cp servicemediator/build/libs/servicemediator-0.1.jar packages/xtee55-proxy/usr/share/sdsb/jlib/
cp serviceimporter/build/libs/serviceimporter-0.1.jar packages/xtee55-proxy/usr/share/sdsb/jlib/

mkdir -p packages/xtee55-proxy/usr/share/sdsb/scripts/
cp  scripts/serviceimporter.sh  scripts/serviceexporter.sh scripts/promote_sdsb.sh scripts/deactivate_sdsb.sh scripts/activate_sdsb.sh scripts/import_internal_sslkey.sh scripts/export_internal_sslkey.sh scripts/check_sdsb.sh scripts/modify_inifile.py packages/xtee55-proxy/usr/share/sdsb/scripts/

#xtee55-common
mkdir -p packages/xtee55-common/usr/share/sdsb/lib/
cp serviceimporter/src/main/c/libxlock.so packages/xtee55-common/usr/share/sdsb/lib/
