
echo "jetty olemas?"
test -d jetty8 && exit 0

wget -O jetty.tgz "http://eclipse.org/downloads/download.php?file=/jetty/8.1.14.v20131031/dist/jetty-distribution-8.1.14.v20131031.tar.gz&r=1"
tar zxf jetty.tgz 
rm jetty.tgz
mv jetty-distribution* jetty8
rm -rf jetty8/logs/ jetty8/contexts jetty8/contexts-available/ jetty8/webapps/ jetty8/javadoc/ jetty8/overlays/
echo -e "OPTIONS=Server,jsp,jmx,resources,websocket,ext,plus,annotations\n/etc/sdsb/jetty/jetty-admin.xml\n/etc/sdsb/jetty/jetty-public.xml\netc/jetty-annotations.xml\netc/jetty-requestlog.xml\netc/jetty-logging.xml\netc/jetty-started.xml\n" > jetty8/start.ini 

