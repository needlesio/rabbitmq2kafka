package io.needles.rabbitmq2kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="rabbitmq")
@Data
public class RabbitmqConfig {
    private String servers;
    private String username;
    private String password;
    private String vHost;
    private boolean declareQueue;
    private String queueName;
    private int qosPrefetchCount;
}
