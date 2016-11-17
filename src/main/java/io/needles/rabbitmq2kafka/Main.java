package io.needles.rabbitmq2kafka;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.needles.rabbitmq2kafka.config.KafkaConfig;
import io.needles.rabbitmq2kafka.config.MetricsConfig;
import io.needles.rabbitmq2kafka.config.RabbitmqConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SpringBootApplication
public class Main {
    private static Log log = LogFactory.getLog(Main.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Main.class, args);
        ctx.registerShutdownHook();

        try {
            ctx.start();
        } catch (Exception e){
            log.error("Error during app startup, bailing", e);
            ctx.close();
            System.exit(1);
        }
    }

    @Bean
    public Connection rabbitMqConnection(RabbitmqConfig config) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(config.getUsername());
        factory.setPassword(config.getPassword());
        factory.setVirtualHost(config.getVHost());
        factory.setAutomaticRecoveryEnabled(true);

        String[] servers = config.getServers().split("[, ]");
        List<Address> addresses = new ArrayList<>();
        for (String server: servers){
            String[] serverComponents = server.split(":");
            addresses.add(new Address(serverComponents[0], Integer.parseInt(serverComponents[1])));
        }
        return factory.newConnection(addresses);
    }

    @Bean
    public Producer<byte[], byte[]> kafkaProducer(KafkaConfig config) {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getServers());
        props.put("acks", config.getAcks());
        props.put("linger.ms", config.getLingerMs());
        props.put("compression.type", config.getCompression());
        props.put("retries", config.getRetries());
        props.put("max.block.ms", config.getMaxBlockTimeMs());
        props.put("max.request.size", config.getMaxRequestSize());
        props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        return new KafkaProducer<>(props);
    }

    @Bean
    @Lazy(false)
    public ConsoleReporter consoleReporter(MetricsConfig config, MetricRegistry registry){
        // Add some heap memory usage metrics while we're at it...
        Map<String, Metric> metrics = new MemoryUsageGaugeSet().getMetrics();
        List<String> memoryKeys = Arrays.asList("heap.committed", "heap.init", "heap.max", "heap.usage", "heap.used");
        for (String k : memoryKeys){
            registry.register(k, metrics.get(k));
        }


        if (config.isLogToConsole()) {
            ConsoleReporter reporter = ConsoleReporter.forRegistry(registry)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build();
            reporter.start(config.getConsoleLogPeriod(), TimeUnit.SECONDS);
            return reporter;
        }
        return null;
    }
}
