java_import Java::ee.cyber.sdsb.common.signer.SignerHelper

class DistributedFilesSigner

  def self.create_data_to_sign(data_boundary)
    curdate = %x[date -u '+%Y-%m-%dT%H:%M:%S%z'].chomp
    file_boundary = %x[openssl rand -base64 20].chomp

    data = []

    data << "--#{data_boundary}"
    data << "Content-Type: multipart/mixed; charset=UTF-8; boundary=#{file_boundary}"
    data << "Content-Date: #{curdate}"
    data << ""

    DistributedFiles.all.each do |distributed_file|
      data << "--#{file_boundary}"
      data << "Content-File-Name: #{distributed_file.file_name}"
      data << "" << distributed_file.file_data
    end

    data << "--#{file_boundary}--"
    data << ""
    data << "--#{data_boundary}--"

    data.join("\n")
  end

  def self.sign
    sign_key_id = SystemParameter::conf_sign_key_id
    if !sign_key_id
      raise "Signing key id system parameter not set in database!"
    end

    sig_algo_id = SystemParameter::conf_sign_algo_id
    if !sig_algo_id
      raise "Signature algorithm id system parameter not set in database"
    end

    data_boundary = %x[openssl rand -base64 20].chomp
    data = create_data_to_sign(data_boundary)
    signature = SignerHelper.sign(sign_key_id, sig_algo_id, data)

    DistributedSignedFiles.delete_all
    DistributedSignedFiles.create(
        :data => data,
        :data_boundary => data_boundary,
        :signature => signature,
        :sig_algo_id => sig_algo_id)
  end

end
