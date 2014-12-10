java_import Java::ee.cyber.sdsb.common.util.CertUtils

class AuthCertValidator < UploadedFileValidator

  def validate
    java_cert = CommonUi::CertUtils.pem_to_java_cert(read_file)

    if !CertUtils::isAuthCert(java_cert)
      raise I18n.t("errors.request.cert_not_auth")
    end
  end
end
