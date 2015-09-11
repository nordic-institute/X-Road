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
