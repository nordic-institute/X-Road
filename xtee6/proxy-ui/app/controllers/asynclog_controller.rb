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

java_import Java::ee.ria.xroad.common.SystemProperties

class AsynclogController < ApplicationController

  LOG_FILE = SystemProperties.getTempFilesPath() + "/async"
  TIME_FORMAT = "%F %T"

  def index
    # authorize!(:view_async_reqs_log)
    redirect_to :root
  end

  def refresh
    authorize!(:view_async_reqs_log)

    records = []
    bad_lines = []

    i = 0
    File.open(LOG_FILE, "r").each_line do |line|
      split = line.split("\t")

      i += 1
      if split.size != 10
        bad_lines << i
        logger.debug("Invalid log row at line #{i}")
      end

      records << {
        :logged => format_time(split[0]),
        :received => format_time(split[1]),
        :removed => format_time(split[2]),
        :state => split[3],
        :send_attempts => split[4],
        :producer => split[5],
        :sender => split[6],
        :request_id => split[9]
      }
    end if File.exists?(LOG_FILE)

    i = records.size
    records.each do |record|
      record[:no] = i
      i -= 1
    end

    unless bad_lines.empty?
      error("Invalid log rows at lines #{bad_lines}")
    end

    render_json(records)
  end

  private

  def format_time(time)
    time = time.to_i
    time == 0 ? "-" : Time.at(time).strftime(TIME_FORMAT)
  end
end
