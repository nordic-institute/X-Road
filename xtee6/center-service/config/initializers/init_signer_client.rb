unless $rails_rake_task
  # Starts signer client for key helper to use.
  java_import Java::ee.cyber.sdsb.common.CenterServices

  Rails.application.config.after_initialize do
    CenterServices::start()
  end

  # Cleanup on shutdown
  at_exit do
    CenterServices::stop()
  end
end
