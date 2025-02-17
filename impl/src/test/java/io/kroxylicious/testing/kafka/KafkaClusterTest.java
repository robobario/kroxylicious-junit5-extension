/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.kroxylicious.testing.kafka;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.kroxylicious.testing.kafka.api.KafkaCluster;
import io.kroxylicious.testing.kafka.common.KafkaClusterConfig;
import io.kroxylicious.testing.kafka.common.KafkaClusterFactory;
import io.kroxylicious.testing.kafka.common.KeytoolCertificateGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Test case that simply exercises the ability to control the kafka cluster from the test.
 * It intentional that this test does not involve the proxy.
 */
public class KafkaClusterTest {

    private static final System.Logger LOGGER = System.getLogger(KafkaClusterTest.class.getName());
    private static final Duration CLUSTER_FORMATION_TIMEOUT = Duration.ofSeconds(10);
    private TestInfo testInfo;
    private KeytoolCertificateGenerator brokerKeytoolCertificateGenerator;
    private KeytoolCertificateGenerator clientKeytoolCertificateGenerator;

    @Test
    public void kafkaClusterKraftMode() throws Exception {
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .kraftMode(true)
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterZookeeperMode() throws Exception {
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .kraftMode(false)
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaTwoNodeClusterKraftMode() throws Exception {
        int brokersNum = 2;
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokersNum(brokersNum)
                .kraftMode(true)
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(brokersNum, cluster);
        }
    }

    @Test
    public void kafkaTwoNodeClusterZookeeperMode() throws Exception {
        int brokersNum = 2;
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokersNum(brokersNum)
                .kraftMode(false)
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(brokersNum, cluster);
        }
    }

    @Test
    public void kafkaClusterKraftModeWithAuth() throws Exception {
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .kraftMode(true)
                .testInfo(testInfo)
                .securityProtocol("SASL_PLAINTEXT")
                .saslMechanism("PLAIN")
                .user("guest", "pass")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterZookeeperModeWithAuth() throws Exception {
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .kraftMode(false)
                .securityProtocol("SASL_PLAINTEXT")
                .saslMechanism("PLAIN")
                .user("guest", "pass")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterKraftModeSASL_SSL_ClientUsesSSLClientAuth() throws Exception {
        createClientCertificate();
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokerKeytoolCertificateGenerator(brokerKeytoolCertificateGenerator)
                .clientKeytoolCertificateGenerator(clientKeytoolCertificateGenerator)
                .kraftMode(true)
                .securityProtocol("SASL_SSL")
                .saslMechanism("PLAIN")
                .user("guest", "pass")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterKraftModeSSL_ClientUsesSSLClientAuth() throws Exception {
        createClientCertificate();
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokerKeytoolCertificateGenerator(brokerKeytoolCertificateGenerator)
                .clientKeytoolCertificateGenerator(clientKeytoolCertificateGenerator)
                .kraftMode(true)
                .securityProtocol("SSL")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterZookeeperModeSASL_SSL_ClientUsesSSLClientAuth() throws Exception {
        createClientCertificate();
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokerKeytoolCertificateGenerator(brokerKeytoolCertificateGenerator)
                .clientKeytoolCertificateGenerator(clientKeytoolCertificateGenerator)
                .kraftMode(false)
                .securityProtocol("SASL_SSL")
                .saslMechanism("PLAIN")
                .user("guest", "pass")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterZookeeperModeSSL_ClientUsesSSLClientAuth() throws Exception {
        createClientCertificate();
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokerKeytoolCertificateGenerator(brokerKeytoolCertificateGenerator)
                .clientKeytoolCertificateGenerator(clientKeytoolCertificateGenerator)
                .kraftMode(false)
                .securityProtocol("SSL")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterKraftModeSSL_ClientNoAuth() throws Exception {
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokerKeytoolCertificateGenerator(brokerKeytoolCertificateGenerator)
                .kraftMode(true)
                .securityProtocol("SSL")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterZookeeperModeSSL_ClientNoAuth() throws Exception {
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokerKeytoolCertificateGenerator(brokerKeytoolCertificateGenerator)
                .kraftMode(false)
                .securityProtocol("SSL")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterKraftModeSASL_SSL_ClientNoAuth() throws Exception {
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokerKeytoolCertificateGenerator(brokerKeytoolCertificateGenerator)
                .kraftMode(true)
                .securityProtocol("SASL_SSL")
                .saslMechanism("PLAIN")
                .user("guest", "guest")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    @Test
    public void kafkaClusterZookeeperModeSASL_SSL_ClientNoAuth() throws Exception {
        try (var cluster = KafkaClusterFactory.create(KafkaClusterConfig.builder()
                .testInfo(testInfo)
                .brokerKeytoolCertificateGenerator(brokerKeytoolCertificateGenerator)
                .kraftMode(false)
                .securityProtocol("SASL_SSL")
                .saslMechanism("PLAIN")
                .user("guest", "guest")
                .build())) {
            cluster.start();
            verifyRecordRoundTrip(1, cluster);
        }
    }

    private void verifyRecordRoundTrip(int expected, KafkaCluster cluster) throws Exception {
        var topic = "TOPIC_1";
        var message = "Hello, world!";

        try (var admin = KafkaAdminClient.create(cluster.getKafkaClientConfiguration())) {
            await().atMost(CLUSTER_FORMATION_TIMEOUT).untilAsserted(() -> assertEquals(expected, getActualNumberOfBrokers(admin)));
            var rf = (short) Math.min(1, Math.max(expected, 3));
            createTopic(admin, topic, rf);
        }

        produce(cluster, topic, message);
        consume(cluster, topic, "Hello, world!");
    }

    private void produce(KafkaCluster cluster, String topic, String message) throws Exception {
        Map<String, Object> config = cluster.getKafkaClientConfiguration();
        config.putAll(Map.<String, Object> of(
                ProducerConfig.CLIENT_ID_CONFIG, "myclient",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3_600_000));
        try (var producer = new KafkaProducer<String, String>(config)) {
            producer.send(new ProducerRecord<>(topic, "my-key", message)).get();
        }
    }

    private void consume(KafkaCluster cluster, String topic, String message) throws Exception {
        Map<String, Object> config = cluster.getKafkaClientConfiguration();
        config.putAll(Map.of(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.GROUP_ID_CONFIG, "my-group-id",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(Set.of(topic));
            var records = consumer.poll(Duration.ofSeconds(10));
            assertEquals(1, records.count());
            assertEquals(message, records.iterator().next().value());
            consumer.unsubscribe();
        }
    }

    private int getActualNumberOfBrokers(AdminClient admin) throws Exception {
        DescribeClusterResult describeClusterResult = admin.describeCluster();
        return describeClusterResult.nodes().get().size();
    }

    private void createTopic(AdminClient admin1, String topic, short replicationFactor) throws Exception {
        admin1.createTopics(List.of(new NewTopic(topic, 1, replicationFactor))).all().get();
    }

    @BeforeEach
    void before(TestInfo testInfo) throws IOException {
        this.testInfo = testInfo;
        this.brokerKeytoolCertificateGenerator = new KeytoolCertificateGenerator();
    }

    private void createClientCertificate() throws GeneralSecurityException, IOException {
        this.clientKeytoolCertificateGenerator = new KeytoolCertificateGenerator();
        this.clientKeytoolCertificateGenerator.generateSelfSignedCertificateEntry("clientTest@kroxylicious.io", "client", "Dev", "Kroxylicious.ip", null, null, "US");
    }
}
