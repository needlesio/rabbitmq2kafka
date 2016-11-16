package io.needles.rabbitmq2kafka;

import com.rabbitmq.client.*;
import org.apache.kafka.clients.producer.*;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class Main {

    public static void main(String[] args) throws IOException, TimeoutException {
        Channel rabbitMqChannel = getRabbitMqChannel();
        Producer<byte[], byte[]> kafkaProducer = getKafkaProducer();

        final Shovel shovel = new Shovel(rabbitMqChannel,"myQueue", kafkaProducer, "myTopic");

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shovel.shutdown()));

        shovel.run();
    }

    private static Channel getRabbitMqChannel() throws IOException, TimeoutException{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.99.100");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setVirtualHost("/");
        //factory.setAutomaticRecoveryEnabled(true);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.basicQos(1000, true);
        return channel;
    }

    private static Producer<byte[], byte[]> getKafkaProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "192.168.99.100:9092");
        props.put("acks", "all");
        props.put("linger.ms", 20);
        props.put("max.block.ms", 60000);
        props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        return new KafkaProducer<>(props);
    }
}
