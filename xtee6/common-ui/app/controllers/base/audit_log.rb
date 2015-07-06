require "common-ui/identifiers_as_json"

java_import Java::ee.ria.xroad.common.AuditLogger

module Base
  module AuditLog

    module_function

    def audit_log(event, data)
      after_commit do
        message = {
          :event => event,
          :user => current_user.name,
          :data => data
        }.to_json

        logger.debug("AUDIT SUCCESS: #{message}")

        AuditLogger::log(message)
      end

      after_rollback do |exception|
        message = {
          :event => "#{event} failed",
          :user => current_user.name,
          :reason => exception.message,
          :data => data,
        }.to_json

        logger.debug("AUDIT FAIL: #{message}")

        AuditLogger::log(message)
      end
    end
  end
end
