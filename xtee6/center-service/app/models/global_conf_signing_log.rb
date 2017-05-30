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

# Responsible of logging last global configuration signing attempt.
class GlobalConfSigningLog

  def self.get_exception_ctx(ex)
    result = "EXCEPTION MESSAGE:\n"
    result << "#{ex.message}\n\n"

    result << "EXCEPTION BACKTRACE:\n"
    result << ex.backtrace.join("\n\t")
    result << "\n"

    return result
  end

  def self.write(writable, file_id, first_line = nil)
    Rails.logger.debug(
        "write_to_log(#{writable}, #{file_id}, #{first_line})")

    log_file_content = ""
    log_file_content << "#{first_line}\n\n" if first_line

    log_file_content << (writable.is_a?(Exception) ?
        get_exception_ctx(writable): writable)

    identifier_mapping_log_file = get_log_file(file_id)

    CommonUi::IOUtils.write(identifier_mapping_log_file, log_file_content)
  end

  private

  def self.get_log_file(file_id)
    return "#{CommonUi::IOUtils.get_log_dir()}/"\
        "xroad_globalconf_signed-#{file_id}"
  end
end
