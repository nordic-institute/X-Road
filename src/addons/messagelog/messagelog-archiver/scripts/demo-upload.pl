#!/usr/bin/perl -w

use CGI;
use Fcntl;
use strict;
use constant FILE_FIELD => "file";

my $q = new CGI;
my $url = $q->url();

# Error handling.
sub err {
    # HTTP-header with error status.
    print $q->header(-status => "500 @_");
    die @_;
}

# Directory where to archive posted files.
my $FILESTORE = '/var/www/html/message_log_archive/';

# Create archive directory if not exists.
(! -d $FILESTORE) &&
    (mkdir $FILESTORE || err("Cannot create directory $FILESTORE"));

# If hostname is defined, then add directory
my $hostname;
if ($url =~ m/cgi-bin\/(.+)\/(demo-)?upload.pl/) {
  $hostname = $1;
  $FILESTORE .= $hostname .'/';
}

# Create archive directory if not exists.
(! -d $FILESTORE) &&
    (mkdir $FILESTORE || err("Cannot create directory $FILESTORE"));

# Get file from request.
my $filename = $q->param(FILE_FIELD) || err("Invalid query: missing file name");
my $filehandle = $q->upload(FILE_FIELD);

# Write file from request.
my $buffer;
my $bytesread;

sysopen (OUT, "$FILESTORE/$filename", O_WRONLY | O_EXCL | O_CREAT) ||
        err("Cannot open file '$FILESTORE/$filename': $!");
while ((defined($bytesread = read($filehandle, $buffer, 0xffff)) ||
        err("Cannot read posted file: $!")) && $bytesread) {
    print OUT $buffer;
}
close OUT;

unlink($filehandle);
close($filehandle);

# HTTP header with OK status.
print $q->header();

exit 0;