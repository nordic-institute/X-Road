package org.niis.xroad.restapi.scheduling;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.controller.WebSocketController;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
public class WebSocketTestScheduling {

    public static final int RATE = 5000;

    @Autowired
    private WebSocketController webSocketController;

    @Autowired
    private ClientRepository clientRepository;

    @Scheduled(fixedRate = RATE)
    public void scheduledTask() throws Exception {
        List<ClientType> clients = clientRepository.getAllLocalClients();
        Map<Integer, String> statusMap = new HashMap<>();
        statusMap.put(0, ClientType.STATUS_REGISTERED);
        statusMap.put(1, ClientType.STATUS_DELINPROG);
        statusMap.put(2, ClientType.STATUS_GLOBALERR);
        statusMap.put(3, ClientType.STATUS_REGINPROG);
        statusMap.put(4, ClientType.STATUS_SAVED);

        int randomIndex = getRandomNumberInRange(0, clients.size() - 1);
        ClientType clientType = clients.get(randomIndex);
        Integer randomStatus = getRandomNumberInRange(0, 4);
        clientType.setClientStatus(statusMap.get(randomStatus));
        clientRepository.saveOrUpdate(clientType);

        // Broadcast a message through the websocket every 5 seconds
        webSocketController.sendString("saved a new status ("
                + clientType.getClientStatus() + ") for client "
                + clientType.getIdentifier().toShortString());
    }

    private static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }
}
