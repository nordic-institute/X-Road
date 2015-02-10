# Parses configuration files (in .ini format) in production environment.
class DbConfParser
  def initialize(environment, conf_file)
    @environment = environment
    @conf_file = conf_file
  end

  def parse
    ini_conf = Java::org.apache.commons.configuration.\
        HierarchicalINIConfiguration.new(@conf_file)

    db_conf = {
        "adapter" => ini_conf.getString("adapter"),
        "encoding" => ini_conf.getString("encoding"),
        "username" => ini_conf.getString("username"),
        "password" => ini_conf.getString("password"),
        "database" => ini_conf.getString("database"),
        "reconnect" => ini_conf.getBoolean("reconnect")
    }

    return { @environment => db_conf }
  end
end
