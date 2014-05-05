require 'test_helper'

class PkisControllerTest < ActionController::TestCase

  test "Should change name extractor data for PKI" do
    # Given
    editable_pki_id = save_initial_pki()

    pki_param = get_pki_param(editable_pki_id)

    # When
    post(:edit_existing_pki, {'pki' => pki_param})

    # Then
    assert_response(:success)

    changed_pki = Pki.find(editable_pki_id)
    assert_equal("eraisik", changed_pki.name_extractor_member_class)
    assert_equal("ee.cyber.sdsb.PkisContollerTest.isChanged",
      changed_pki.name_extractor_method_name)
  end

  test "Should delete existing OCSP info" do
    # Given
    deletable_ocsp_info = OcspInfo.new()
    deletable_ocsp_info.url = "deletable.ocsp.com"

    editable_pki_id = save_initial_pki([deletable_ocsp_info])

    pki_param = get_pki_param(editable_pki_id)

    deletable_ocsp_info_id = 
        OcspInfo.where(:url => "deletable.ocsp.com").first().id

    ocsp_info_param = {
      :delete => [deletable_ocsp_info_id]
    }.to_json()

    # When
    post(:edit_existing_pki,
      {'pki' => pki_param, 'ocspInfos' => ocsp_info_param})

    # Then
    assert_response(:success)

    assert_equal(0, OcspInfo.all().size())
  end

  test "Should change URL and cert of existing OCSP info" do
    # Given
    editable_ocsp_info = OcspInfo.new()
    editable_ocsp_info.url = "editable.ocsp.com"
    editable_ocsp_info.cert = "initialOcspCert"

    editable_pki_id = save_initial_pki([editable_ocsp_info])

    pki_param = get_pki_param(editable_pki_id)

    editable_ocsp_info_id = 
        OcspInfo.where(:url => "editable.ocsp.com").first().id

    ocsp_info_param = {
      :update => [{
        :id => editable_ocsp_info_id,
        :url => "changed.ocsp.com",
        :ocspTempCertId => 0 # Session has only one temp cert
      }]
    }.to_json()

    # When
    post(:edit_existing_pki,
      {'pki' => pki_param, 'ocspInfos' => ocsp_info_param}, 
      {:temp_certs => ["newOcspCert"]})

    # Then
    assert_response(:success)

    affected_ca = Pki.find(editable_pki_id).top_ca
    assert_equal(1, affected_ca.ocsp_infos.size())

    edited_ocsp = affected_ca.ocsp_infos[0]
    assert_equal("changed.ocsp.com", edited_ocsp.url)
    assert_equal("newOcspCert", edited_ocsp.cert)
  end

  test "Should add new OCSP info for existing CA" do
    # Given
    editable_pki_id = save_initial_pki()
    pki_param = get_pki_param(editable_pki_id)

    top_ca_id = Pki.find(editable_pki_id).top_ca.id
    ocsp_info_param = {
      :new => [{
        :caInfoId => top_ca_id,
        :url => "new.ocsp.com",
        :ocspTempCertId => 0 # Session has only one temp cert
      }]
    }.to_json()

    # When
    post(:edit_existing_pki,
      {'pki' => pki_param, 'ocspInfos' => ocsp_info_param}, 
      {:temp_certs => ["newOcspCert"]})

    # Then
    assert_response(:success)

    affected_ca = Pki.find(editable_pki_id).top_ca
    assert_equal(1, affected_ca.ocsp_infos.size())

    added_ocsp = affected_ca.ocsp_infos[0]
    assert_equal("new.ocsp.com", added_ocsp.url)
    assert_equal("newOcspCert", added_ocsp.cert)
  end

  test "Should delete intermediate CA by id" do
    # Given
    intermediate_ca = CaInfo.new()
    intermediate_ca.cert = read_admin_ca2_cert()

    editable_pki_id = save_initial_pki([], [intermediate_ca])
    pki_param = get_pki_param(editable_pki_id)

    deletable_id = get_intermediate_ca_id(editable_pki_id)

    intermediate_cas_param = {
      :delete => [deletable_id]
    }.to_json()

    # When
    post(:edit_existing_pki,
      {'pki' => pki_param, 'intermediateCas' => intermediate_cas_param})

    # Then
    edited_pki = Pki.find(editable_pki_id)
    assert_equal(0, edited_pki.intermediate_cas.size())
  end

  test "Should update cert for existing intermediate CA" do
    # Given
    intermediate_ca = CaInfo.new()
    intermediate_ca.cert = read_admin_ca2_cert()

    editable_pki_id = save_initial_pki([], [intermediate_ca])
    pki_param = get_pki_param(editable_pki_id)

    intermediate_ca_id = get_intermediate_ca_id(editable_pki_id)

    intermediate_cas_param = {
      :update => [{
        :id => intermediate_ca_id,
        :intermediateCaTempCertId => 0 # Session has only one temp cert
      }]
    }.to_json()
    admin_ca1_cert = read_admin_ca1_cert()

    # When
    post(:edit_existing_pki,
      {'pki' => pki_param,  'intermediateCas' => intermediate_cas_param}, 
      {:temp_certs => [admin_ca1_cert]})

    # Then
    assert_response(:success)

    edited_intermediate_ca = CaInfo.find(intermediate_ca_id)
    assert_equal(admin_ca1_cert, edited_intermediate_ca.cert)
  end

  test "Should add new intermediate CA to PKI" do
    # Given
    editable_pki_id = save_initial_pki()
    pki_param = get_pki_param(editable_pki_id)

    intermediate_cas_param = {
      :new => [{
        :ocspInfos => [{
          :url => "intermediateCaNewOcsp.com",
          :ocspTempCertId => 0 # first in session
        }],
        :intermediateCaTempCertId => 1 # second in session
      }]
    }.to_json()

    admin_ca2_cert = read_admin_ca2_cert()

    # When
    post(:edit_existing_pki,
      {'pki' => pki_param,  'intermediateCas' => intermediate_cas_param}, 
      {:temp_certs => ["newIntermediateCaOcspCert", admin_ca2_cert]})

    # Then
    assert_response(:success)

    edited_pki = Pki.find(editable_pki_id)
    assert_equal(1, edited_pki.intermediate_cas.size())

    intermediate_ca = edited_pki.intermediate_cas[0]
    assert_equal(admin_ca2_cert, intermediate_ca.cert)

    assert_equal(1, intermediate_ca.ocsp_infos.size())

    ocsp_info = intermediate_ca.ocsp_infos[0]
    assert_equal("intermediateCaNewOcsp.com", ocsp_info.url)
    assert_equal("newIntermediateCaOcspCert", ocsp_info.cert)
  end

  # Tests for discovered bugs - start

  test "Should save topCA OCSP cert" do
    # Given
    name_extractor_param = {
      :authOnly => true
    }.to_json()

    ocsp_infos_param = [{
      :url => "topcaocsp.com",
      :ocspTempCertId => 1 #Second temp cert is for OCSP
    }].to_json()

    # When
    post(:save_new_pki, 
      {
        "topCaTempCertId" => 0, #First temp cert is topCA cert
        "nameExtractor" => name_extractor_param,
        "topCaOcspInfos" => ocsp_infos_param
      },
      {:temp_certs => [read_admin_ca1_cert(), "topCaOcspCert"]})

    # Then
    saved_ocsp = OcspInfo.where(:url => "topcaocsp.com").first
    assert_equal("topCaOcspCert", saved_ocsp.cert)
  end

  test "Should save OCSP cert to PKI when tempCertId blank" do
    # Given
    name_extractor_param = {
      :authOnly => true
    }.to_json()

    ocsp_infos_param = [{
      :url => "topcaocsp.com",
      :ocspTempCertId => "" # It has been mistakenly turned into 0
    }].to_json()

    # When
    post(:save_new_pki, 
    {
      "topCaTempCertId" => 0, #First temp cert is topCA cert
      "nameExtractor" => name_extractor_param,
      "topCaOcspInfos" => ocsp_infos_param
    },
    {:temp_certs => [read_admin_ca1_cert(), "topCaOcspCert"]})

    # Then
    saved_ocsp = OcspInfo.where(:url => "topcaocsp.com").first
    assert_nil(saved_ocsp.cert)
  end

  # Tests for discovered bugs - start

  private

  def get_intermediate_ca_id(editable_pki_id)
    Pki.find(editable_pki_id).intermediate_cas[0].id
  end

  def get_pki_param(editable_pki_id)
    {
      :id => editable_pki_id,
      :authOnly => false,
      :nameExtractorMemberClass => "eraisik",
      :nameExtractorMethodName => "ee.cyber.sdsb.PkisContollerTest.isChanged"
    }.to_json()
  end

  # Saves PKI to edit to the database and returns id for it.
  def save_initial_pki(top_ca_ocsp_infos = [], intermediate_cas = [])
    pki = Pki.new()
    pki.authentication_only = false
    pki.name_extractor_member_class = "riigiasutus"
    pki.name_extractor_method_name = 
        "ee.cyber.sdsb.PkisContollerTest.getInitialPki"

    top_ca = CaInfo.new()
    top_ca.cert = read_admin_ca1_cert()
    top_ca.ocsp_infos = top_ca_ocsp_infos
    pki.top_ca = top_ca
    pki.intermediate_cas = intermediate_cas
    pki.save!

    Pki.where(:name_extractor_method_name => 
      "ee.cyber.sdsb.PkisContollerTest.getInitialPki").first().id()
  end
end

=begin
BROWSER->SERVER PROTOCOL FOR EDITING (format: JSON):
  pki:
    id:
    authOnly:
    nameExtractorMemberClass:
    nameExtractorMethodName:

  ocspInfos:
    delete: [id1, id2, ...]
    update[]:
      id:
      url:
      ocspTempCertId:
    new[]:
      caInfoId:
      url:
      ocspTempCertId:

  intermediateCas:
    delete: [id1, id2, ...]
    update[]:
      id:
      intermediateCaTempCertId:
    new[]:
      ocspInfos[]:
        url:
        ocspTempCertId:
      intermediateCaTempCertId:

BROWSER->SERVER PROTOCOL FOR ADDING NEW PKI (format: JSON)
  topCaTempCertId:

  nameExtractor:
    authOnly:
    memberClass:
    extractorMethod:

  topCaOcspInfos[]:
    url:
    ocspTempCertId:

  intermediateCas:
    ocspInfos[]:
      url:
      ocspTempCertId:
    intermediateCaTempCertId:
=end
