#!/bin/bash
# Mock script which creates dummy backup files

touch2() { mkdir -p "$(dirname "$1")" && touch "$1" ; }

while getopts ":i:n:f:Sbh" opt ; do
  case $opt in
    h)
      exit 0
      ;;
    S)
      SKIP_DB_BACKUP=true
      ;;
    i)
      INSTANCE_ID=$OPTARG
      ;;
    n)
      CENTRAL_SERVER_HA_NODE_NAME=$OPTARG
      ;;
    f)
      BACKUP_FILENAME=$OPTARG
      ;;
    b)
      USE_BASE_64=true
      ;;
    \?)
      echo "Invalid option $OPTARG"
      exit 2
      ;;
    :)
      echo "Option -$OPTARG requires an argument"
      exit 2
      ;;
  esac
done

 if [[ $USE_BASE_64 = true ]] ; then
    BACKUP_FILENAME=$(echo "$BACKUP_FILENAME" | base64 --decode)
  fi

echo $BACKUP_FILENAME
touch2 $BACKUP_FILENAME

# vim: ts=2 sw=2 sts=2 et filetype=sh
