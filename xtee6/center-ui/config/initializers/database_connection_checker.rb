# Responsible of setting appropriate HTTP status code on database failure.
class DatabaseConnectionChecker
  HTTP_STATUS_DATABASE_UNAVAILABLE = 503

  def initialize(app)
    @app = app
  end

  def call(env)
    return @app.call(env)
  rescue ActiveRecord::ActiveRecordError
    error_message = I18n.t("common.db_error")
    return [HTTP_STATUS_DATABASE_UNAVAILABLE , {}, [error_message]]
  end
end
