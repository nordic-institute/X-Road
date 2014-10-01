unless $rails_rake_task
  java_import Java::ee.cyber.sdsb.common.db.HibernateUtil

  at_exit do
    HibernateUtil.closeSessionFactories
  end
end
