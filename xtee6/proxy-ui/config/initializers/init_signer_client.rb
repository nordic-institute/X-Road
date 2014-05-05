# Starts signer client for key helper to use.
java_import Java::ee.cyber.sdsb.proxyui.ProxyUIServices

ProxyUIServices::start()

# Cleanup on shutdown
at_exit do
  ProxyUIServices::stop()
end