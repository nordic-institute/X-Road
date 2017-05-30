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

class SecurityServerClientTest < ActiveSupport::TestCase

  def setup
    @group_id = ActiveRecord::Fixtures.identify(:vallavalitsused)
  end

  test "Advanced search with existing member name" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_clients.name","asc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new(
        {:name =>"This member should belong to group 'vallavalitsused'"})

    # When
    result = GlobalGroupMember.
        get_group_members(@group_id, query_params, advanced_search_params)

    # Then
    assert_equal(1, result.size)

    member = result[0]
    member_id = member.group_member

    assert_equal("member_in_vallavalitsused", member_id.member_code)
    assert_equal("MEMBER", member_id.object_type)
  end

  test "Advanced search members count with existing member name" do
    # Given
    advanced_search_params = AdvancedSearchParams.new(
        {:name =>"This member should belong to group 'vallavalitsused'"})

    # When
    result = GlobalGroupMember.
        get_group_member_count(@group_id, advanced_search_params)

    # Then
    assert_equal(1, result)
  end

  test "Advanced search with non-existing member name" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_clients.name","asc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new(
        {:name => "in_vallavalitsused"})

    # When
    result = GlobalGroupMember.
        get_group_members(@group_id, query_params, advanced_search_params)

    # Then
    assert_equal(0, result.size)
  end

  test "Advanced search members count with non-existing member name" do
    # Given
    advanced_search_params = AdvancedSearchParams.new(
        {:name => "in_vallavalitsused"})

    # When
    result = GlobalGroupMember.
        get_group_member_count(@group_id, advanced_search_params)

    # Then
    assert_equal(0, result)
  end

  test "Advanced search existing entry with all fields filled" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_clients.name","asc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new(
      {
        # Case intentionally altered
        :name => "THis member should nOt belong to group 'vallavalitsused'",
        :xroad_instance => "EE",
        :member_class => "riigi", #Intentionally abbreviated
        :member_code => "member_out_of_vallavalitsused",
        :subsytem_code => "subsystem_in_vallavalitsused",
        :object_type => "SUBSYSTEM"
      }
    )

    # When
    result = GlobalGroupMember.
        get_group_members(@group_id, query_params, advanced_search_params)

    # Then
    assert_equal(1, result.size)

    member_id = result[0].group_member

    assert_equal("subsystem_in_vallavalitsused", member_id.subsystem_code)
  end

  test "Advanced search non-existing entry with all fields filled" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_clients.name","asc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new(
      {
        :name => "This member should NOT belong to group 'vallavalitsused'",
        :xroad_instance => "LV", # This one should be not found
        :member_class => "riigiasutus",
        :member_code => "member_out_of_vallavalitsused",
        :subsystem_code => "subsystem_in_vallavalitsused",
        :object_type => "SUBSYSTEM"
      }
    )

    # When
    result = GlobalGroupMember.
        get_group_members(@group_id, query_params, advanced_search_params)

    # Then
    assert_equal(0, result.size)
  end

  test "Advanced search existing entry with two fields filled" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_clients.name","asc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new(
      {
        :member_code => "member_out_of_vallavalitsused",
        :subsystem_code => "subsystem_in_vallavalitsused",
      }
    )

    # When
    result = GlobalGroupMember.
        get_group_members(@group_id, query_params, advanced_search_params)

    # Then
    assert_equal(1, result.size)

    member_id = result[0].group_member

    assert_equal("subsystem_in_vallavalitsused", member_id.subsystem_code)
  end

  test "Advanced search non-existing entry with two fields filled" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_clients.name","asc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new(
      {
        :member_code => "dummy", # Should not exist
        :subsystem_code =>"subsystem_in_vallavalitsused",
      }
    )

    # When
    result = GlobalGroupMember.
        get_group_members(@group_id, query_params, advanced_search_params)

    # Then
    assert_equal(0, result.size)
  end

  test "Advanced search finds all entries when all parameters blank" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_clients.name","asc", 0, 10)

    advanced_search_params = AdvancedSearchParams.new()

    # When
    result = GlobalGroupMember.
        get_group_members(@group_id, query_params, advanced_search_params)

    # Then
    assert_equal(2, result.size)
  end

  test "Verify if simple search works" do
    # Given
    query_params = ListQueryParams.new(
        "security_server_clients.name","asc", 0, 10, "riigias")

    # When
    result = GlobalGroupMember.
        get_group_members(@group_id, query_params)

    # Then
    assert_equal(2, result.size)
  end
end