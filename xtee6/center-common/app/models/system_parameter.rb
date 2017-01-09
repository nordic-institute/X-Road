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
java_import Java::javax.xml.crypto.dsig.DigestMethod

class SystemParameter < ActiveRecord::Base

  INSTANCE_IDENTIFIER = "instanceIdentifier"

  CENTRAL_SERVER_ADDRESS = "centralServerAddress"

  MANAGEMENT_SERVICE_PROVIDER_CLASS = "managementServiceProviderClass"

  MANAGEMENT_SERVICE_PROVIDER_CODE = "managementServiceProviderCode"

  MANAGEMENT_SERVICE_PROVIDER_SUBSYSTEM = "managementServiceProviderSubsystem"

  SECURITY_SERVER_OWNERS_GROUP =  "securityServerOwnersGroup"
  DEFAULT_SECURITY_SERVER_OWNERS_GROUP = "security-server-owners"
  DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC = "Security server owners"

  AUTH_CERT_REG_URL = "authCertRegUrl"
  DEFAULT_AUTH_CERT_REG_URL = "https://%{centralServerAddress}:4001/managementservice/"

  CONF_SIGN_ALGO_ID = "confSignAlgoId"
  DEFAULT_CONF_SIGN_ALGO_ID = CryptoUtils::SHA512WITHRSA_ID

  CONF_HASH_ALGO_URI = "confHashAlgoUri"
  DEFAULT_CONF_HASH_ALGO_URI = DigestMethod.SHA512

  CONF_SIGN_CERT_HASH_ALGO_URI = "confSignCertHashAlgoUri"
  DEFAULT_CONF_SIGN_CERT_HASH_ALGO_URI = DigestMethod.SHA512

  OCSP_FRESHNESS_SECONDS = "ocspFreshnessSeconds"
  DEFAULT_OCSP_FRESHNESS_SECONDS = 3600

  TIME_STAMPING_INTERVAL_SECONDS = "timeStampingIntervalSeconds"
  DEFAULT_TIME_STAMPING_INTERVAL_SECONDS = 60

  CONF_EXPIRE_INTERVAL_SECONDS = "confExpireIntervalSeconds"
  DEFAULT_CONF_EXPIRE_INTERVAL_SECONDS = 600

  # These parameters are set independently on each instance of the central server
  # even though the records are replicated to each database node.
  NODE_LOCAL_PARAMETERS = [
    SystemParameter::CENTRAL_SERVER_ADDRESS
  ]

  validates_with Validators::MaxlengthValidator

  # Return true if the name of the local database node must be taken into
  # account when working with the parameter.
  def self.node_local_parameter?(key)
    return NODE_LOCAL_PARAMETERS.include?(key)
  end

  # Return the value corresponding to the given key.
  # In HA systems, the correct record must be found using the name of the
  # database node for some parameters.
  # In other cases, the default node name is used and each parameter should
  # have a single value.
  def self.get(key_id, default_value = nil)
    value_in_db = nil
    if CommonSql.ha_configured? && SystemParameter.node_local_parameter?(key_id)
      ha_node_name = CommonSql.ha_node_name
      value_in_db =
        SystemParameter.where(:key => key_id, :ha_node_name => CommonSql.ha_node_name)
    else
      value_in_db = SystemParameter.where(:key => key_id)
    end
    if value_in_db.first
      return value_in_db.first.value
    end
    return default_value
  end

  # Return an object representing a system parameter record with the given key
  # on the local database node.
  # For new records, the node name will be set by a trigger function when the
  # object is written to the database.
  def self.find_or_initialize(lookup_key, update_value)
    rec = nil
    if CommonSql.ha_configured? && SystemParameter.node_local_parameter?(lookup_key)
      ha_node_name = CommonSql.ha_node_name
      rec = SystemParameter.find_or_initialize_by_key_and_ha_node_name(
          lookup_key, ha_node_name)
    else
      rec = SystemParameter.find_or_initialize_by_key(lookup_key)
    end
    rec.update_attributes!(:value => update_value)
  end

  def self.instance_identifier
    get(INSTANCE_IDENTIFIER)
  end

  def self.central_server_address
    get(CENTRAL_SERVER_ADDRESS)
  end

  def self.conf_sign_algo_id
    get(CONF_SIGN_ALGO_ID)
  end

  def self.conf_hash_algo_uri
    get(CONF_HASH_ALGO_URI)
  end

  def self.conf_sign_cert_hash_algo_uri
    get(CONF_SIGN_CERT_HASH_ALGO_URI)
  end

  def self.auth_cert_reg_url
    url = get(AUTH_CERT_REG_URL)

    return "" if url.blank?

    url % {
      :centralServerAddress => get(CENTRAL_SERVER_ADDRESS)
    }
  end

  def self.management_service_provider_class
    get(MANAGEMENT_SERVICE_PROVIDER_CLASS)
  end

  def self.management_service_provider_code
    get(MANAGEMENT_SERVICE_PROVIDER_CODE)
  end

  def self.management_service_provider_subsystem
    get(MANAGEMENT_SERVICE_PROVIDER_SUBSYSTEM)
  end

  def self.management_service_provider_id
    provider_class = management_service_provider_class
    provider_code = management_service_provider_code
    provider_subsystem = management_service_provider_subsystem

    return nil unless provider_class && provider_code

    Java::ee.ria.xroad.common.identifier.ClientId.create(
        instance_identifier,
        provider_class,
        provider_code,
        provider_subsystem)
  end

  def self.security_server_owners_group
    get(SECURITY_SERVER_OWNERS_GROUP)
  end

  def self.ocsp_freshness_seconds
    get(OCSP_FRESHNESS_SECONDS, DEFAULT_OCSP_FRESHNESS_SECONDS).to_i()
  end

  def self.time_stamping_interval_seconds
    get(TIME_STAMPING_INTERVAL_SECONDS,
      DEFAULT_TIME_STAMPING_INTERVAL_SECONDS).to_i()
  end

  def self.conf_expire_interval_seconds
    get(CONF_EXPIRE_INTERVAL_SECONDS,
      DEFAULT_CONF_EXPIRE_INTERVAL_SECONDS).to_i()
  end
end
