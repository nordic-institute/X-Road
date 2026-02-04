#!/bin/bash
set -euo pipefail

LOCK=/var/lock/xroad-archive.lock

DEFAULT_KEY=/etc/xroad/ssl/internal.key
DEFAULT_CERT=/etc/xroad/ssl/internal.crt
DEFAULT_ARCHIVE_DIR=/var/lib/xroad

URL=
HTTPS_OPTIONS=()
KEY=$DEFAULT_KEY
CERT=$DEFAULT_CERT
CACERT=
ARCHIVE_DIR=$DEFAULT_ARCHIVE_DIR
REMOVE_TRANSPORTED_FILES=
AUTH=true

die () {
  echo >&2 "ERROR: $*"
  exit 1
}

usage () {
  echo >&2 "$@"
  echo
  echo "Usage: $0 [options...] <URL>"
  echo "Options:"
  echo " -d, --dir DIR    Archive directory. Defaults to '$DEFAULT_ARCHIVE_DIR'"
  echo " -r, --remove     Remove successfully transported files form the"
  echo "                  archive directory."
  echo " -k, --key KEY    Client authentication private key file name in PEM format (TLS)."
  echo "                  Defaults to '$DEFAULT_KEY'"
  echo " -c, --cert CERT  Client authentication certificate file in PEM format (TLS)."
  echo "                  Defaults to '$DEFAULT_CERT'"
  echo " --cacert FILE    CA certificate file to verify the peer (TLS)."
  echo "                  The file may contain multiple CA certificates."
  echo "                  The certificate(s) must be in PEM format."
  echo " --noauth         Skip TLS client authentication (key and cert not required)"
  echo " -h, --help       This help text."

  exit 2
}

# Main
while [[ $# -gt 0 ]]
do
  case $1 in
    -k|--key)
      KEY="$2"
      AUTH=true
      shift
      ;;
    --noauth)
      AUTH=false
      ;;
    -c|--cert)
      CERT="$2"
      shift
      ;;
    --cacert)
      CACERT="$2"
      shift
      ;;
    -d|--dir)
      ARCHIVE_DIR="$2"
      shift
      ;;
    -r|--remove)
      REMOVE_TRANSPORTED_FILES=true
      ;;
    -h|--help)
      usage
      ;;
    *)
      if [[ $# = 1 ]]; then
        URL="$1"
      else
        # Unknown option
        usage "Unknown option '$1'"
      fi
      ;;
  esac
  shift
done

if [ -z "$URL" ]; then
  usage "ERROR: Required URL option is missing"
fi

shopt -s nocasematch

if [[ "$URL" == https://* ]]; then
  if [[ $AUTH == true ]]; then
    if [[ ! -f "$KEY" ]]; then
      die "Client TLS key file '$KEY' not found"
    fi

    if [[ ! -f "$CERT" ]]; then
      die "Client TLS certificate file '$CERT' not found"
    fi

    HTTPS_OPTIONS=(--key "$KEY" --cert "$CERT")
  fi

  if [[ -n $CACERT ]]; then
    if [[ ! -f "$CACERT" ]]; then
      die "Certificate file '$CACERT' to verify the peer not found"
    fi

    HTTPS_OPTIONS+=(--cacert "$CACERT")
  else
    HTTPS_OPTIONS+=(-k)
  fi
fi

shopt -u nocasematch

if [ ! -d "$ARCHIVE_DIR" ]; then
  die "Archive directory '$ARCHIVE_DIR' not found"
fi

exec 123>$LOCK || die "Cannot acquire lock"
flock -n 123 || die "There is archive transporter process already running"
trap 'rm -f "$LOCK"' EXIT

shopt -s nullglob
for i in "$ARCHIVE_DIR"/*.{zip,zip.gpg}; do
  if ! http_code=$(curl -s -S -o /dev/null -w "%{http_code}" -F file=@"$i" "${HTTPS_OPTIONS[@]}" "$URL"); then
    # curl already wrote error message to stderr.
    exit 3
  fi

  if [ "$http_code" -ne 200 ]; then
    die "HTTP server sent status code $http_code"
  fi

  if [[ $REMOVE_TRANSPORTED_FILES ]]; then
    rm -f "$i"
  fi
done

exit 0
