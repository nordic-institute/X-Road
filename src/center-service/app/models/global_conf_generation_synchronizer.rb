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

# Guarantees that there will always be one global conf generation
# at a time.
class GlobalConfGenerationSynchronizer
  GLOBAL_CONF_GENERATION_LOCKFILE = "global_conf_generation_lockfile"

  # Takes generation operation as a block
  def self.generate
    lock = try_globalconf_lock
    report_generation_in_progress unless lock

    yield
  ensure
    release_globalconf_lock(lock) if lock
  end

  private

  def self.try_globalconf_lock
    return CommonUi::IOUtils.try_lock(GLOBAL_CONF_GENERATION_LOCKFILE)
  end

  def self.release_globalconf_lock(lockfile)
    CommonUi::IOUtils.release_lock(lockfile)
  end

  def self.report_generation_in_progress
    raise "Global configuration is currently being generated, "\
        "parallel generations are not allowed."
  end
end
