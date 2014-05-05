# Starts signer client for key helper to use.
java_import Java::ee.cyber.sdsb.common.CenterServices

CenterServices::start()

# Cleanup on shutdown
at_exit do
  CenterServices::stop()
end