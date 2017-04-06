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

require 'json'

class GlobalConfGenerationStatus

  def self.write_success(log_file_content)
    write(true, log_file_content)
  end

  def self.write_failure(log_file_content)
    write(false, log_file_content)
  end

  def self.get
    status_file = get_status_file()

    if !File.exists?(status_file)
      return {:no_status_file => true}
    end

    raw_status = CommonUi::IOUtils.read(status_file)

    if raw_status.blank?
      return {
        :time => File.mtime(status_file),
        :success => false
      }
    end

    status_as_json = JSON.parse(raw_status)

    return {
      :time => Time.at(status_as_json["time"]),
      :success => status_as_json["success"]
    }
  end

  private

  def self.write(success, log_file_content)
    status_as_hash = {
      :time => Time.now().to_f(),
      :success => success
    }

    if can_write_status_file?(success)
      CommonUi::IOUtils.write(get_status_file(), status_as_hash.to_json())
    end

    CommonUi::IOUtils.write(get_log_file(), log_file_content)
  end

  def self.can_write_status_file?(success)
    previous_successful = get()[:success]
    previous_successful = true if previous_successful == nil

    return success || previous_successful
  end

  def self.get_status_file
    log_dir = CommonUi::IOUtils.get_log_dir()
    return "#{log_dir}/.global_conf_gen_status"
  end

  def self.get_log_file
    log_dir = CommonUi::IOUtils.get_log_dir()
    return "#{log_dir}/xroad_distributed_files-distributed_files-globalconf"
  end
end
