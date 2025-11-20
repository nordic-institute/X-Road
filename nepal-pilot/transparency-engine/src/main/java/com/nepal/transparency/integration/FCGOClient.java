package com.nepal.transparency.integration;

import org.springframework.stereotype.Component;

@Component
public class FCGOClient {

    // In a real X-Road scenario, this would use the X-Road Security Server adapter
    // to call the FCGO subsystem.

    public void notifyFreeze(String projectTid, String reason) {
        System.out.println(">>> X-ROAD OUTBOUND: Calling FCGO Service");
        System.out.println(">>> COMMAND: FREEZE_PAYMENT");
        System.out.println(">>> TARGET: " + projectTid);
        System.out.println(">>> REASON: " + reason);
        System.out.println(">>> STATUS: SUCCESS (Mocked)");
    }
}
