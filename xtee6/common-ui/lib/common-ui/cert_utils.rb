require 'openssl'

java_import Java::ee.ria.xroad.common.util.CryptoUtils

module CommonUi
  module CertUtils

    module_function

    # Parameters:
    # cert - OpenSSL::X509::Certificate
    def cert_csp(cert)
      issuer_cn = ""
      cert.issuer.to_a.each do |part|
        if part[0] == "CN"
          issuer_cn = part[1]
          break
        end
      end
      issuer_cn
    end

    # Parameters:
    # cert - either OpenSSL::X509::Certificate, binary data
    #        as Ruby String, or binary data as Java's byte[]
    def cert_object(cert)
      bytes = cert_to_bytes(cert)
      OpenSSL::X509::Certificate.new(bytes) if bytes
    end

    # Parameters:
    # cert - either OpenSSL::X509::Certificate, binary data
    #        as Ruby String, or binary data as Java's byte[]
    def cert_dump(cert)
      return nil unless cert

      %x[echo "#{cert_object(cert).to_pem}" | openssl x509 -text -noout -nameopt oneline,-esc_msb 2>&1]
    end

    # Parameters:
    # cert - either OpenSSL::X509::Certificate, binary data
    #        as Ruby String, or binary data as Java's byte[]
    def cert_hash(cert)
      java_bytes = cert_to_java_bytes(cert)
      return nil unless java_bytes

      hash = CryptoUtils::calculateCertHexHash(java_bytes)
      hash.upcase.scan(/.{1,2}/).join(':')
    end

    def cert_hash_algorithm
      CryptoUtils::DEFAULT_CERT_HASH_ALGORITHM_ID
    end

    # Parameters:
    # cert - PEM or DER encoded cert bytes as Ruby String
    #
    # Returns DER bytes as Ruby String.
    def pem_to_der(cert)
      OpenSSL::X509::Certificate.new(cert).to_der
    rescue
      Rails.logger.error($!.message)
      raise I18n.t('validation.invalid_cert')
    end

    # Parameters:
    # cert - PEM or DER encoded cert bytes as Ruby String
    #
    # Returns java.security.cert.X509Certificate.
    def pem_to_java_cert(cert)
      raw_cert = pem_to_der(cert)
      return CryptoUtils::readCertificate(raw_cert.to_java_bytes())
    end

    private_class_method

    def cert_to_java_bytes(cert)
      if cert.kind_of?(OpenSSL::X509::Certificate)
        cert.to_der.to_java_bytes
      elsif cert.kind_of?(String)
        cert.to_java_bytes
      elsif cert.java_kind_of?(Java::byte[])
        cert
      else
        nil
      end
    end

    def cert_to_bytes(cert)
      if cert.kind_of?(OpenSSL::X509::Certificate)
        cert.to_der
      elsif cert.kind_of?(String)
        cert
      elsif cert.java_kind_of?(Java::byte[])
        String.from_java_bytes(cert)
      else
        nil
      end
    end
  end
end
