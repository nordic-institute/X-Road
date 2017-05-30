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

# Responsible for building content for signed directory.
class DirectoryContentBuilder
  def initialize(
      expire_date,
      hash_calculator,
      generation_timestamp,
      allowed_content_identifiers = nil)
    Rails.logger.debug(
        "Created directory content builder with expire date '#{expire_date}' "\
        "and hash algorithm URI: '#{hash_calculator.getAlgoURI()}'")

    raise "Expire date must be of type Time" unless expire_date.is_a?(Time)

    @expire_date = expire_date
    @hash_calculator = hash_calculator
    @generation_timestamp = generation_timestamp
    @allowed_content_identifiers = allowed_content_identifiers
  end

  def build(data_boundary)
    content_lines = []
    content_lines << "--#{data_boundary}"
    content_lines << "Expire-date: "\
        "#{@expire_date.utc().strftime "%Y-%m-%dT%H:%M:%SZ"}"
    content_lines << ""

    DistributedFiles.get_all.each do |distributed_file|
      next if !can_add_file?(distributed_file)

      file_data = distributed_file.file_data
      content_identifier = distributed_file.content_identifier

      Rails.logger.debug("Writing distributed file into directory:\n"\
          "\tContent identifier: #{content_identifier}\n"\
          "\tFile size: #{file_data.size()} bytes\n"\
          "\tFile generated at: #{distributed_file.file_updated_at}\n")

      content_lines << "--#{data_boundary}"
      content_lines << "Content-type: application/octet-stream"
      content_lines << "Content-transfer-encoding: base64"
      content_lines << "Content-identifier: #{content_identifier}; "\
          "instance='#{SystemParameter.instance_identifier}'"
      content_lines <<
          "Content-location: /#@generation_timestamp/#{distributed_file.file_name}"
      content_lines << "Hash-algorithm-id: #{@hash_calculator.getAlgoURI()}"
      content_lines << ""
      content_lines << @hash_calculator.calculateFromBytes(
          file_data.to_java_bytes())
    end

    content_lines << "--#{data_boundary}"

    directory = content_lines.join("\n")
    Rails.logger.debug("Generated directory content:\n#{directory}\n")

    return directory
  end

  def can_add_file?(dist_file)
    return true if @allowed_content_identifiers == nil

    return @allowed_content_identifiers.include?(dist_file.content_identifier)
  end
end
