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

java_import Java::ee.ria.xroad.common.conf.globalconf.PrivateParameters
java_import Java::ee.ria.xroad.common.conf.globalconf.SharedParameters

# This controller contains actions for public requests for the status of
# the system. No authentication is required for making the requests.
# XXX The actions in BaseController are accessible via this controller, too.
# FIXME: konfida routes.rb sees t2psemad piirangud või t6sta lahku veahalduse
# abivahendid ja tokeniga seotud tegevused baaskontrolleris!
class PublicSystemStatusController < BaseController

  # The meaning of HA node status labels as of BDR 0.9
  # (from http://bdr-project.org/docs/0.9.0/catalog-bdr-nodes.html):
  BDR_NODE_STATUS_LABELS = {
    "r" => "ready",
    "b" => "bootstrapping",
    "i" => "initial slot creation or dump",
    "c" => "catching up",
    "o" => "caught up, waiting for slot creation", 
    "k" => "killed or removed"
  }
  HA_STATUS_UNKNOWN = :unknown

  def index
  end

  def check_ha_cluster_status
    return render :json => {:ha_node_status => ha_node_status}
  end

  private

  # Return the status information of the HA nodes.
  def ha_node_status
    if !CommonSql.ha_configured?
      return { :ha_configured => false }
    end
 
    status_info = {
      :ha_configured => true, :nodes =>Hash.new,
    }
    all_nodes_ready = true
    configuration_ok = true
    all_nodes_ok = true
    replication_slot_count = 0

    node_status_result = ActiveRecord::Base.connection.execute(
      "select * from get_xroad_bdr_replication_info();")
    node_status_result.each do |node_info|
      node_ready, conf_ok, replication_active = format_node_status_info(
        status_info, node_info)
      if !node_ready
        all_nodes_ready = false
      end
      if !conf_ok
        configuration_ok = false
      end
      if replication_active
        replication_slot_count += 1
      end
    end
    status_info[:configuration_ok] = configuration_ok
    status_info[:all_nodes_ok] =
      replication_slot_count >= (node_status_result.length - 1) &&
      all_nodes_ready && configuration_ok
    return status_info
  end

  def private_params_update_timestamp(node_name)
    return params_last_update_timestamp(
      node_name, PrivateParameters::FILE_NAME_PRIVATE_PARAMETERS)
  end

  def shared_params_update_timestamp(node_name)
    return params_last_update_timestamp(
      node_name, SharedParameters::FILE_NAME_SHARED_PARAMETERS)
  end

  def params_last_update_timestamp(node_name, file_name)
    params = DistributedFiles.where(
        :ha_node_name => node_name, :file_name => file_name).first
    if params != nil && params.file_updated_at != nil
      return params.file_updated_at
    end
    return HA_STATUS_UNKNOWN
  end

  def internal_anchor_update_timestamp(node_name)
    return anchor_last_update_timestamp(
      node_name, ConfigurationSource::SOURCE_TYPE_INTERNAL)
  end

  def external_anchor_update_timestamp(node_name)
    return anchor_last_update_timestamp(
      node_name, ConfigurationSource::SOURCE_TYPE_EXTERNAL)
  end

  def anchor_last_update_timestamp(node_name, source_type)
    anchor = ConfigurationSource.where(
        :ha_node_name => node_name, :source_type => source_type).first
    if anchor != nil && anchor.anchor_generated_at != nil
      return anchor.anchor_generated_at
    end
    return HA_STATUS_UNKNOWN
  end

  def format_node_status_info(status_info, node_info)
    node_ready = true
    configuration_ok = true

    node_name = node_info["node_name"]
    node_status = node_info["node_status"]
    node_ready = (node_status == "r")

    friendly_node_status = if BDR_NODE_STATUS_LABELS[node_status] != nil then
      BDR_NODE_STATUS_LABELS[node_status]
    else
      HA_STATUS_UNKNOWN
    end
    status_info[:nodes][node_name] = {
      :node_status => friendly_node_status,
    }

    timestamp_keys = [
      :private_params_update_timestamp,
      :shared_params_update_timestamp,
      :internal_anchor_update_timestamp,
      :external_anchor_update_timestamp
    ]
    timestamp_keys.each do |key|
      timestamp = send(key, node_name)
      if timestamp == HA_STATUS_UNKNOWN
        configuration_ok = false
      end
      status_info[:nodes][node_name][key] = timestamp
    end

    replication_active = (node_info["replication_active"] == "t")
    if replication_active
      status_info[:nodes][node_name]["replication_client_address"] =
          node_info["client_addr"]
      status_info[:nodes][node_name]["replication_state"] =
          node_info["replication_state"]
      status_info[:nodes][node_name]["replication_lag_bytes"] =
          node_info["replication_lag_bytes"]
    end

    return node_ready, configuration_ok, replication_active
  end

end

