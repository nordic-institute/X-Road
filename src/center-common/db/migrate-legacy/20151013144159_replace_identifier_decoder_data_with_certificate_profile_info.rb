class ReplaceIdentifierDecoderDataWithCertificateProfileInfo < ActiveRecord::Migration
  def change
    # remove_column :approved_cas, :identifier_decoder_member_class
    # remove_column :approved_cas, :identifier_decoder_method_name
    add_column :approved_cas, :cert_profile_info, :string
  end
end
