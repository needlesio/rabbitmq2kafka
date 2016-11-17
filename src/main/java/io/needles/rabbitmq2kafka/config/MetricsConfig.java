package io.needles.rabbitmq2kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="metrics")
@Data
public class MetricsConfig {
    private int port;
    private boolean logToConsole;
    private int consoleLogPeriod;
}
