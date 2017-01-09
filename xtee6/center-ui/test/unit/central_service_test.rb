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

class CentralServiceTest < ActiveSupport::TestCase

  def setup
    @service_code = "service_code"
  end

  test "Find first entries" do
    # Given
    query_params = ListQueryParams.new(
        "central_services.service_code", "asc", 0, 2)

    # When
    result = CentralService.get_central_services(query_params)

    # Then
    assert_equal(2, result.size)

    assert_equal("automotive_service", result[0].service_code)
    assert_equal("ballistic_service", result[1].service_code)
  end

  test "Get count" do
    count = CentralService.get_service_count("")
    assert_equal(4, count)
  end

  test "Find last entries" do
    # Given
    query_params = ListQueryParams.new(
        "identifiers.service_code", "desc", 1, 12)

    # When
    result = CentralService.get_central_services(query_params)

    # Then
    assert_equal(3, result.size)

    assert_equal("automotive_service", result[0].service_code)
    assert_equal("ceramic_service", result[1].service_code)
  end

  test "Find searchable entries" do
    # Given
    query_params = ListQueryParams.new(
        "central_services.service_code", "asc", 0, 12, "kosmodroom")

    # When
    result = CentralService.get_central_services(query_params)

    # Then
    assert_equal(1, result.size)
    assert_equal("ballistic_service", result[0].service_code)
  end

  test "Save central service" do
    # Given
    target_service = {
      :code => "target_service_code",
      :version => "v1"
    }

    member_code = "member_in_vallavalitsused"
    subsystem_code = "subsystem_out_of_vallavalitsused"

    provider_id = ClientId.from_parts(
        "EE",
        "riigiasutus",
        member_code,
        subsystem_code
    )

    # When
    CentralService.save(@service_code, target_service, provider_id)

    # Then
    central_service = CentralService.where(:service_code => @service_code)[0]
    target_service_id = central_service.target_service

    assert_equal(member_code, target_service_id.member_code)
    assert_equal(subsystem_code, target_service_id.subsystem_code)
    assert_equal("target_service_code", target_service_id.service_code)
    assert_equal("v1", target_service_id.service_version)
  end

  test "Save central service without target service" do
    # Given
    empty_service_code = "emptyServiceCode"

    # When
    CentralService.save(empty_service_code, nil, nil)

    # Then
    central_service =
        CentralService.where(:service_code => empty_service_code)[0]

    assert_not_nil(central_service)
  end

  test "Should fail when trying to save service with non-existent provider" do
    # Given
    target_service = {:code => "target_service_code"}
    member_code = "member_in_vallavalitsused"
    subsystem_code = "subsystem_out_of_vallavalitsused"

    provider_id = ClientId.from_parts(
        "EE",
        "olematu_klass",
        member_code,
        subsystem_code
    )

    # When/then
    assert_raises(RuntimeError) do
      CentralService.save(@service_code, target_service, provider_id)
    end
  end

  test "Should fail when trying to save service with no service code" do
    # Given
    target_service = {:code => "another_target_service"}
    member_code = "member_out_of_vallavalitsused"
    subsystem_code = "subsystem_in_vallavalitsused"

    provider_id = ClientId.from_parts(
        "EE",
        "riigiasutus",
        member_code,
        subsystem_code
    )

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      CentralService.save("", target_service, provider_id)
    end
  end

  test "Delete central service" do
    # When
    CentralService.delete(@service_code)

    # Then
    central_services = CentralService.where(:service_code => @service_code)
    assert_equal(0, central_services.size)
  end
end
