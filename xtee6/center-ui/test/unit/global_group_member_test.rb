require 'test_helper'

class SecurityServerClientTest < ActiveSupport::TestCase

  def setup
    @group_id = ActiveRecord::Fixtures.identify(:vallavalitsused)
  end

  def members_count
    GlobalGroupMember.where(:global_group_id => @group_id).count
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

  test "Delete members found with search" do
    # Given
    searchable = "subsystem_in_vallavalitsused"
    members_before = members_count

    # When
    removed_member_ids =
        GlobalGroupMember.remove_matching_members(@group_id, searchable)

    # Then
    members_after = members_count
    members_less = members_before - members_after

    removed_members_expected_no = 1
    assert_equal(removed_members_expected_no, members_less)
    assert_equal(removed_members_expected_no, removed_member_ids.size)

    assert_equal(searchable, removed_member_ids[0].subsystem_code)

    group = GlobalGroup.find(@group_id)
    assert_equal(members_after, group.member_count)
  end
end
