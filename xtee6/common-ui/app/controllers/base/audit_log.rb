#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

require "common-ui/identifiers_as_json"

java_import Java::ee.ria.xroad.common.AuditLogger

module Base
  module AuditLog

    module_function

    def audit_log(event, data)
      after_commit do
        message = unescape({
          :event => event,
          :user => current_user.name,
          :data => data
        }.to_json)

        logger.debug("AUDIT SUCCESS: #{message}")

        AuditLogger::log(message)
      end

      after_rollback do |exception|
        message = unescape({
          :event => "#{event} failed",
          :user => current_user.name,
          :reason => exception.message,
          :data => data,
        }.to_json)

        logger.debug("AUDIT FAIL: #{message}")

        AuditLogger::log(message)
      end
    end

    ##
    # Undo escaping of non-ASCII characters in json strings
    def unescape(json_string)
      json_string.gsub(/\\u([0-9a-z]{4})/) {|s| [$1.to_i(16)].pack("U")}
    end
  end
end
