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

class ApprovedTsaTest < ActiveSupport::TestCase

  test "Should not let save TSP with no URL" do
    # Given
    tsp = ApprovedTsa.new()

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      tsp.save!
    end
  end

  test "Should not let save TSP with invalid URL" do
    # Given
    tsp = ApprovedTsa.new()
    tsp.url = "Random string"

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      tsp.save!
    end
  end

  test "Should not let save TSP without cert" do
    # Given
    tsp = ApprovedTsa.new()
    tsp.url = "http://www.cyber.ee"

    # When/then
    error = assert_raises(ActiveRecord::RecordInvalid) do
      tsp.save!
    end
  end

  test "Should not let save TSP with invalid cert" do
    # Given
    tsp = ApprovedTsa.new()
    tsp.url = "http://www.cyber.ee"
    tsp.cert = "arbitrary String"

    # When/then
    assert_raises(OpenSSL::X509::CertificateError) do
      tsp.save!
    end
  end

  test "Should save approved TSP correctly" do
    # Given
    tsp = ApprovedTsa.new()
    tsp.url = "http://www.cyber.ee"
    tsp.cert = read_cert_ca1()

    # When
    tsp.save!

    # Then
    saved_tsp = ApprovedTsa.where(:url => "http://www.cyber.ee").first
    assert_not_nil(saved_tsp.cert)
    assert_not_nil(saved_tsp.valid_from)
    assert_not_nil(saved_tsp.valid_to)
  end

  test "Should save two TSPs with same urls but different certs" do
    # Given
    common_url = "http://www.sameurldifferentcert.ee"
    tsp1 = ApprovedTsa.new()
    tsp1.url = common_url
    tsp1.cert = read_cert_ca1()

    tsp2 = ApprovedTsa.new()
    tsp2.url = common_url
    tsp2.cert = read_cert_ca2()

    # When
    tsp1.save!
    tsp2.save!

    # Then
    saved_tsps = ApprovedTsa.where(:url => common_url)
    assert_equal(2, saved_tsps.size)
  end

  test "Should save two TSPs with same certs but different urls" do
    # Given
    first_url = "http://www.samecerturl1.ee"
    tsp1 = ApprovedTsa.new()
    tsp1.url = first_url
    tsp1.cert = read_cert_ca1()

    second_url = "http://www.samecerturl2.ee"
    tsp2 = ApprovedTsa.new()
    tsp2.url = second_url
    tsp2.cert = read_cert_ca1()

    # When
    tsp1.save!
    tsp2.save!

    # Then
    saved_tsps = ApprovedTsa.where(:url => [first_url, second_url])
    assert_equal(2, saved_tsps.size)
  end

  test "Should not let save TSPS when cert and url are same" do
    # Given
    common_url = "http://www.sameurlandcert.ee"
    tsp1 = ApprovedTsa.new()
    tsp1.url = common_url
    tsp1.cert = read_cert_ca1()

    tsp2 = ApprovedTsa.new()
    tsp2.url = common_url
    tsp2.cert = read_cert_ca1()

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      tsp1.save!
      tsp2.save!
    end
  end

  test "Should not let change cert for already existing approved TSP" do
    # Given
    tsp = ApprovedTsa.new()
    tsp.url = "http://www.cyber.ee"
    tsp.cert = read_cert_ca1()
    tsp.save!

    saved_tsp = ApprovedTsa.where(:url => "http://www.cyber.ee").first
    saved_tsp.cert = read_cert_ca2()

    # When/then
    assert_raises(ActiveRecord::RecordInvalid) do
      saved_tsp.save!
    end
  end

  test "Should give all tsps in order of urls" do
    # Given
    first_tsp = ApprovedTsa.new()
    first_tsp.url = "http://www.url2.com"
    first_tsp.cert = read_cert_ca1()
    first_tsp.save!

    second_tsp = ApprovedTsa.new()
    second_tsp.url = "http://www.url1.com"
    second_tsp.cert = read_cert_ca2()
    second_tsp.save!

    query_params = ListQueryParams.new(
        "approved_tsas.url","asc", 0, 10)

    # When
    tsps = ApprovedTsa.get_approved_tsas(query_params)

    # Then
    assert_equal(2, tsps.size)
    assert_equal("http://www.url1.com", tsps[0].url)
    assert_equal("http://www.url2.com", tsps[1].url)
  end
end
