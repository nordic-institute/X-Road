package org.niis.xroad.restapi.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@DataJpaTest
public class ClientRepositoryIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ClientRepository repository;

    @Test
    public void test() {
        assertEquals(1, repository.getAllClients().size());
    }
}


