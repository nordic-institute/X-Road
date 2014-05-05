#!/bin/bash

# This script creates a signed multipart file, that contains the GlobalConf,
# signing time and the signature. The GlobalConf and signing time are both 
# signed. Because of this, they are contained in a separate multipart.

## Configuration options

# The name of the resulting output file
outfile="globalconf.signed"

# The name of the signed data file
signdata="globalconf.temp"

# The final destination file name of the multipart response
finaldest="/usr/share/nginx/www/globalconf"

# The location of the global conf to sign
globalconf="../../test/resources/globalconf.xml"
#globalconf="/usr/share/nginx/www/globalconf.xml"

# The key used to sign
signkey="../../test/resources/keypair.pem"

# The algorithm digest and id
dgstalgoid="-sha256"
signalgoid="SHA-256"

###############################################################################

# A shortcut function for writing to main multipart file
function mp {
    echo $1 >> $outfile
}

# A shortcut function for writing to signed data multipart file
function sd {
    echo $1 >> $signdata
}

# Logs to system log and to console
function log {
    echo $1
    logger $1
}

###############################################################################

# Check if the GlobalConf exists...
if [ ! -r $globalconf ]; then
    log "Could not read GlobalConf file $globalconf!";
    exit 1;
fi

log "Start signing GlobalConf $globalconf..."

# Truncate the output files or die trying
> $signdata || { log "Could not create temporary file $signdata!"; exit 1; }
> $outfile || { log "Could not create file $outfile!"; exit 1; }

# The value of the main boundary
mainboundary=`openssl rand -base64 20`

# The value of the boundary used for the signed data multipart
databoundary=`openssl rand -base64 20`

# Current date, used to indicate signing time 
curdate=`date -u '+%Y-%m-%dT%H:%M:%S%z'`

# Start writing data to be signed (globalconf and date)
sd "--$databoundary"
sd "Content-Type: text/xml"
sd "Content-Date: $curdate"
sd ""
cat $globalconf >> $signdata
sd ""
sd "--$databoundary--"

# The topmost multipart headers
mp "Content-Type: multipart/related; charset=UTF-8; boundary=$mainboundary"
mp ""

# The signed data part
mp "--$mainboundary"
mp "Content-Type: multipart/mixed; charset=UTF-8; boundary=$databoundary"
mp ""
cat $signdata >> $outfile
mp ""

# The signature part
mp "--$mainboundary"
mp "Content-Type: application/octet-stream"
mp "Content-Transfer-Encoding: binary"
mp "Signature-Algorithm-Id: $signalgoid"
# TODO: ID of signing key?
mp ""

# Sign the GlobalConf and write the signature
openssl dgst $dgstalgoid -sign $signkey $signdata >> $outfile
if [ $? -ne 0 ]; then
    log "OpenSSL sign failed!";
    exit 1;
else
    log "Sucessfully signed GlobalConf!";
fi

# End the multipart
mp ""
mp "--$mainboundary--"

# Finally, move the generated multipart response to correct location
mv $outfile $finaldest
if [ $? -ne 0 ]; then
    log "Could not move signed GlobalConf to destination $finaldest";
    exit 1;
fi
