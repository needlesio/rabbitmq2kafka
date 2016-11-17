package io.needles.rabbitmq2kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="kafka")
@Data
public class KafkaConfig {
    private String servers;
    private String acks;
    private int lingerMs;
    private String compression;
    private int retries;
    private int maxBlockTimeMs;
    private int maxRequestSize;
    private String topicName;
}
