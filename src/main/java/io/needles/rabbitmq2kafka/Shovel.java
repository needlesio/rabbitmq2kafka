package io.needles.rabbitmq2kafka;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.rabbitmq.client.*;
import io.needles.rabbitmq2kafka.config.KafkaConfig;
import io.needles.rabbitmq2kafka.config.RabbitmqConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class Shovel implements Lifecycle {
    private static Log log = LogFactory.getLog(Shovel.class);

    @Autowired private Connection rabbitMqConnection;
    @Autowired private Producer<byte[], byte[]> kafkaProducer;
    @Autowired private RabbitmqConfig rabbitmqConfig;
    @Autowired private KafkaConfig kafkaConfig;
    @Autowired private MetricRegistry metricRegistry;

    private volatile boolean running = false;

    private Channel rabbitMqChannel;
    private RabbitMqConsumer rabbitMqConsumer;

    private Meter rabbitmqIncomingMeter;
    private Meter kafkaExceptionMeter;
    private Meter kafkaSuccessMeter;
    private Timer kafkaWriteLatencyTimer;

    @Override
    public void start() {
        log.info("Starting Shovel");
        setupMetrics();
        // Force kafka to connect here, rather than lazily later on
        kafkaProducer.partitionsFor(kafkaConfig.getTopicName());
        createRabbitMqChannel();
        startConsumingFromRabbitMq();

        running = true;
        log.info("Shovel Started");
    }


    @Override
    public void stop() {
        log.info("Stopping Shovel");
        stopConsumingFromRabbitMq();

        running = false;
        log.info("Shovel Stopped");
    }


    @Override
    public boolean isRunning() {
        return running;
    }

    private void setupMetrics(){
        rabbitmqIncomingMeter = metricRegistry.meter("rabbitmq.incoming");
        kafkaExceptionMeter = metricRegistry.meter("kafka.outgoing.exceptions");
        kafkaSuccessMeter = metricRegistry.meter("kafka.outgoing.success");
        kafkaWriteLatencyTimer = metricRegistry.timer("kafka.write.latency");
    }


    private void createRabbitMqChannel() {
        try {
            rabbitMqChannel = rabbitMqConnection.createChannel();
            rabbitMqChannel.basicQos(rabbitmqConfig.getQosPrefetchCount(), true);
        } catch (IOException e) {
            log.error("Problem creating rabbitmq channel", e);
            throw new RuntimeException(e);
        }
    }

    private void startConsumingFromRabbitMq() {
        try {
            if (rabbitmqConfig.isDeclareQueue()) {
                rabbitMqChannel.queueDeclare(rabbitmqConfig.getQueueName(), true, false, false, null);
            }
            rabbitMqConsumer = new RabbitMqConsumer(rabbitMqChannel);
            rabbitMqChannel.basicConsume(rabbitmqConfig.getQueueName(), false, "rabbitmq2kafka", rabbitMqConsumer);
        } catch (IOException e) {
            log.error("Problem starting rabbitmq consumer", e);
            throw new RuntimeException(e);
        }
    }

    private void stopConsumingFromRabbitMq(){
        try {
            if (rabbitMqChannel.isOpen()) {
                rabbitMqChannel.basicCancel(rabbitMqConsumer.getConsumerTag());
            }
        } catch (IOException e){
            log.error("Problem stopping rabbitmq consumer", e);
        }
    }

    private class RabbitMqConsumer extends DefaultConsumer {
        public RabbitMqConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            rabbitmqIncomingMeter.mark();
            ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(kafkaConfig.getTopicName(), body);

            long startSendTime = System.nanoTime();
            kafkaProducer.send(record, (metadata, exception) -> {
                // record kafka metrics
                if (exception != null) {
                    kafkaExceptionMeter.mark();
                    log.error("Failed to send to kafka", exception);
                } else {
                    long finishedSendTime = System.nanoTime();
                    kafkaSuccessMeter.mark();
                    kafkaWriteLatencyTimer.update(finishedSendTime - startSendTime, TimeUnit.NANOSECONDS);
                }

                // ack/nack
                Exception lastException = null;
                for (int i=0; i < 10; i++) {
                    try {
                        if (exception != null) {
                            getChannel().basicNack(envelope.getDeliveryTag(), false, true);
                        } else {
                            getChannel().basicAck(envelope.getDeliveryTag(), false);
                        }
                        return;
                    } catch (Exception e) {
                        // We should hopefully never end up here. If we can't talk to rabbitmq
                        // to nack the message we could be in trouble as the message
                        // will just sit in a un-acked state and not be requeued for retry.
                        // Best we can do is try again.
                        lastException = e;
                        log.error("Problem (n)acking rabbitmq message", e);
                    }
                }
                // We're really in trouble if we get to here....
                if (lastException instanceof RuntimeException){
                    throw (RuntimeException) lastException;
                } else if(lastException != null){
                    throw new RuntimeException(lastException);
                }
            });
        }
    }


}
