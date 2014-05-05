java_import Java::ee.cyber.sdsb.common.request.ManagementRequestClient

# Actually doesn't do anything, but lets follow the StartStop interface
ManagementRequestClient::getInstance().start()

# Cleanup on shutdown
at_exit do
  ManagementRequestClient::getInstance().stop()
end
