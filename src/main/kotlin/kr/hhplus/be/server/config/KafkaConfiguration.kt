package kr.hhplus.be.server.config

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.reservation.application.ReservationPaymentKafkaMessage
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.util.backoff.FixedBackOff

@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = ["enabled"], havingValue = "true")
class KafkaConfiguration {
    @Bean
    fun reservationPaymentTopic(
        @Value("\${app.kafka.topic.reservation-payment}") topicName: String,
    ): NewTopic = TopicBuilder.name(topicName).partitions(3).replicas(1).build()

    @Bean
    fun reservationPaymentProducerFactory(
        kafkaProperties: org.springframework.boot.autoconfigure.kafka.KafkaProperties,
        objectMapper: ObjectMapper,
    ): ProducerFactory<String, ReservationPaymentKafkaMessage> {
        val props = kafkaProperties.buildProducerProperties()
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        return DefaultKafkaProducerFactory(props, StringSerializer(), JsonSerializer<ReservationPaymentKafkaMessage>(objectMapper).apply {
            setAddTypeInfo(false)
        })
    }

    @Bean
    fun reservationPaymentKafkaTemplate(
        producerFactory: ProducerFactory<String, ReservationPaymentKafkaMessage>,
    ): KafkaTemplate<String, ReservationPaymentKafkaMessage> = KafkaTemplate(producerFactory)

    @Bean
    fun reservationPaymentConsumerFactory(
        kafkaProperties: org.springframework.boot.autoconfigure.kafka.KafkaProperties,
        @Value("\${app.kafka.consumer-group}") consumerGroup: String,
        objectMapper: ObjectMapper,
    ): ConsumerFactory<String, ReservationPaymentKafkaMessage> {
        val props = kafkaProperties.buildConsumerProperties()
        props[ConsumerConfig.GROUP_ID_CONFIG] = consumerGroup
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        return DefaultKafkaConsumerFactory(
            props,
            StringDeserializer(),
            JsonDeserializer(ReservationPaymentKafkaMessage::class.java, objectMapper, false).apply {
                addTrustedPackages("*")
            },
        )
    }

    @Bean
    fun reservationPaymentKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, ReservationPaymentKafkaMessage>,
    ): ConcurrentKafkaListenerContainerFactory<String, ReservationPaymentKafkaMessage> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, ReservationPaymentKafkaMessage>()
        factory.consumerFactory = consumerFactory
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.setCommonErrorHandler(DefaultErrorHandler(FixedBackOff(1_000L, 3L)))
        return factory
    }
}
