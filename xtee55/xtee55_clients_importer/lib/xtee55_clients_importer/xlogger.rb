require 'java'
require 'xtee55_clients_importer/xtee55_clients_importer.jar'

module Xtee55ClientsImporter
  def xlogger(clazz)
    Java::OrgSlf4j::LoggerFactory.getLogger(clazz.name)
  end
end
