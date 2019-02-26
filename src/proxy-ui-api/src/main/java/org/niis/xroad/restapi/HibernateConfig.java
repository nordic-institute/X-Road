package org.niis.xroad.restapi;

//@Configuration
//@EnableTransactionManagement
public class HibernateConfig {

    // TO DO :
//    configuration.setInterceptor(interceptor);
//    applyDatabasePropertyFile(configuration, name);
//    applySystemProperties(configuration, name);
// multiple facotries: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#howto-two-datasources
    // config locations: https://stackoverflow.com/a/10820111/1469083
    // basic "configure custom datasource":
    // https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#howto-configure-a-datasource
//    @Bean
//    public LocalSessionFactoryBean sessionFactory() {
//        LocalSessionFactoryBean sessionFactory = new LocalSessionFactoryBean();
//        sessionFactory.setConfigLocations(new ClassPathResource("hibernate.cfg.xml"),
//                new ClassPathResource("serverconf.hibernate.cfg.xml"));
//        return sessionFactory;
//    }

//    @Bean
//    public DataSource dataSource() {
//        BasicDataSource dataSource = new BasicDataSource();
//        dataSource.setDriverClassName("org.h2.Driver");
//        dataSource.setUrl("jdbc:h2:mem:db;DB_CLOSE_DELAY=-1");
//        dataSource.setUsername("sa");
//        dataSource.setPassword("sa");
//
//        return dataSource;
//    }

}
