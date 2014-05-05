require 'test_helper'
require 'date'
require 'openssl'

class PkiTest < ActiveSupport::TestCase

  # Writing tests - start

  test "Raise SdsbArgumentError if no topCaData provided" do
    # Given
    pki = Pki.new()
    pki.authentication_only = false
    pki.name_extractor_member_class = "riigiasutus"
    pki.name_extractor_method_name = "ee.cyber.sdsb.Extractor.extract"

    # When/Then
    error = assert_raises(ActiveRecord::RecordInvalid) do
      pki.save!
    end
  end

  test "Raise SdsbArgumentError if no nameExtractorMethodName provided" do
    # Given
    pki = Pki.new()
    pki.authentication_only = false
    pki.name_extractor_member_class = "riigiasutus"
    pki.name_extractor_method_name = nil

    top_ca = CaInfo.new()
    top_ca.cert = read_admin_ca1_cert()
    pki.top_ca = top_ca

    # When/Then
    error = assert_raises(SdsbArgumentError) do
      pki.save!
    end
    assert_equal(:no_name_extractor_method, error.type)
  end

  test "Raise OpenSSL::OpenSSLError if cert invalid" do
    # Given
    pki = Pki.new()
    pki.authentication_only = false
    pki.name_extractor_member_class = "riigiasutus"
    pki.name_extractor_method_name = "ee.cyber.sdsb.Extractor.extract"

    top_ca = CaInfo.new()
    top_ca.cert = "invalidcert"
    pki.top_ca = top_ca

    # When/Then
    assert_raises(OpenSSL::X509::CertificateError) do
      pki.save!
    end
  end

  test "Save PKI successfully" do
    # Given
    pki = Pki.new()
    pki.authentication_only = false
    pki.name_extractor_member_class = "riigiasutus"
    pki.name_extractor_method_name = "ee.cyber.sdsb.Extractor.saveSuccessfully"

    first_top_ca_ocsp_info = OcspInfo.new()
    first_top_ca_ocsp_info.url = "http://www.ocsp1.ee"
    first_top_ca_ocsp_info.cert = "cert1"

    second_top_ca_ocsp_info = OcspInfo.new()
    second_top_ca_ocsp_info.url = "http://www.ocsp2.ee"
    second_top_ca_ocsp_info.cert = "cert2"

    top_ca = CaInfo.new()
    top_ca.cert = read_admin_ca1_cert()
    top_ca.ocsp_infos = [first_top_ca_ocsp_info, second_top_ca_ocsp_info]
    pki.top_ca = top_ca

    first_intermediate_ca_ocsp_info = OcspInfo.new()
    first_intermediate_ca_ocsp_info.url = "http://www.intermocsp1.ee"
    first_intermediate_ca_ocsp_info.cert = "certi1"

    first_intermediate_ca = CaInfo.new()
    first_intermediate_ca.cert = read_admin_ca2_cert()
    first_intermediate_ca.ocsp_infos = [first_intermediate_ca_ocsp_info]

    second_intermediate_ca_ocsp_info = OcspInfo.new()
    second_intermediate_ca_ocsp_info.url = "http://www.intermocsp2.ee"
    second_intermediate_ca_ocsp_info.cert = "certi2"

    second_intermediate_ca = CaInfo.new()
    second_intermediate_ca.cert = read_admin_ca2_cert()
    second_intermediate_ca.ocsp_infos = [second_intermediate_ca_ocsp_info]

    pki.intermediate_cas = [first_intermediate_ca, second_intermediate_ca]

    # When
    pki.save!

    # Then
    saved = Pki.where(:name => "/CN=AdminCA1/O=EJBCA Sample/C=SE").first()

    assert_equal("ee.cyber.sdsb.Extractor.saveSuccessfully",
        saved.name_extractor_method_name)

    assert_equal("riigiasutus", saved.name_extractor_member_class)

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
    pki = Pki.new()
    pki.authentication_only = true

    top_ca = CaInfo.new()
    top_ca.cert = read_admin_ca1_cert()
    pki.top_ca = top_ca

    # Fault: no cert!
    intermediate_ca = CaInfo.new()
    intermediate_ca.cert = nil
    pki.intermediate_cas = [intermediate_ca]

    # When/then
    error = assert_raises(SdsbArgumentError) do
      pki.save!
    end

    assert_equal(:no_ca_cert, error.type)
  end

  # Writing tests - end

  # Updating tests - start

  test "Update name extractor from ordinary to authOnly" do
    # Given
    pki = Pki.new()
    pki.authentication_only = false
    pki.name_extractor_member_class = "riigiasutus"
    pki.name_extractor_method_name = 
        "ee.cyber.sdsb.Extractor.extractorToAuthOnly"

    top_ca = CaInfo.new()
    top_ca.cert = read_admin_ca1_cert()
    pki.top_ca = top_ca
    pki.save!

    pki_to_update = Pki.where(
      :name_extractor_method_name =>
      "ee.cyber.sdsb.Extractor.extractorToAuthOnly").first()

    # When
    pki.authentication_only = true
    pki.save!

    # Then
    updated_pki = Pki.where(:name => "/CN=AdminCA1/O=EJBCA Sample/C=SE").first()

    assert(updated_pki.authentication_only)
    assert_nil(updated_pki.name_extractor_member_class)
    assert_nil(updated_pki.name_extractor_method_name)
  end

  # Updating tests - end

  # Reading tests - start

  test "Get all entries" do
    # Given
    query_params = ListQueryParams.new(
        "pkis.name","asc", 0, 10)
        
    # When
    pkis = Pki.get_pkis(query_params)

    # Then
    assert_equal(3, pkis.size)
    assert_equal("Autosid valmistatakse siin", pkis[0].name)
    assert_equal("Balletti tehakse siin", pkis[1].name)
    assert_equal("C keeles midagi tehakse siin", pkis[2].name)
  end

  test "Get all pkis count" do
    # Given/When
    count = Pki.get_pkis_count("")

    # Then
    assert_equal(3, count)
  end

  test "Find one entry with offset" do
    # Given
    query_params = ListQueryParams.new(
        "pkis.name","desc", 1, 10, "tehakse")

    # When
    pkis = Pki.get_pkis(query_params)

    # Then
    assert_equal(1, pkis.size)
    assert_equal("Balletti tehakse siin", pkis[0].name)
  end

  # Reading tests - end
end
