#!/bin/bash
set -e

JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
PATH=$JAVA_HOME/bin:$PATH

export PATH JAVA_HOME

source $HOME/.rvm/scripts/rvm
rvm use jruby-1.7.25
