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

# Parses configuration files (in .ini format) in production environment.
class DbConfParser
  def initialize(environment, conf_file)
    @environment = environment
    @conf_file = conf_file
  end

  def parse
    ini_conf = Java::org.apache.commons.configuration.HierarchicalINIConfiguration.new
    ini_conf.setDelimiterParsingDisabled(true)
    ini_conf.load(@conf_file)

    db_conf = {
        "adapter" => ini_conf.getString("adapter"),
        "encoding" => ini_conf.getString("encoding"),
        "username" => ini_conf.getString("username"),
        "password" => ini_conf.getString("password"),
        "database" => ini_conf.getString("database"),
        "host" => ini_conf.getString("host", "localhost"),
        "port" => ini_conf.getInt("port", 5432),
        "reconnect" => ini_conf.getBoolean("reconnect", true),
        :min_messages => ini_conf.getString("min_messages","error"),
        :properties => {}
    }

    if ini_conf.get_string("url")
      db_conf["url"] = ini_conf.getString("url")
    end

    if db_conf["adapter"] == "postgresql" && ini_conf.get_string("secondary_hosts")
      secondary_hosts = ini_conf.get_string("secondary_hosts").split("\s*,\s*")
                            .map { |x| x.include?(":") ? x : "#{x}:#{db_conf["port"]}" }
                            .join(",")
      db_conf["url"] =
          "jdbc:postgresql://#{db_conf["host"]}:#{db_conf["port"]},#{secondary_hosts}/#{db_conf["database"]}"
      db_conf[:properties] = {
          targetServerType: "master",
      }
    end

    schema = ini_conf.getString("schema", db_conf["username"])
    db_conf[:schema] = schema
    if schema != "public"
      db_conf[:properties][:currentSchema] = "#{schema},public"
    end

    it = ini_conf.get_section("properties").get_keys()
    while it.has_next?
      key = it.next
      db_conf[:properties][key.to_sym] = ini_conf.get_string("properties." + key)
    end

    return { @environment => db_conf }
  end
end
