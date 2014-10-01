java_import Java::ee.cyber.sdsb.common.util.CertUtils

class AuthCertValidator < UploadedFileValidator
  include RubyCertHelper

  def validate
    if !CertUtils::isAuthCert(pem_to_java_cert(read_file()))
      raise I18n.t("errors.request.cert_not_auth")
    end
  end
end
