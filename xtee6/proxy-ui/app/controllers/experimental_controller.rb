require "net/http"

java_import Java::ee.cyber.sdsb.common.util.CryptoUtils
java_import Java::ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo

# Controller to developers for experimenting
# Hashes are the ones that are present in
# PROJECT/systemtest/conf/testservers/consumer/serverconf.xml
class ExperimentalController < ApplicationController

  def index
  end

  def experiment

    method = params[:method]
    @result = send("test_" + method)

    render :partial => "result"
  rescue Java::java.lang.Exception
    @result="EXCEPTION: #{$!}"
    render :partial => "result"
  end

  def test_list_devices
    list_devices
  end

  def test_activate_device
    # TODO - turning password into Java string may be security threat!
    # Find better way to turn password into char array!
    activate_device("device_UID", "kala".to_java.to_char_array)
  end

  def test_deactivate_device
    deactivate_device("deviceUid")
  end

  def test_set_device_friendly_name
    set_device_friendly_name("deviceUid", "Friendly name for device")
  end

  def test_set_key_friendly_name
    set_key_friendly_name("636f6e73756d6572", "Friendly name for key")
  end

  def test_generate_key
    generate_key("deviceUID")
  end

  def test_import_cert
    import_cert(get_cert_bytes)
  end

  def test_activate_cert
    activate_cert("352db5c70f0f1fb8287b2aa4ce14fd35da978bd7")
  end

  def test_deactivate_cert
    deactivate_cert("352db5c70f0f1fb8287b2aa4ce14fd35da978bd7")
  end

  def test_generate_cert_request
    # request_ctx = CertRequestCtx.new("2", "02d66d5589f5b8626875ef99a11a84f7b2aa202f",
    #   "producer", KeyUsageInfo::SIGNING,
    #   "producer", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA247T/ihTbSn
    #   LiNMo28Z3gHphXAoEDSIB/oj/R7aaZyIXlVNFAV8YjgtpWDNhwPV4OlZ
    #   cnRuX61WDv7jwKn6xucDSwBnU5iOuVALqRIHQtzopZNAiCHnXrp9yXZ1
    #   jng7bUz2gTVlPh5oX3tf5ejcdnkgKth/TKaCpFkhlj7ewwQUTixRkm83
    #   pUq487Q/yvm60OBTIX8ujK8dx0DPUuyraJSQ4h4pDr8jjEDXvFgD0nT1
    #   FCHkFO/Vo+2FEnrAGaHLZoYfBEmQz2ZP+xS92wXGTM47TLV+fB9IkgA0
    #   LdLe5OhEmMGSqpJDTAyZAdVwnG9tykqAlpsoTTJt8MkEdIWQtrwIDAQAB")
    # result = generate_cert_request(request_ctx)
    # puts "Byte array not nil" if result
    # result
    nil
  end
  
  def test_delete_cert
    delete_cert("352db5c70f0f1fb8287b2aa4ce14fd35da978bd7")
  end

  def test_delete_key
    delete_key("636f6e73756d6572")
  end

  def test_get_cached_devices
    get_cached_devices
  end

  def get_cert_bytes
    CryptoUtils::decodeBase64(
    "MIIDiDCCAnCgAwIBAgIIW99Q5VUloqswDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UEAwwIQWRtaW
    5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0UwHhcNMTIwOTI4MTgxNzM5W
    hcNMTQwOTI4MTgxNzM5WjATMREwDwYDVQQDDAhjb25zdW1lcjCCASIwDQYJKoZIhvcNAQEBBQAD
    ggEPADCCAQoCggEBAILY5AcoHHeoHIYqrrjaadQJwJlwMFN8mT/txE4/oKUWecvikwk1RNJNH0s
    +D9iUoCsCYqlU7PXbIXIelkH08ehgsdi5OmNAiG0fxEIouPDDOg5L5c4wxOm1/vVf0H+yBrv1OW
    UfEnCwsiRmqRN1JU9LH1GkVulPdqCMbicqlbidTTfYcFwf4R7RfOFeHrrNJSBvRev+TUt+JnwbO
    4vHFxhGDBXMLwiNZdedhE9NO3zUorWPEiVNapp/u0agMXAv3RmJsIGeVJerGFay7Eb9RbhTcHOe
    PGl1IetV7J3A9L14OqauMShaFJQUnTXSqS8ldcge/JfgSiWTqE0TjVc0pYMCAwEAAaOBuzCBuDB
    YBggrBgEFBQcBAQRMMEowSAYIKwYBBQUHMAGGPGh0dHA6Ly9pa3MyLXVidW50dS5jeWJlci5lZT
    o4MDgwL2VqYmNhL3B1YmxpY3dlYi9zdGF0dXMvb2NzcDAdBgNVHQ4EFgQU25SlUgQRwFCiraz2e
    uhPUBqpvj0wDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBR3LYkuA7b9+NJlOTE1ItBGGujSCTAO
    BgNVHQ8BAf8EBAMCBeAwDQYJKoZIhvcNAQEFBQADggEBAFFWRyInsq/jKrW20BKzRr2KAAnE2nD
    VmZLFfcv7ZwrLOOJYkHxdPEfkcXcwJy4B1KJdvm0+1FlgfoKgDiUjTRbXraXmyUwAL5s5yMr9wF
    wu9N9JL6IwchMNT6S5zwA+iioLMQbHAMfwXXSS/Vp7aUxmejK4XbNtehsukalD7S3ILAK7dtamP
    r0YvRqUBbj4k9zD60gVU13jmACr/JuSXI4JxyoiFdUNDdtQbiiGOsrOuLmc/WbzXNo7iN/zhwEM
    JNJThtyGYthhiYeZKT+0B5Yy/sARkinWqLpUwddf+plfH+4HP2akrt8uoHSZXKKOmN8IlXgN89L
    PVBC+oSltnhY=")
  end
end
