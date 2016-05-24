require "common-ui/cert_utils"

class ApprovedCa < ActiveRecord::Base
  include Validators

  validates_with MaxlengthValidator
  validates :top_ca, :presence => true
  validates :cert_profile_info , :presence => true

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

    approved_ca.validate_cert_profile_info
    approved_ca.prepare_top_ca_cert
  end

  before_destroy do |approved_ca|
    logger.info("Deleting PKI: '#{approved_ca}'")
  end

  def validate_cert_profile_info
    Java::ee.ria.xroad.commonui.CertificateProfileInfoValidator\
        .validate(self.cert_profile_info)
  rescue Java::java.lang.RuntimeException
    raise $!.message
  end

  def prepare_top_ca_cert
    top_ca_cert = self.top_ca.cert

    cert_obj = CommonUi::CertUtils.cert_object(top_ca_cert)
    subject_name = CommonUi::CertUtils.cert_subject_cn(cert_obj)

    unless MaxlengthValidator.string_length_valid?(subject_name)
      raise I18n.t("errors.approved_ca.top_ca_cert_too_long_subject_name", {
          :max_length => Validators::STRING_MAX_LENGTH,
          :subject_name => subject_name})
    end

    self.name = subject_name
  end

  def to_s
    "ApprovedCa(authOnly: '#{self.authentication_only}', "\
      "certificateProfileInfo: "\
      "'#{self.cert_profile_info}', id: '#{self.id}', "\
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
