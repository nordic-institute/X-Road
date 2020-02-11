java_import Java::ee.ria.xroad.common.util.CryptoUtils

class ReplaceConfSignAlgoIdSystemParam < ActiveRecord::Migration
  CONF_SIGN_ALGO_ID = "confSignAlgoId"

  def up
    sign_algo_ids = SystemParameter.where(:key => CONF_SIGN_ALGO_ID)

    if sign_algo_ids.first
      sign_algo_id = sign_algo_ids.first.value
      sign_digest_algo_id = CryptoUtils::getDigestAlgorithmId(sign_algo_id)

      SystemParameter.find_or_initialize(SystemParameter::CONF_SIGN_DIGEST_ALGO_ID, sign_digest_algo_id)

      sign_algo_ids.each do |i|
        i.destroy
      end
    end
  end

  def down
    sign_digest_algo_ids = SystemParameter.where(:key => SystemParameter::CONF_SIGN_DIGEST_ALGO_ID)

    if sign_digest_algo_ids.first
      sign_digest_algo_id = sign_digest_algo_ids.first.value
      sign_algo_id = CryptoUtils::getSignatureAlgorithmId(sign_digest_algo_id, CryptoUtils::CKM_RSA_PKCS_NAME)

      SystemParameter.find_or_initialize(CONF_SIGN_ALGO_ID, sign_algo_id)

      sign_digest_algo_ids.each do |i|
        i.destroy
      end
    end
  end

end
