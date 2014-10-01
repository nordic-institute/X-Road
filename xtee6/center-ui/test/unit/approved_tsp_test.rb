require 'test_helper'

class ApprovedTspTest < ActiveSupport::TestCase

  test "Should not let save TSP with no URL" do
    # Given
    tsp = ApprovedTsp.new()

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      tsp.save!
    end
  end

  test "Should not let save TSP with invalid URL" do
    # Given
    tsp = ApprovedTsp.new()
    tsp.url = "Random string"

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      tsp.save!
    end
  end

  test "Should not let save TSP without cert" do
    # Given
    tsp = ApprovedTsp.new()
    tsp.url = "http://www.cyber.ee"

    # When/then
    error = assert_raises(ActiveRecord::RecordInvalid) do
      tsp.save!
    end
  end

  test "Should not let save TSP with invalid cert" do
    # Given
    tsp = ApprovedTsp.new()
    tsp.url = "http://www.cyber.ee"
    tsp.cert = "arbitrary String"

    # When/then
    assert_raises(OpenSSL::X509::CertificateError) do
      tsp.save!
    end
  end

  test "Should save approved TSP correctly" do
    # Given
    tsp = ApprovedTsp.new()
    tsp.url = "http://www.cyber.ee"
    tsp.cert = read_admin_ca1_cert()

    # When
    tsp.save!

    # Then
    saved_tsp = ApprovedTsp.where(:url => "http://www.cyber.ee").first
    assert_not_nil(saved_tsp.cert)
    assert_not_nil(saved_tsp.valid_from)
    assert_not_nil(saved_tsp.valid_to)
  end

  test "Should save two TSPs with same urls but different certs" do
    # Given
    common_url = "http://www.sameurldifferentcert.ee"
    tsp1 = ApprovedTsp.new()
    tsp1.url = common_url
    tsp1.cert = read_admin_ca1_cert()

    tsp2 = ApprovedTsp.new()
    tsp2.url = common_url
    tsp2.cert = read_admin_ca2_cert()

    # When
    tsp1.save!
    tsp2.save!

    # Then
    saved_tsps = ApprovedTsp.where(:url => common_url)
    assert_equal(2, saved_tsps.size)
  end

  test "Should save two TSPs with same certs but different urls" do
    # Given
    first_url = "http://www.samecerturl1.ee"
    tsp1 = ApprovedTsp.new()
    tsp1.url = first_url
    tsp1.cert = read_admin_ca1_cert()

    second_url = "http://www.samecerturl2.ee"
    tsp2 = ApprovedTsp.new()
    tsp2.url = second_url
    tsp2.cert = read_admin_ca1_cert()

    # When
    tsp1.save!
    tsp2.save!

    # Then
    saved_tsps = ApprovedTsp.where(:url => [first_url, second_url])
    assert_equal(2, saved_tsps.size)
  end

  test "Should not let save TSPS when cert and url are same" do
    # Given
    common_url = "http://www.sameurlandcert.ee"
    tsp1 = ApprovedTsp.new()
    tsp1.url = common_url
    tsp1.cert = read_admin_ca1_cert()

    tsp2 = ApprovedTsp.new()
    tsp2.url = common_url
    tsp2.cert = read_admin_ca1_cert()

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      tsp1.save!
      tsp2.save!
    end
  end

  test "Should not let change cert for already existing approved TSP" do
    # Given
    tsp = ApprovedTsp.new()
    tsp.url = "http://www.cyber.ee"
    tsp.cert = read_admin_ca1_cert()
    tsp.save!

    saved_tsp = ApprovedTsp.where(:url => "http://www.cyber.ee").first
    saved_tsp.cert = read_admin_ca2_cert()

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      saved_tsp.save!
    end
  end

  test "Should give all tsps in order of urls" do
    # Given
    first_tsp = ApprovedTsp.new()
    first_tsp.url = "http://www.url2.com"
    first_tsp.cert = read_admin_ca1_cert()
    first_tsp.save!

    second_tsp = ApprovedTsp.new()
    second_tsp.url = "http://www.url1.com"
    second_tsp.cert = read_admin_ca2_cert()
    second_tsp.save!

    query_params = ListQueryParams.new(
        "approved_tsps.url","asc", 0, 10)

    # When
    tsps = ApprovedTsp.get_approved_tsps(query_params)

    # Then
    assert_equal(2, tsps.size)
    assert_equal("http://www.url1.com", tsps[0].url)
    assert_equal("http://www.url2.com", tsps[1].url)
  end
end
