require 'thread'

java_import Java::java.lang.System

# Application-wide file read/write functions
# XXX: Duplication with proxy-ui/app/helpers/sdsb_file_utils.rb.
# If changed, make sure that other file gets synchronized as well.
# TODO (RM task #3505): How to handle dependencies common for center-common,
# center-ui and proxy-ui?
class SdsbFileUtils
  def self.write(file_path, content)
    Mutex.new.synchronize do
      File.open(file_path, "w:UTF-8") { |f| f.write(content) }
    end
  end

  def self.write_binary(file_path, content)
    Mutex.new.synchronize do
      File.open(file_path, 'wb') {|f| f.write(content) }
    end
  end

  def self.read(file_path)
    result = ""

    Mutex.new.synchronize do 
      File.open(file_path, "r:UTF-8") do |f|
        result << f.read()
      end
    end

    return result
  end

  def self.read_binary(file_path)
    Mutex.new.synchronize do
      File.open(file_path, "rb") do |f|
        return f.read()
      end
    end
  end

  def self.read_to_array(file_path)
    return [] if !(File.exist?(file_path))
    result = []

    Mutex.new.synchronize do 
      File.open(file_path, "r:UTF-8") do |f|
        f.each_line do |each|
          result << each
        end
      end
    end

    return result
  end

  def self.get_log_dir
    return System.getProperty("ee.cyber.sdsb.appLog.path", "/var/log/sdsb")
  end
end
