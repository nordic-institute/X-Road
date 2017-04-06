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

require 'test_helper'
require "test/unit/assertions"
include Test::Unit::Assertions


# These database-related tests must be run against one or more nodes of an
# initialized cluster of central servers.
# The tests depend on existing data;
# all changes made inside the tests will be rolled back.
class HaSupportTest < ActiveSupport::TestCase

  test "The name of the database node must be a non-empty string" do
    assert(!CommonSql.ha_node_name.empty?)
  end

  test "There must be more than one HA node available" do
    assert(CommonSql.available_ha_nodes.length > 1)
  end

  test "A central server address must correspond to each node" do
    CommonSql.available_ha_nodes.each do |node_name|
      assert_equal(1, SystemParameter.where(
          :key => SystemParameter::CENTRAL_SERVER_ADDRESS,
          :ha_node_name => node_name).length)
    end
  end

end
