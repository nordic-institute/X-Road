require 'openssl'

module RubyCertHelper

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

  def cert_from_bytes(bytes)
    OpenSSL::X509::Certificate.new(bytes)
  end
end
