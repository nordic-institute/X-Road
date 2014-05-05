class ApprovedTsp < ActiveRecord::Base
  include Validators

  validates :url, :presence => true, :url => true
  validates_uniqueness_of :url, :message => I18n.t("errors.tsp_url_not_unique")

  validates :cert, :presence => true
  validates_uniqueness_of :cert,
      :message => I18n.t("errors.tsp_cert_not_unique")

  before_save do |tsp|
    cert_obj = CertObjectGenerator.new.generate(tsp.cert)
    tsp.valid_from = cert_obj.not_before
    tsp.valid_to = cert_obj.not_after
    tsp.name = cert_obj.subject.to_s

    logger.info("Saving CA: '#{tsp}'")
  end

  before_update do |tsp|
    if tsp.cert_changed?
      tsp.errors[:cert] << "could not be modified for existing TSP"
      raise ActiveRecord::RecordInvalid.new(tsp)
    end
  end

  def to_s
    "ApprovedTsp(name: '#{self.name}', url: '#{self.url}', "\
        "validFrom: '#{self.valid_from}', validTo: '#{self.valid_to}')"
  end
  
  def self.get_approved_tsps(query_params)
    logger.info("get_approved_tsps(#{query_params})")

    get_search_relation(query_params.search_string).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_approved_tsp_count(searchable)
    logger.info("get_approved_tsp_count(#{searchable})")

    get_search_relation(searchable).count
  end

  private

  def self.get_search_relation(searchable)
    sql_generator =
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    ApprovedTsp.
        where(sql_generator.sql, *sql_generator.params)
  end

  def self.get_searchable_columns
    [   "approved_tsps.name",
        "CAST(approved_tsps.valid_from AS TEXT)",
        "CAST(approved_tsps.valid_to AS TEXT)"
    ]
  end
end
