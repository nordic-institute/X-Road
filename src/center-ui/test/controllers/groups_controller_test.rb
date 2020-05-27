#
# The MIT License
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

class GroupsControllerTest < ActionController::TestCase
  def setup
    @tyhigrupp_id = ActiveRecord::Fixtures.identify(:tyhigrupp)
  end

  test "Sort addable members by name" do
    # Given
    params = {
      'groupId' => @tyhigrupp_id,
      'iDisplayStart' => 0,
      'iDisplayLength' => 100,
      'iSortCol_0' => 0,
      'sEcho' => 1,
      'sSearch' => "",
      'sSortDir_0' => "asc",
      'showMembersInSearchResult' => false,
      'skipFillTable' => false
    }

    # When
    get(:addable_members, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)

    assert_equal("1", response_as_json["sEcho"])
    assert_equal(6, response_as_json["iTotalDisplayRecords"])
    assert_equal(6, response_as_json["iTotalRecords"])
    assert_equal(6, response_as_json["aaData"].size())
  end

  # XROAD column
  test "Sort by column 4" do
    # Given
    params = {
      'groupId' => @tyhigrupp_id,
      'iDisplayStart' => 0,
      'iDisplayLength' => 100,
      'iSortCol_0' => 4, # Important here!
      'sEcho' => 1,
      'sSearch' => "",
      'sSortDir_0' => "asc",
      'showMembersInSearchResult' => false,
      'skipFillTable' => false
    }

    # When
    get(:addable_members, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)

    assert_equal("1", response_as_json["sEcho"])
  end

  # Type column
  test "Sort by column 5" do
    # Given
    params = {
      'groupId' => @tyhigrupp_id,
      'iDisplayStart' => 0,
      'iDisplayLength' => 100,
      'iSortCol_0' => 5, # Important here!
      'sEcho' => 1,
      'sSearch' => "",
      'sSortDir_0' => "asc",
      'showMembersInSearchResult' => false,
      'skipFillTable' => false
    }

    # When
    get(:addable_members, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)

    assert_equal("1", response_as_json["sEcho"])
  end

  test "Get existing group members list" do
    # Given
    params = {
      'groupId' => ActiveRecord::Fixtures.identify(:vallavalitsused),
      'iDisplayLength' => 100,
      'iDisplayStart' => 0,
      'iSortCol_0' => 6,
      'sEcho' => 1,
      'sSearch' => "",
      'sSortDir_0' => 'desc'
    }

    # When
    get(:group_members, params)

    # Then
    assert_response(:success)

    response_as_json = JSON.parse(response.body)
    assert_equal("1", response_as_json["sEcho"])
  end

  test "Remove members found with advanced search" do
    # Given
    advanced_search_params = {
      :name => "This member should NOT belong to group 'vallavalitsused'",
      :memberCode => "member_out_of_vallavalitsused",
      :memberClass => "riigiasutus",
      :subsystem => "subsystem_in_vallavalitsused",
      :xRoadInstance => "EE",
      :objectType => "SUBSYSTEM"
    }.to_json

    req_params = {
      'groupId' => ActiveRecord::Fixtures.identify(:vallavalitsused),
      'searchable' => "",
      'advancedSearchParams' => advanced_search_params
    }

    # When
    post(:remove_matching_members, req_params)

    # Then
    deleted_member_id = ActiveRecord::Fixtures.identify(
        :subsystem_in_vallavalitsused_group_member_identifier)

    member_count = GlobalGroupMember.where(:id => deleted_member_id).count
    assert_equal(
        0,
        member_count,
        "This member should be deleted as a result of the test")
  end

  test "Remove all members from global group" do
    # Given
    group_id = ActiveRecord::Fixtures.identify(:vallavalitsused)
    params = {
      'groupId' => group_id,
      'searchable' => ""
    }

    # When
    post(:remove_matching_members, params)

    # Then
    member_count = GlobalGroupMember.where(:global_group_id => group_id).count
    assert_equal(0, member_count, "There should be no members")
  end
end
