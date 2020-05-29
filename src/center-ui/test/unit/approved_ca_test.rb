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
require 'date'
require 'openssl'

class ApprovedCaTest < ActiveSupport::TestCase
  CN_CERT_CA1 = "AAA-central-external"
  CERT_PROFILE_INTERFACE_QNAME = "ee.ria.xroad.common.certificateprofile.impl."\
        "EjbcaCertificateProfileInfoProvider"

  # Writing tests - start

  test "Raise XRoadArgumentError if no topCaData provided" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false
    approved_ca.cert_profile_info = CERT_PROFILE_INTERFACE_QNAME

    # When/Then
    error = assert_raises(ActiveRecord::RecordInvalid) do
      approved_ca.save!
    end
  end

  test "Raise RecordInvalid if no certificate profile info provided" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false

    top_ca = CaInfo.new()
    top_ca.cert = read_cert_ca1()
    approved_ca.top_ca = top_ca

    # When/Then
    error = assert_raises(ActiveRecord::RecordInvalid) do
      approved_ca.save!
    end

    assert(error.message.include?(
        I18n.t("activerecord.attributes.approved_ca.cert_profile_info")))
  end

  test "Raise OpenSSL::OpenSSLError if cert invalid" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false
    approved_ca.cert_profile_info = CERT_PROFILE_INTERFACE_QNAME

    top_ca = CaInfo.new()
    top_ca.cert = "invalidcert"
    approved_ca.top_ca = top_ca

    # When/Then
    error = assert_raises(RuntimeError) do
      approved_ca.save!
    end

    assert_equal(I18n.t("validation.invalid_cert"), error.message)
  end

  test "Save approved ca successfully" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false
    approved_ca.cert_profile_info = CERT_PROFILE_INTERFACE_QNAME

    first_top_ca_ocsp_info = OcspInfo.new()
    first_top_ca_ocsp_info.url = "http://www.ocsp1.ee"
    first_top_ca_ocsp_info.cert = "cert1"

    second_top_ca_ocsp_info = OcspInfo.new()
    second_top_ca_ocsp_info.url = "http://www.ocsp2.ee"
    second_top_ca_ocsp_info.cert = "cert2"

    top_ca = CaInfo.new()
    top_ca.cert = read_cert_ca1()
    top_ca.ocsp_infos = [first_top_ca_ocsp_info, second_top_ca_ocsp_info]
    approved_ca.top_ca = top_ca

    first_intermediate_ca_ocsp_info = OcspInfo.new()
    first_intermediate_ca_ocsp_info.url = "http://www.intermocsp1.ee"
    first_intermediate_ca_ocsp_info.cert = "certi1"

    first_intermediate_ca = CaInfo.new()
    first_intermediate_ca.cert = read_cert_ca2()
    first_intermediate_ca.ocsp_infos = [first_intermediate_ca_ocsp_info]

    second_intermediate_ca_ocsp_info = OcspInfo.new()
    second_intermediate_ca_ocsp_info.url = "http://www.intermocsp2.ee"
    second_intermediate_ca_ocsp_info.cert = "certi2"

    second_intermediate_ca = CaInfo.new()
    second_intermediate_ca.cert = read_cert_ca2()
    second_intermediate_ca.ocsp_infos = [second_intermediate_ca_ocsp_info]

    approved_ca.intermediate_cas = [first_intermediate_ca, second_intermediate_ca]

    # When
    approved_ca.save!

    # Then
    saved = ApprovedCa.where(:name => CN_CERT_CA1).first()

    assert_equal(CERT_PROFILE_INTERFACE_QNAME, saved.cert_profile_info)

    top_ca = saved.top_ca
    assert_not_nil(top_ca.cert)
    assert_not_nil(top_ca.valid_from)
    assert_not_nil(top_ca.valid_to)

    ocsp_infos = top_ca.ocsp_infos
    assert_equal(2, ocsp_infos.size)
    assert_equal("http://www.ocsp1.ee", ocsp_infos[0].url)
    assert_equal("http://www.ocsp2.ee", ocsp_infos[1].url)

    intermediate_cas = saved.intermediate_cas
    assert_equal(2, intermediate_cas.size)

    first_intermediate_ca = intermediate_cas[0]
    second_intermediate_ca = intermediate_cas[1]
    assert_equal(1, first_intermediate_ca.ocsp_infos.size)
    assert_equal(1, second_intermediate_ca.ocsp_infos.size)
    assert_equal("http://www.intermocsp1.ee",
        first_intermediate_ca.ocsp_infos[0].url)
    assert_equal("http://www.intermocsp2.ee",
        second_intermediate_ca.ocsp_infos[0].url)
  end

  test "Raise XRoadArgumentError if no cert for intermediate CA present" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = true
    approved_ca.cert_profile_info = CERT_PROFILE_INTERFACE_QNAME

    top_ca = CaInfo.new()
    top_ca.cert = read_cert_ca1()
    approved_ca.top_ca = top_ca

    # Fault: no cert!
    intermediate_ca = CaInfo.new()
    intermediate_ca.cert = nil
    approved_ca.intermediate_cas = [intermediate_ca]

    # When/then
    error = assert_raises(XRoadArgumentError) do
      approved_ca.save!
    end

    assert_equal(:no_ca_cert, error.type)
  end

  # Writing tests - end

  # Reading tests - start

  test "Get all entries" do
    # Given
    query_params = ListQueryParams.new(
        "approved_cas.name","asc", 0, 10)

    # When
    approved_cas = ApprovedCa.get_approved_cas(query_params)

    # Then
    assert_equal(3, approved_cas.size)
    assert_equal("Autosid valmistatakse siin", approved_cas[0].name)
    assert_equal("Balletti tehakse siin", approved_cas[1].name)
    assert_equal("C keeles midagi tehakse siin", approved_cas[2].name)
  end

  test "Get all approved cas count" do
    # Given/When
    count = ApprovedCa.get_approved_cas_count("")

    # Then
    assert_equal(3, count)
  end

  test "Find one entry with offset" do
    # Given
    query_params = ListQueryParams.new(
        "approved_cas.name","desc", 1, 10, "tehakse")

    # When
    approved_cas = ApprovedCa.get_approved_cas(query_params)

    # Then
    assert_equal(1, approved_cas.size)
    assert_equal("Balletti tehakse siin", approved_cas[0].name)
  end

  test "Should find approved ca according to datetime" do
    # Given
    query_params = ListQueryParams.new(
        "approved_cas.name","desc", 0, 10, "2014-08-14 14:14:14")

    # When
    approved_cas = ApprovedCa.get_approved_cas(query_params)

    # Then
    assert_equal(1, approved_cas.size)
    assert_equal("C keeles midagi tehakse siin", approved_cas[0].name)
  end

  # Reading tests - end
end
