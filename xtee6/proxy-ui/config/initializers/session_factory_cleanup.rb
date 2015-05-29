unless $rails_rake_task
  java_import Java::ee.ria.xroad.common.db.HibernateUtil

  at_exit do
    HibernateUtil.closeSessionFactories
  end
end
