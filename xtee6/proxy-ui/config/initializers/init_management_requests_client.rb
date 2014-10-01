unless $rails_rake_task
  java_import Java::ee.cyber.sdsb.common.request.ManagementRequestClient

  Rails.application.config.after_initialize do
    # Actually doesn't do anything, but lets follow the StartStop interface
    ManagementRequestClient::getInstance().start()
  end

  # Cleanup on shutdown
  at_exit do
    ManagementRequestClient::getInstance().stop()
  end
end
