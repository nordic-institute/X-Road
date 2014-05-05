#!/bin/bash
sed -i -e 's/^require "validation_helper"/require "sdsb\/validation_helper"/g' lib/sdsb/validators.rb
