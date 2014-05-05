# XXX: Work-around to require jars.
# TODO: How to load file for test environment?
$CLASSPATH << "build/libs"
Dir.glob(File.expand_path("../../../build/libs/*.jar", __FILE__)).each do |file|
  require file
end

# Here Java imports must be after loading jars
java_import Java::ee.cyber.sdsb.common.conf.serverconf.AuthorizedSubjectType
java_import Java::ee.cyber.sdsb.common.identifier.ClientId
java_import Java::javax.xml.datatype.DatatypeFactory
java_import Java::javax.xml.datatype.XMLGregorianCalendar
java_import Java::javax.xml.datatype.DatatypeConfigurationException
java_import Java::java.util.GregorianCalendar

require 'fileutils'
require 'benchmark'
require "conf_helper"

include ConfHelper

TEMP_DIR = "build/tmp"

INITIAL_CONF_SMALL = "../systemtest/conf/local_test/serverconf_consumer.xml"
TEMP_CONF_SMALL = "#{TEMP_DIR}/serverconf_small.xml"

INITIAL_CONF_LARGE = "test/resources/serverconf_large.xml"
TEMP_CONF_LARGE = "#{TEMP_DIR}/serverconf_large.xml"

def set_up
  if !File.exists?(TEMP_DIR)
    FileUtils.mkdir_p(TEMP_DIR)
  end

  FileUtils.cp(INITIAL_CONF_SMALL, TEMP_CONF_SMALL)
  FileUtils.cp(INITIAL_CONF_LARGE, TEMP_CONF_LARGE)
end

def tear_down
  FileUtils.rm(TEMP_CONF_SMALL)
  FileUtils.rm(TEMP_CONF_LARGE)
end

def benchmark(operation_name)
  puts "Starting benchmarking operation '#{operation_name}'"

  Benchmark.bmbm do |x|
    x.report do
      yield
    end
  end

  puts "Finished benchmarking operation '#{operation_name}'"
end

def read_serverconf(temp_conf_file)
  Conf.new(temp_conf_file,
      Java::ee.cyber.sdsb.common.conf.serverconf.ObjectFactory.java_class,
      {:temp_dir => TEMP_DIR})
end

def get_xml_gregorian_calendar(date_time)
  df = DatatypeFactory.newInstance
  gc = GregorianCalendar.new
  gc.setTimeInMillis(date_time.to_java.getTime())
  df.newXMLGregorianCalendar(gc)
end

# -- Methods to be benchmarked - start ---

def read_small_serverconf
  benchmark("Read small serverconf") do
    read_serverconf(TEMP_CONF_SMALL)
  end
end

def read_large_serverconf
  benchmark("Read large (200 services, 800 total ACL-s) serverconf") do
    read_serverconf(TEMP_CONF_LARGE)
  end
end

# XXX: Tried to read serverconf with 200 services and 800 ACL-s per service, but
# 4G of max heap size was too few for it.

def write_large_serverconf
  conf = read_serverconf(TEMP_CONF_LARGE)

  benchmark("Write large (200 services, 800 total ACL-s) serverconf") do
    client = conf.root.client[0]
    service = client.wsdl[0].service[199]

    new_acl = AuthorizedSubjectType.new
    client_id = ClientId.create(
        "EE", "memberClass", "memberCode", "subsystemCode")

    new_acl.subject_id = client_id
    new_acl.rights_given = get_xml_gregorian_calendar(Time.now)

    service.authorizedSubject << new_acl

    conf.write
  end
end

def write_lot_of_acl_entries
  conf = read_serverconf(TEMP_CONF_LARGE)
  acl_entries_count = 800

  benchmark("Write #{acl_entries_count} ACL entries to serverconf") do
    client = conf.root.client[0]
    service = client.wsdl[0].service[100]

    acl_entries_count.times do |i|
      new_acl = AuthorizedSubjectType.new
      client_id = ClientId.create(
          "EE", "memberClass", "memberCode_#{i}", "subsystemCode_#{i}")

      new_acl.subject_id = client_id

      new_acl.rights_given = get_xml_gregorian_calendar(Time.now)

      service.authorizedSubject << new_acl
    end

    conf.write
  end
end

def read_change_and_save
  benchmark("Read, write and save large serverconf") do
    conf = read_serverconf(TEMP_CONF_LARGE)
    conf.root.tspUrl[0] = "http://newtspurl.com"
    conf.write
  end
end

# -- Methods to be benchmarked - end ---

set_up

read_small_serverconf

read_large_serverconf

write_large_serverconf

write_lot_of_acl_entries

read_change_and_save

tear_down