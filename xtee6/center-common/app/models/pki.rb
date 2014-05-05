class Pki < ActiveRecord::Base

  has_one :top_ca,
      :class_name => "CaInfo",
      :foreign_key => "top_ca_id",
      :dependent => :destroy,
      :autosave => true

  has_many :intermediate_cas,
      :class_name => "CaInfo",
      :foreign_key => "intermediate_ca_id",
      :dependent => :destroy,
      :autosave => true

  validates :top_ca, :presence => true

  before_save do |pki|
    logger.info("Saving PKI: '#{pki}'")

    pki.prepare_name_extractor()
    pki.prepare_top_ca_cert()
  end

  before_destroy do |pki|
    logger.info("Deleting PKI: '#{pki}'")
  end

  def prepare_name_extractor
    if self.name_extractor_missing?
      raise SdsbArgumentError.new(:no_name_extractor_method) 
    end

    if self.authentication_only?
      self.name_extractor_method_name = nil
      self.name_extractor_member_class = nil
    end
  end

  def name_extractor_missing?
    !self.authentication_only? &&
        (!self.name_extractor_method_name ||
            self.name_extractor_method_name.empty?)
  end

  def prepare_top_ca_cert
    top_ca_cert = self.top_ca.cert

    cert_obj = CertObjectGenerator.new.generate(top_ca_cert)
    self.name = cert_obj.subject.to_s
  end

  def to_s
    "Pki(authOnly: '#{self.authentication_only}', nameExtractorMemberClass: "\
      "'#{self.name_extractor_member_class}', nameExtractorMethodName: "\
      "'#{self.name_extractor_method_name}', id: '#{self.id}', "\
      ", topCa: '#{self.top_ca}', intermediateCas: "\
      "[#{self.intermediate_cas.join(', ')}])"
  end

  def self.get_pkis(query_params)
    logger.info("get_pkis('#{query_params}')")

    get_search_relation(query_params.search_string).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_pkis_count(searchable)
    get_search_relation(searchable).count
  end

  private

  def self.get_search_relation(searchable)
    sql_generator = 
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    Pki.where(sql_generator.sql, *sql_generator.params).joins(:top_ca)
  end

  def self.get_searchable_columns
    ["pkis.name"]
  end
end
