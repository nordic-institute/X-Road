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

        old_execute(sql, name)
      end
    end
  end
end
