package io.needles.rabbitmq2kafka;

import com.codahale.metrics.MetricRegistry;
import com.rabbitmq.client.*;
import org.apache.kafka.clients.producer.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Main.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ShovelTest {

    @MockBean private Producer kafkaProducer;

    @MockBean private Connection rabbitMqConnection;

    @Mock private Channel rabbitMqChannel;

    @Autowired private Shovel shovel;

    @Autowired private MetricRegistry metricRegistry;

    @Before
    public void setup() throws Exception {
        when(rabbitMqConnection.createChannel()).thenReturn(rabbitMqChannel);
    }

    @Test
    public void testBasicStart() throws Exception {
        shovel.start();

        verify(rabbitMqChannel).basicConsume(eq("test"), eq(false), eq("rabbitmq2kafka"), notNull(Consumer.class));
    }

    @Test
    public void testBasicStop() throws Exception {
        shovel.start();

        verify(rabbitMqChannel).basicConsume(eq("test"), eq(false), eq("rabbitmq2kafka"), notNull(Consumer.class));
    }

    @Test
    public void testIsRunning(){
        assertFalse(shovel.isRunning());
        shovel.start();
        assertTrue(shovel.isRunning());
        shovel.stop();
        assertFalse(shovel.isRunning());
    }

    /**
     * The end to end shovel process should happen as follows:
     * 1. shovel receives a message from rabbitmq
     * 2. shovel writes message to kafka
     * 3. kafka client invokes message received callback
     * 4. callback sends ack with the correct deliverytag back to rabbitmq
     */
    @Test
    public void testHappyCaseShovel() throws Exception {
        byte[] body = "My Message".getBytes();
        Envelope envelope = new Envelope(1001, true, "", "");

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Callback> kafkaCallbackCaptor = ArgumentCaptor.forClass(Callback.class);
        ArgumentCaptor<ProducerRecord> kafkaRecordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);

        shovel.start();

        // 1. shovel receives a message from rabbitmq
        verify(rabbitMqChannel).basicConsume(eq("test"), eq(false), eq("rabbitmq2kafka"), consumerCaptor.capture());
        Consumer consumer = consumerCaptor.getValue();
        consumer.handleDelivery("consumerTag", envelope, new AMQP.BasicProperties(), body);

        // 2. shovel writes message to kafka
        verify(kafkaProducer).send(kafkaRecordCaptor.capture(), kafkaCallbackCaptor.capture());
        assertEquals(kafkaRecordCaptor.getValue().value(), body);
        assertEquals(kafkaRecordCaptor.getValue().topic(), "test");

        // 3. kafka client invokes message received callback
        RecordMetadata meta = new RecordMetadata(null, 0, 0, 0, 0, 0, 0);
        kafkaCallbackCaptor.getValue().onCompletion(meta, null);

        // 4. callback sends ack with the correct deliverytag back to rabbitmq
        verify(rabbitMqChannel).basicAck(1001, false);

        assertEquals(metricRegistry.meter("rabbitmq.incoming.meter").getCount(), 1);
        assertEquals(metricRegistry.meter("kafka.outgoing.success").getCount(), 1);
        assertEquals(metricRegistry.meter("kafka.outgoing.exceptions").getCount(), 0);
    }

    @Test
    public void testKafkaFailureShovel() throws Exception {
        byte[] body = "My Message".getBytes();
        Envelope envelope = new Envelope(1001, true, "", "");

        ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Callback> kafkaCallbackCaptor = ArgumentCaptor.forClass(Callback.class);
        ArgumentCaptor<ProducerRecord> kafkaRecordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);

        shovel.start();

        // 1. shovel receives a message from rabbitmq
        verify(rabbitMqChannel).basicConsume(eq("test"), eq(false), eq("rabbitmq2kafka"), consumerCaptor.capture());
        Consumer consumer = consumerCaptor.getValue();
        consumer.handleDelivery("consumerTag", envelope, new AMQP.BasicProperties(), body);

        // 2. shovel writes message to kafka
        verify(kafkaProducer).send(kafkaRecordCaptor.capture(), kafkaCallbackCaptor.capture());

        // 3. kafka client invokes message received callback with an error
        kafkaCallbackCaptor.getValue().onCompletion(null, new Exception("Something bad happened"));

        // 4. callback sends nack with the correct deliverytag back to rabbitmq
        verify(rabbitMqChannel).basicNack(1001, false, true);

        assertEquals(metricRegistry.meter("rabbitmq.incoming.meter").getCount(), 1);
        assertEquals(metricRegistry.meter("kafka.outgoing.success").getCount(), 0);
        assertEquals(metricRegistry.meter("kafka.outgoing.exceptions").getCount(), 1);
    }
}
