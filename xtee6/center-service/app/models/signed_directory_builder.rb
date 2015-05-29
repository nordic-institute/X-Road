java_import Java::ee.ria.xroad.common.util.CryptoUtils

# Constructs signed directory into the target file.
class SignedDirectoryBuilder
  def initialize(conf_dir, target_file, hash_calculator, verification_cert)
    raise "Target file must not be blank" if target_file.blank?
    raise "Hash calculator must be present" if hash_calculator == nil
    raise "Verification cert must not be blank" if verification_cert.blank?

    @target_file_path = "#{conf_dir}/#{target_file}"
    @hash_calculator = hash_calculator
    @verification_cert = verification_cert

    Rails.logger.debug(
        "Created signed directory builder with target file "\
        "'#@target_file_path' "\
        "and hash algorithm URI: '#{hash_calculator.getAlgoURI()}'")
  end

  def build(last_signed_file_record)
    CommonUi::IOUtils.write_public(
        @target_file_path, get_writing_process(last_signed_file_record))
  rescue => e
    Rails.logger.error(
        "Building signed directory into "\
        "'#@target_file' failed: #{e.message}")
    raise e
  end

  private

  def get_writing_process(last_signed_file_record)
    main_boundary = Utils.create_mime_boundary()

    return Proc.new do |file|
      file.write "Content-Type: multipart/related; charset=UTF-8; "\
          "boundary=#{main_boundary}\n"
      file.write "\n"
      file.write "--#{main_boundary}\n"

      file.write "Content-Type: multipart/mixed; charset=UTF-8; "\
          "boundary=#{last_signed_file_record.data_boundary}\n"
      file.write "\n"

      file.write last_signed_file_record.data.force_encoding(
          Rails.configuration.encoding)

      file.write "\n"
      file.write "--#{main_boundary}\n"

      file.write "Content-Type: application/octet-stream\n"
      file.write "Content-Transfer-Encoding: base64\n"
      file.write "Signature-Algorithm-Id: "\
          "#{CryptoUtils.getSignatureAlgorithmURI(
              last_signed_file_record.sig_algo_id)}\n"

      verification_cert_hash =
          @hash_calculator.calculateFromBytes(@verification_cert.to_java_bytes())

      file.write "Verification-certificate-hash: #{verification_cert_hash}; "\
          "hash-algorithm-id=\"#{@hash_calculator.getAlgoURI()}\"\n"
      file.write "\n"

      file.write last_signed_file_record.signature
      file.write "\n"

      file.write "--#{main_boundary}--\n"
    end
  end
end
