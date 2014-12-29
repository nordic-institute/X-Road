#!/bin/sh

if [ "x$SDSB_HOME" = "x" ]
then
  SDSB_HOME=`pwd`
fi

RUBY_PROJECTS="center-service common-ui center-common proxy-ui center-ui"

for each in $RUBY_PROJECTS
do
  cd $SDSB_HOME/$each

  echo "Re-installing gems in '$each' - start"

  rm Gemfile.lock;gem clean;bundle install

  echo "Re-installing gems in '$each' - finished"
done
