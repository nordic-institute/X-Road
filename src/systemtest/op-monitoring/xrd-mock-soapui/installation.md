# Installation guide

## Keystore

A Java keystore is required to test TLS connections between SOAPUI and Security Server.
New keystore for SOAPUI can be generated with the following command:
```
keytool -genkey -alias soapui -keyalg RSA -keystore soapui.keystore
```

Export SOAPUI cert. For example:
```
keytool -export -alias soapui -file RIA-CI-SoapUI.crt -keystore RIA-CI-SoapUI.keystore
```

After that insert that SOAPUI sertificate under every subsystem in Security Servers that will communicate with SOAPUI using TLS.

Import TLS Certificates of Security Servers to keystore. For example:
```
keytool -import -trustcacerts -alias xtee6.ci.kit -file xtee6.ci.kit.pem -keystore RIA-CI-SoapUI.keystore
```

As an example RIA CI keystore and certificates are provided under: [EE configuration folder](../../EE-national/xrd-mock-soapui/).

## SOAPUI configuration
Download and install SOAPUI from [https://www.soapui.org/downloads/latest-release.html](https://www.soapui.org/downloads/latest-release.html).

Under Windows modify file `bin\SoapUI-5.2.0.vmoptions` and add option:
```
-Dsoapui.https.protocols=TLSv1.2
```

Under Linux modify file `bin/soapui.sh` and add option `-Dsoapui.https.protocols=TLSv1.2`:
```
JAVA_OPTS="-Xms128m -Xmx512m -Dsoapui.https.protocols=TLSv1.2 ..."
```

Open SOAPUI and under Preferences -> SSL Settings set all "KeyStore" and "TrustStore" parameters to the location of `soapui.keystore`.
Set all the "Password" fields with the correct password. Enable options "enable SSL for Mock Services" and "requires client authentication".

Save the pereferences and find the `soapui-settings.xml` file with the saved configuration. You will need that file for running mock as a service.

## Running mock in linux server
Download SOAPUI from [https://www.soapui.org/downloads/latest-release.html](https://www.soapui.org/downloads/latest-release.html).
Unpack that:
```
tar -xzf SoapUI-5.3.0-linux-bin.tar.gz
```

You will need a `soapui-settings.xml` file for TLS configuration. Additionally you might need to add `-Dsoapui.https.protocols=TLSv1.2` Java option in the `bin/mockservicerunner.sh` file.
For some reason SOAPUI version 5.3.0 mockservicerunner can run without that option while SOAPUI GUI requires that:
```
JAVA_OPTS="-Xms128m -Xmx256m -Dsoapui.https.protocols=TLSv1.2 ..."
```

Start mock service with command (please use the correct paths):
```
bin/mockservicerunner.sh -s soapui-settings.xml mock-soapui-project.xml
```

By default mock is listening port "8443" for HTTPS connection and port "8086" for HTTP connection. Example service URL's:
```
http://xtee2.ci.kit:8086/xrd-mock
https://xtee2.ci.kit:8443/xrd-mock
```

## Automate mock startup in Ubuntu

In this example it is assumed that you have SOAPUI installed into `/opt/riajenk/SoapUI-5.3.0`
this repository is cloned under `/opt/riajenk/git/testing/` and mock is starting in `/opt/riajenk/xrd-soapui-mock` folder.

Create new upstart script `/etc/init/xrd-mock-soapui.conf`
```
description "X-Road SOAPUI mock"

start on runlevel [2345]
stop on runlevel [!2345]

respawn
respawn limit 10 5
setuid riajenk
setgid riajenk
console log

script
  COMMONDIR=/opt/riajenk/git/testing/common/xrd-mock-soapui
  EEDIR=/opt/riajenk/git/testing/EE-national/xrd-mock-soapui
  SOAPUIDIR=/opt/riajenk/SoapUI-5.3.0/bin
  cd /opt/riajenk/xrd-soapui-mock
  rsync -a --delete $COMMONDIR/data ./
  rsync -a $COMMONDIR/mock-soapui-project.xml ./
  rsync -a $EEDIR/soapui-settings.xml ./
  rsync -a $EEDIR/RIA-CI-SoapUI.keystore ./
  exec $SOAPUIDIR/mockservicerunner.sh -s soapui-settings.xml mock-soapui-project.xml
end script
```

## Running SOAPUI GUI with X11 forwarding

Using Windows + Putty:

Download, install and run Xming X Server for Windows: [https://sourceforge.net/projects/xming/](https://sourceforge.net/projects/xming/)

Connect to server with enabled X11 forwarding under Putty -> Configuration -> Connection -> SSH -> X11

Headless Java will not be able to run SOAPUI GUI. Therefore make sure you have the coddect version of Java. For example under Ubuntu:
```
sudo apt-get install default-jre
```

If you need to run SOAPUI as different user then copy xauth key. For example run as user you connected to with putty:
```
$ xauth list
xtee2/unix:10  MIT-MAGIC-COOKIE-1  62b8c7a3d83a50b2d21e33e2xxxxxxxx
```

And after run as user you need to start SOAPUI as:
```
$ xauth add xtee2/unix:10  MIT-MAGIC-COOKIE-1  62b8c7a3d83a50b2d21e33e2xxxxxxxx
```

Run the SOAPUI:
```
SoapUI-5.3.0/bin/soapui.sh
```
