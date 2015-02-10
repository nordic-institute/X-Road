#!/bin/bash

set -e

if [ "x$SDSB_HOME" = "x" ]
then
  SDSB_HOME=`pwd`
fi

source ~/.rvm/scripts/rvm
rvm use jruby

RUBY_PROJECTS="center-service common-ui center-common proxy-ui center-ui"

for each in $RUBY_PROJECTS
do
  cd $SDSB_HOME/$each

  echo "Re-installing gems in '$each' - start"

  rm Gemfile.lock;gem clean;bundle install

  echo "Re-installing gems in '$each' - finished"
done

# For more accurate tracking when gems were updated.
GEM_UPDATE_LOG_FILE=$HOME/.sdsb_gem_updates
echo $(date) | cat >> $GEM_UPDATE_LOG_FILE
