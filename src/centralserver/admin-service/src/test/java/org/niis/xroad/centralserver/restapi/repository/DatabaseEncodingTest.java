package org.niis.xroad.centralserver.restapi.repository;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@WithMockUser
@Slf4j
public class DatabaseEncodingTest {

    @Autowired
    private FlattenedSecurityServerClientRepository repository;

    @Test
    public void testScandis() throws Exception {
        var clients = repository.findAll();
        var member = clients.stream().filter(
                f -> f.getMemberCode().equals("M5"))
                .findFirst().get();
        log.info("*********************************");
        log.info("read member ffrom db: " + member);
        log.info("name: " + member.getMemberName());
        log.info("default charset: " + Charset.defaultCharset());
        log.info("default locale: " + Locale.getDefault());
        log.info("System.getProperties():");
        System.getProperties()
                .entrySet()
                .stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .sorted()
                .forEach(log::info);
        // Member5-ÅÖÄ
        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("Member5-ÅÖÄ"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("ÅÖÄ"));
        assertEquals(1, clients.size());

        clients = repository.findAll(
                FlattenedSecurityServerClientRepository.multifieldSearch("åöä"));
        assertEquals(1, clients.size());
    }
}
