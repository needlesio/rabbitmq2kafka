package io.needles.rabbitmq2kafka;

import com.rabbitmq.client.*;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.IOException;

public class Shovel extends DefaultConsumer {
    private Producer<byte[], byte[]> kafkaProducer;
    private String rabbitMqQueue;
    private String kafkaTopic;

    public Shovel(Channel rabbitMqChannel,
                  String rabbitMqQueue,
                  Producer<byte[], byte[]> kafkaProducer,
                  String kafkaTopic) {
        super(rabbitMqChannel);
        this.kafkaProducer = kafkaProducer;
        this.rabbitMqQueue = rabbitMqQueue;
        this.kafkaTopic = kafkaTopic;
    }

    public void run() {
        try {
            getChannel().queueDeclare(rabbitMqQueue, true, false, false, null);
            getChannel().basicConsume(rabbitMqQueue, false, "rabbitmq2kafka", this);
        } catch (IOException e){
            System.err.println("Problem starting up");
            e.printStackTrace();
            shutdown();
        }
    }

    public void shutdown() {
        try {
            Channel channel = getChannel();
            if (channel.isOpen()) {
                channel.basicCancel(getConsumerTag());
            }

            kafkaProducer.close();

            if (channel.getConnection().isOpen()) {
                channel.getConnection().close();
            }
        } catch (IOException e) {
            System.err.println("Error Shutting down");
            e.printStackTrace();
        }
    }

    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body) throws IOException {
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(kafkaTopic, body);
        try {
            kafkaProducer.send(record, (metadata, exception) -> {
                try {

                    if (metadata != null) {
                        getChannel().basicAck(envelope.getDeliveryTag(), false);
                    } else {
                        System.err.println("Nacking rabbitmq message due to");
                        exception.printStackTrace();
                        getChannel().basicNack(envelope.getDeliveryTag(), false, true);
                    }
                } catch (IOException e) {
                    // Something went wrong (n)acking
                    // prob best just shutdown as we don't want unacked messages to build up
                    System.err.println("Error (n)acking rabbitmq message");
                    e.printStackTrace();
                    shutdown();
                }
            });
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
