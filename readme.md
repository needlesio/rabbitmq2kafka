## Rabbitmq2Kafka

A bare bones POC of how a shovel reading messages off rabbitmq and writing them to kafka
#Currently a work in progress, do not use :)

---
#### Build
To build run the following

`./gradlew shadowJar`

---
#### To run locally(with some help from docker)
Start a rabbitmq

`docker run -ti --rm --name rabbitmq -p 15672:15672 -p 5672:5672 rabbitmq:3-management`

Start a kafka

`docker run -ti --rm --name kafka -p 9092:9092 -e ADVERTISED_HOSTNAME=$(docker-machine ip) harisekhon/kafka:2.10_0.9`

Run the rabbitmq2kafka tool

`java -jar build/libs/rabbitmq2kafka-*all.jar`