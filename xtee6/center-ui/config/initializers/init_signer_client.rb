unless $rails_rake_task
  # Starts signer client to see status of it.
  java_import Java::ee.cyber.sdsb.centerui.CenterUIServices

  CenterUIServices::start()

  # Cleanup on shutdown
  at_exit do
    CenterUIServices::stop()
  end
end
