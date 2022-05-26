#!/usr/bin/env bash

scriptdir=$(dirname "$BASH_SOURCE")

# Use new associative array env variables declaration
if [[ ${XRD_CS_DB@a} = A ]]; then
  : # NOOP

# Use new env variables
elif (
  isDocker=$([[ -f /.dockerenv ]] && echo 1 || echo 0) # Is docker
  hasAnyXrdCsDbEnvs=$([[ $(compgen -v XRD_CS_DB | wc -l) -gt 0 ]] && echo 1 || echo 0) # Probably uses a new env variables
  [[ $isDocker ]] || [[ $hasAnyXrdCsDbEnvs ]]
); then
  source $scriptdir/.env

# Inherit values from legacy env variables
else
  source $scriptdir/.env_legacy
fi

# Verify that all required keys are defined
allRequiredKeys=(
  MASTER_USER
  DATABASE_NAME
  SCHEMA_NAME
  ADMIN_USER
  ADMIN_PASSWORD
#  USER - optional
#  USER_PASSWORD - optional
)
isValid=true
for key in "${allRequiredKeys[@]}"; do
  if [ ! -v "XRD_CS_DB[$key]" ]; then
    isValid=false
    echo "ERROR: missing key \"$key\" from env associative array \"XRD_CS_DB\""
  fi
done
[[ $isValid == true ]] || exit 2

# Perform SQL substitution
sqlReadyForSubstitution=$(cat $scriptdir/init_database.sql | sed 's/\$\$/\\\$\\\$/g' | sed 's/"/\\"/g')
substitutedSql=$(eval "echo \"$sqlReadyForSubstitution\"")
sql=$(echo "$substitutedSql" | sed 's/\\\$\\\$/\$\$/g' | sed 's/\\"/"/g')

# Return
echo "$sql"
