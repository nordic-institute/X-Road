# Static helper methods for common raw SQL
class CommonSql
  @@is_postgres = nil

  def self.get_identifier_to_member_join_sql
    "LEFT JOIN security_server_clients
      ON identifiers.member_code = security_server_clients.member_code
    LEFT JOIN member_classes
      ON security_server_clients.member_class_id = member_classes.id
      AND identifiers.member_class = member_classes.code"
  end

  # Column with format table.column
  def self.turn_timestamp_into_text(column)
    return "lower(#{column})" if !is_postgres?()

    return "to_char(#{column} "\
        "AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS')"
  end

  def self.is_postgres?
    if @@is_postgres == nil
      conf = Rails.configuration.database_configuration[Rails.env]
      @@is_postgres = "postgresql".eql?(conf["adapter"])
    end

    return @@is_postgres
  end
end
