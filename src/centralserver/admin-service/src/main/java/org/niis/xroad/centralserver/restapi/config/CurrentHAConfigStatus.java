package org.niis.xroad.centralserver.restapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CurrentHAConfigStatus {

    @Bean
    HAConfigStatus currentHaConfigStatus() {
        boolean isHaConfigured = true;
        String haNodeName;


        if (!isPostgresDatabaseInUse()) {
            isHaConfigured = false;
        }

        haNodeName = getHaNodeNameProperty();
        if (haNodeName.isEmpty()) {
            isHaConfigured = false;
            haNodeName = "node_0";

        }
        return new HAConfigStatus(haNodeName, isHaConfigured);
    }

    private boolean isPostgresDatabaseInUse() {
        String dataSourceUrl = System.getProperty("spring.datasource.url", "");
        return dataSourceUrl.toLowerCase().contains("postgres");
    }

    private String getHaNodeNameProperty() {
        return System.getProperty("xroad.center.ha-node-name", "");
    }


}
