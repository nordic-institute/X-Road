#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

java_import Java::ee.ria.xroad.common.util.CryptoUtils

# Constructs signed directory into the target file.
class SignedDirectoryDistributor
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

  def distribute(last_signed_file)
    CommonUi::IOUtils.write_public(
        @target_file_path, get_writing_process(last_signed_file))
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
          "boundary=#{last_signed_file_record[:data_boundary]}\n"
      file.write "\n"

      file.write last_signed_file_record[:data].force_encoding(
          Rails.configuration.encoding)

      file.write "\n"
      file.write "--#{main_boundary}\n"

      file.write "Content-Type: application/octet-stream\n"
      file.write "Content-Transfer-Encoding: base64\n"
      file.write "Signature-Algorithm-Id: "\
          "#{CryptoUtils.getSignatureAlgorithmURI(
              last_signed_file_record[:sig_algo_id])}\n"

      verification_cert_hash =
          @hash_calculator.calculateFromBytes(@verification_cert.to_java_bytes())

      file.write "Verification-certificate-hash: #{verification_cert_hash}; "\
          "hash-algorithm-id=\"#{@hash_calculator.getAlgoURI()}\"\n"
      file.write "\n"

      file.write last_signed_file_record[:signature]
      file.write "\n"

      file.write "--#{main_boundary}--\n"
    end
  end
end
