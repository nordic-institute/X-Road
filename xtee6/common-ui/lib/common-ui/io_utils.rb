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

require 'thread'
require 'fileutils'

java_import Java::ee.ria.xroad.common.SystemProperties

java_import Java::java.lang.System
java_import Java::java.io.RandomAccessFile

# Common file read/write functions
# TODO Remove Mutex. Acquire file locks for concrete files. RM #3505.
module CommonUi
  module IOUtils

    @@mutex = Mutex.new

    module_function

    # Writes in threadsafe manner reasonably small text file using encoding
    # specified by the application configuration.
    def write(file_path, content)
      Rails.logger.debug("write(#{file_path}, #{content})")

      @@mutex.synchronize do
        File.open(file_path, "w:#{Rails.configuration.encoding}") do |f|
          f.write(content)
        end
      end
    end

    # Writes in threadsafe manner reasonably small binary files.
    def write_binary(file_path, content)
      Rails.logger.debug("write_binary(#{file_path}, #{content.length} bytes)")

      @@mutex.synchronize do
        File.open(file_path, 'wb') {|f| f.write(content) }
      end
    end

    # Writes in threadsafe manner text file intended to be shared to the World.
    # Second parameter is Proc object that does actual writing, as public files
    # may be large in size.
    def write_public(file_path, writing_process)
      @@mutex.synchronize do
        begin
          file = File.open(file_path, "w:#{Rails.configuration.encoding}")
          writing_process.call(file)
          FileUtils.chmod(0644, file.path)
        ensure
          file.close()
        end
      end
    end

    def read(file_path)
      result = ""

      @@mutex.synchronize do
        File.open(file_path, "r:#{Rails.configuration.encoding}") do |f|
          result << f.read()
        end
      end

      return result
    end

    def read_binary(file_path)
      @@mutex.synchronize do
        File.open(file_path, "rb") do |f|
          return f.read()
        end
      end
    end

    def read_to_array(file_path)
      return [] if !(File.exist?(file_path))
      result = []

      @@mutex.synchronize do
        File.open(file_path, "r:#{Rails.configuration.encoding}") do |f|
          f.each_line do |each|
            result << each
          end
        end
      end

      return result
    end

    def get_log_dir
      return System.getProperty("ee.ria.xroad.appLog.path", "/var/log/xroad")
    end

    # Returns the full path to a file in temp dir.
    def temp_file(file)
      temp_dir = SystemProperties::getTempFilesPath

      unless File.directory?(temp_dir)
        FileUtils.mkdir_p(temp_dir)
      end

      "#{temp_dir}/#{file}"
    end

    def validate_filename(filename)
      if !is_filename_valid?(filename)
        raise I18n.t("common.filename_error", :file => filename)
      end
    end

    def is_filename_valid?(filename)
      return filename =~ /\A[\w\.\-]+\z/
    end

    # Returns lockfile of type java.io.RandomAccessFile of nil if aquiring lock
    # is unsuccessful.
    def try_lock(lockfile_name)
      lockfile = RandomAccessFile.new(temp_file(lockfile_name), "rw")
      return lockfile.getChannel.tryLock ? lockfile : nil
    rescue Java::java.nio.channels.OverlappingFileLockException
      return nil
    end

    # Argument must be of type java.io.RandomAccessFile.
    def release_lock(lockfile)
      unless lockfile.is_a?(RandomAccessFile)
        raise "Lock file must be of type 'java.io.RandomAccessFile', but is #{lockfile.class}"
      end

      # closing lockfile releases the lock
      lockfile.close
    end
  end
end
