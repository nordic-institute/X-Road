java_import Java::ee.ria.xroad.common.signer.SignerHelper

# Responsible of creating signed directory to the point when its content is
# stored into database.
class DirectorySigner
  # content_builder must be of type DirectoryContentBuilder.
  def initialize(sign_key_id, sign_algo_id, content_builder)
    if sign_key_id.blank?
      raise "Cannot sign without signing key"
    end

    if sign_algo_id.blank?
      raise "Signature algorithm id system parameter not set in database"
    end

    @sign_key_id = sign_key_id
    @sign_algo_id = sign_algo_id
    @content_builder = content_builder
  end

  def sign
    Rails.logger.debug("Signing directory with signing key '#{@sign_key_id}' "\
        "and signing algorithm '#{@sign_algo_id}'")

    data_boundary = Utils.create_mime_boundary()
    data = @content_builder.build(data_boundary)

    signature = SignerHelper.sign(@sign_key_id, @sign_algo_id, data)

    {
      :data => data,
      :data_boundary => data_boundary,
      :signature => signature,
      :sig_algo_id => @sign_algo_id
    }
  end
end
