# Static helper methods for common raw SQL
class CommonSql

  def self.get_identifier_to_member_join_sql
    "LEFT JOIN security_server_clients
      ON identifiers.member_code = security_server_clients.member_code
    LEFT JOIN member_classes
      ON security_server_clients.member_class_id = member_classes.id
      AND identifiers.member_class = member_classes.code"
  end
end
