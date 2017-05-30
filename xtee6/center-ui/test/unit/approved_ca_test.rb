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
require 'date'
require 'openssl'

class ApprovedCaTest < ActiveSupport::TestCase
  # Writing tests - start

  test "Raise XroadArgumentError if no topCaData provided" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false
    approved_ca.identifier_decoder_member_class = "riigiasutus"
    approved_ca.identifier_decoder_method_name = "ee.ria.xroad.Extractor.extract"

    # When/Then
    error = assert_raises(ActiveRecord::RecordInvalid) do
      approved_ca.save!
    end
  end

  test "Raise XroadArgumentError if no nameExtractorMethodName provided" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false
    approved_ca.identifier_decoder_member_class = "riigiasutus"
    approved_ca.identifier_decoder_method_name = nil

    top_ca = CaInfo.new()
    top_ca.cert = read_cert_ca1()
    approved_ca.top_ca = top_ca

    # When/Then
    error = assert_raises(XroadArgumentError) do
      approved_ca.save!
    end
    assert_equal(:no_name_extractor_method, error.type)
  end

  test "Raise OpenSSL::OpenSSLError if cert invalid" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false
    approved_ca.identifier_decoder_member_class = "riigiasutus"
    approved_ca.identifier_decoder_method_name = "ee.ria.xroad.Extractor.extract"

    top_ca = CaInfo.new()
    top_ca.cert = "invalidcert"
    approved_ca.top_ca = top_ca

    # When/Then
    assert_raises(OpenSSL::X509::CertificateError) do
      approved_ca.save!
    end
  end

  test "Save approved ca successfully" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false
    approved_ca.identifier_decoder_member_class = "riigiasutus"
    approved_ca.identifier_decoder_method_name = "ee.ria.xroad.Extractor.saveSuccessfully"

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

    assert_equal("ee.ria.xroad.Extractor.saveSuccessfully",
        saved.identifier_decoder_method_name)

    assert_equal("riigiasutus", saved.identifier_decoder_member_class)

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

  test "Raise RecordInvalid if no cert for intermediate CA present" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = true

    top_ca = CaInfo.new()
    top_ca.cert = read_cert_ca1()
    approved_ca.top_ca = top_ca

    # Fault: no cert!
    intermediate_ca = CaInfo.new()
    intermediate_ca.cert = nil
    approved_ca.intermediate_cas = [intermediate_ca]

    # When/then
    error = assert_raises(XroadArgumentError) do
      approved_ca.save!
    end

    assert_equal(:no_ca_cert, error.type)
  end

  # Writing tests - end

  # Updating tests - start

  test "Update name extractor from ordinary to authOnly" do
    # Given
    approved_ca = ApprovedCa.new()
    approved_ca.authentication_only = false
    approved_ca.identifier_decoder_member_class = "riigiasutus"
    approved_ca.identifier_decoder_method_name =
        "ee.ria.xroad.Extractor.extractorToAuthOnly"

    top_ca = CaInfo.new()
    top_ca.cert = read_cert_ca1()
    approved_ca.top_ca = top_ca
    approved_ca.save!

    approved_ca_to_update = ApprovedCa.where(
      :identifier_decoder_method_name =>
      "ee.ria.xroad.Extractor.extractorToAuthOnly").first()

    # When
    approved_ca.authentication_only = true
    approved_ca.save!

    # Then
    updated_approved_ca = ApprovedCa.where(:name => CN_CERT_CA1).first()

    assert(updated_approved_ca.authentication_only)
    assert_nil(updated_approved_ca.identifier_decoder_member_class)
    assert_nil(updated_approved_ca.identifier_decoder_method_name)
  end

  # Updating tests - end

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
