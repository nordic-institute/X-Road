class SystemParameter < ActiveRecord::Base

  CONF_SIGN_KEY_ID = "confSignKeyId"
  CONF_SIGN_ALGO_ID = "confSignAlgoId"

  SDSB_INSTANCE = "sdsbInstance"

  MGMT_SERVICE_URL = "mgmtServiceUrl"
  MGMT_SERVICE_ID_CLASS = "mgmtServiceIdClass"
  MGMT_SERVICE_ID_CODE = "mgmtServiceIdCode"
  MGMT_SERVICE_ID_SUBSYSTEM = "mgmtServiceIdSubsystem"

  # Group name that will automatically be filled with security server owners.
  SERVER_OWNERS_GROUP =  "serverOwnersGroup"

  validates_with Validators::MaxlengthValidator

  def self.get(keyId)
    value_in_db = SystemParameter.where(:key => keyId)
    if value_in_db.first
      return value_in_db.first.value
    end
    nil
  end

  def self.sdsb_instance
    get(SDSB_INSTANCE)
  end

  def self.conf_sign_key_id
    get(CONF_SIGN_KEY_ID)
  end

  def self.conf_sign_algo_id
    get(CONF_SIGN_ALGO_ID)
  end

  def self.management_service_url
    get(MGMT_SERVICE_URL)
  end

  def self.management_service_id_class
    get(MGMT_SERVICE_ID_CLASS)
  end

  def self.management_service_id_code
    get(MGMT_SERVICE_ID_CODE)
  end

  def self.management_service_id_subsystem
    get(MGMT_SERVICE_ID_SUBSYSTEM)
  end

  def self.server_owners_group
    get(SERVER_OWNERS_GROUP)
  end
end
