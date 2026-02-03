#!/bin/bash

#
# The MIT License
#
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

setup_directories() {
  # ensure home directory ownership
  mkdir -p /var/lib/xroad
  chown xroad:xroad /var/lib/xroad
  chmod 0755 /var/lib/xroad
  chmod -R go-w /var/lib/xroad

  # config folder permissions
  chown xroad:xroad /etc/xroad
  chmod 0751 /etc/xroad

  # nicer log directory permissions
  mkdir -p /var/log/xroad
  chmod -R go-w /var/log/xroad
  chmod 1770 /var/log/xroad
  chown xroad:adm /var/log/xroad

  #tmp folder
  mkdir -p /var/tmp/xroad
  chmod 1750 /var/tmp/xroad
  chown xroad:xroad /var/tmp/xroad
}

# Run setup_directories function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  setup_directories
fi