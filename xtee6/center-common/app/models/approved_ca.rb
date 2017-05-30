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

require "common-ui/cert_utils"

class ApprovedCa < ActiveRecord::Base
  include Validators

  validates_with MaxlengthValidator
  validates :top_ca, :presence => true

  belongs_to :top_ca,
      :class_name => "CaInfo",
      :foreign_key => "top_ca_id",
      :dependent => :destroy,
      :autosave => true

  has_many :intermediate_cas,
      :class_name => "CaInfo",
      :foreign_key => "intermediate_ca_id",
      :dependent => :destroy,
      :autosave => true

  before_save do |approved_ca|
    logger.info("Saving PKI: '#{approved_ca}'")

    approved_ca.prepare_name_extractor()
    approved_ca.prepare_top_ca_cert()
  end

  before_destroy do |approved_ca|
    logger.info("Deleting PKI: '#{approved_ca}'")
  end

  def prepare_name_extractor
    if self.name_extractor_missing?
      raise XroadArgumentError.new(:no_name_extractor_method)
    end

    if self.authentication_only?
      self.identifier_decoder_method_name = nil
      self.identifier_decoder_member_class = nil
    end
  end

  def name_extractor_missing?
    !self.authentication_only? &&
        (!self.identifier_decoder_method_name ||
            self.identifier_decoder_method_name.empty?)
  end

  def prepare_top_ca_cert
    top_ca_cert = self.top_ca.cert

    cert_obj = CommonUi::CertUtils.cert_object(top_ca_cert)
    subject_name = cert_obj.subject.to_s

    unless MaxlengthValidator.string_length_valid?(subject_name)
      raise I18n.t("errors.approved_ca.top_ca_cert_too_long_subject_name", {
          :max_length => Validators::STRING_MAX_LENGTH,
          :subject_name => subject_name})
    end

    self.name = subject_name
  end

  def to_s
    "ApprovedCa(authOnly: '#{self.authentication_only}', nameExtractorMemberClass: "\
      "'#{self.identifier_decoder_member_class}', nameExtractorMethodName: "\
      "'#{self.identifier_decoder_method_name}', id: '#{self.id}', "\
      ", topCa: '#{self.top_ca}', intermediateCas: "\
      "[#{self.intermediate_cas.join(', ')}])"
  end

  def self.get_approved_cas(query_params)
    logger.info("get_approved_cas('#{query_params}')")

    return get_search_relation(query_params.search_string).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_approved_cas_count(searchable)
    get_search_relation(searchable).count
  end

  private

  def self.get_search_relation(searchable)
    sql_generator =
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    logger.debug("Search relation:\nSQL: '#{sql_generator.sql}'\n"\
        "Params: '#{sql_generator.params}'")

    return ApprovedCa.where(sql_generator.sql, *sql_generator.params).joins(:top_ca)
  end

  def self.get_searchable_columns
    ["approved_cas.name", "ca_infos.valid_from", "ca_infos.valid_to"]
  end
end
