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

class V5DataImportStatus

  def self.write(file_name, exit_status)
    status_as_hash = {
      :time => Time.now().to_f(),
      :file_name => file_name,
      :exit_status => exit_status
    }

    CommonUi::IOUtils.write(get_status_file(), status_as_hash.to_json())
  end

  def self.get
    status_file = get_status_file()

    if !File.exists?(status_file)
      return {:no_status_file => true}
    end

    raw_status = CommonUi::IOUtils.read(status_file)

    status_as_json = JSON.parse(raw_status)

    return {
      :time => Time.at(status_as_json["time"]),
      :file_name => status_as_json["file_name"],
      :exit_status => status_as_json["exit_status"]
    }
  end

  private

  def self.get_status_file
    home_dir = ENV["HOME"]
    import_dir = home_dir != nil ? home_dir : "."

    return "#{import_dir}/.v5_data_import_status"
  end
end
