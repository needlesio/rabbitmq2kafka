# Needles Rabbitmq2Kafka

A bare bones POC of how a shovel reading messages off rabbitmq and writing them to kafka
##Not yet stress tested or proven in a production like setting, be warned.

---
#### Build
To build you must have jdk 8+ installed, then just run the following

`./gradlew`

---
#### To run/dev locally(with some help from docker)
Start a rabbitmq

`docker run -ti --rm --name rabbitmq -p 15672:15672 -p 5672:5672 rabbitmq:3.5.6-management`

Start a zookeeper

`docker run -ti --rm --name zookeeper -p 2181:2181 -e ZOOKEEPER_CLIENT_PORT=2181 confluentinc/cp-zookeeper:3.1.1`

Start a kafka

`docker run -ti --rm --name kafka -p 9092:9092 -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://$(docker-machine ip):9092 -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 --link zookeeper confluentinc/cp-kafka:3.1.1`

Run the rabbitmq2kafka tool

`java -Xms1g -Xmx1g -XX:+UseG1GC -jar build/libs/rabbitmq2kafka*.jar`

Or with console metrics enabled

`METRICS_LOG_TO_CONSOLE=true java -Xms1g -Xmx1g -XX:+UseG1GC -jar build/libs/rabbitmq2kafka*.jar`

Metrics over http can be found at [http://localhost:8000/metrics](http://localhost:8000/metrics)

---
#### To configure
There are many ways of configuring this tool, the easiest are probably to provide a custom application.yml in the current directory or to
set environment vars before starting(handy for running in docker container).
The environment vars that you might want to set are as follows
* `METRICS_PORT` - port to expose metrics on(http), defaults to `8000`
* `METRICS_LOG_TO_CONSOLE` - set to true to log metrics out to the console, defaults to `false`

* `RABBITMQ_SERVERS` - comma separated list of servers to connect to, defaults to `192.168.99.100:5672`
* `RABBITMQ_USERNAME` - username for connecting to rabbitmq, defaults to `guest`
* `RABBITMQ_PASSWORD` - password for connecting to rabbitmq, defaults to `guest`
* `RABBITMQ_V_HOST` - virtual host containing queue, defaults to `/`
* `RABBITMQ_DECLARE_QUEUE` - if set to true, rabbitmq2kafka will attempt to create the queue if it doesn't exist. Should probably be set to false in a production like environment, defaults to `true`
* `RABBITMQ_QUEUE_NAME` - queue to shovel from, defaults to `test`

* `KAFKA_SERVERS` - comma separated list of servers to connect to, defaults to `192.168.99.100:9092`
* `KAFKA_TOPIC_NAME` - The topic to publish the messages to, defaults to `test`

A full list of configuration values can be found [here](src/main/resources/application.yml)

For more ways to configure these checkout the [spring boot guide](http://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html)
