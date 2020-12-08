#!/bin/bash

set -e

if [ -z "$XROAD_HOME" ]
then
    XROAD_HOME=$(cd "$(dirname "$0")"; pwd)
fi

JRUBY_VERSION=$(cat $XROAD_HOME/.jruby-version)
source ~/.rvm/scripts/rvm
rvm use jruby-$JRUBY_VERSION

RUBY_PROJECTS="common-ui center-common center-service center-ui"

for each in $RUBY_PROJECTS
do
  cd $XROAD_HOME/$each

  echo "Re-installing gems in '$each' - start"

  if [[ "$1" == "--update" ]]
  then
      gem clean
      bundle update
  else
      bundle install
  fi

  echo "Re-installing gems in '$each' - finished"
done

# For more accurate tracking when gems were updated.
GEM_UPDATE_LOG_FILE=$HOME/.xroad_gem_updates
echo $(date) | cat >> $GEM_UPDATE_LOG_FILE
