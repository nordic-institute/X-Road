unless $rails_rake_task
  # Starts signer client for key helper to use.
  java_import Java::ee.cyber.sdsb.proxyui.ProxyUIServices

  Rails.application.config.after_initialize do
    ProxyUIServices::start()
  end

  # Cleanup on shutdown
  at_exit do
    ProxyUIServices::stop()
  end
end
