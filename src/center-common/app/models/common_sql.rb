#
# The MIT License
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
# Static helper methods for common raw SQL

require 'java'

class CommonSql

  @@is_postgres = nil
  @@ha_configured = nil
  @@ha_node_name = nil

  # Column with format table.column
  def self.turn_timestamp_into_text(column)
    return "lower(#{column})" if !is_postgres?()

    return "to_char(#{column} "\
        "AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS')"
  end

  def self.is_postgres?
    if @@is_postgres == nil
      conf = Rails.configuration.database_configuration[Rails.env]
      @@is_postgres = "postgresql".eql?(conf["adapter"])
    end

    return @@is_postgres
  end

  # Check if a HA cluster of databases has been configured and assign the
  # corresponding class variables.
  def self.detect_ha_support
    if @@ha_configured != nil
      return
    end

    if !CommonSql.is_postgres?
      @@ha_configured = false
      return
    end

    node_name = java.lang.System.get_property("xroad.center.ha-node-name")

    if node_name && !node_name.empty?
      @@ha_node_name = node_name
      @@ha_configured = true
    else
      @@ha_configured = false
    end
  end

  # Return true if BDR nodes have been configured in the database, and a node
  # name exists for this instance of the database.
  # Normally, this property will not change during the lifetime of this class
  # if the database has been configured properly
  def self.ha_configured?
    if @@ha_configured == nil
      CommonSql.detect_ha_support
    end
    return @@ha_configured
  end

  # Return the BDR node name from the database if we are running in HA setup or nil.
  # Normally, this property will not change during the lifetime of this class
  # if the database has been configured properly.
  def self.ha_node_name
    if @@ha_configured == nil
      CommonSql.detect_ha_support
    end
    return @@ha_node_name
  end

  # Return the names of all the known HA nodes or an empty list.
  def self.available_ha_nodes
    if @@ha_configured == nil
      CommonSql.detect_ha_support
    end

    if !@@ha_configured
      return []
    end
    return SystemParameter.where(key: SystemParameter::CENTRAL_SERVER_ADDRESS).map { |x| x.node_name }
  end

end
