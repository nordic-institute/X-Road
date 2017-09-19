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

require 'arjdbc/jdbc/connection_methods'

# Overrides 'execute' method in class JdbcAdapter of gem
# 'activerecord-jdbc-adapter'. Adds performing reconnecting attempt and
# executing query once more, when connection-related error occurs.
#
# XXX: Is there cleaner and/or more elegant way to accomplish reconnect?
module ActiveRecord
  module ConnectionAdapters
    class JdbcAdapter < AbstractAdapter
      alias_method :old_execute, :execute

      def execute(sql, name=nil, binds=[])
        old_execute(sql, name, binds)
      rescue ActiveRecord::StatementInvalid, ActiveRecord::JDBCError => e
        Rails.logger.error("Database connection was down meanwhile, "\
            "reconnected\n#{e.message}")

        ActiveRecord::Base.establish_connection()

        old_execute(sql, name, binds)
      end
    end
  end
end
