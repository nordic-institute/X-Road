package ee.ria.xroad.signer;

import ee.ria.xroad.common.SystemProperties;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static ee.ria.xroad.signer.protocol.ComponentNames.SIGNER;

@ComponentScan("ee.ria.xroad.signer.protocol")
@Configuration
public class SignerConfig {

    @Bean
    @Deprecated
    public ActorSystem actorSystem() {
        return ActorSystem.create(SIGNER, getConf(SystemProperties.getSignerPort()));
    }

    private static Config getConf(int signerPort) {
        Config conf = ConfigFactory.load().getConfig("signer-main")
                .withFallback(ConfigFactory.load());
        return conf.withValue("akka.remote.artery.canonical.port",
                ConfigValueFactory.fromAnyRef(signerPort));
    }
}
