package com.ebanking.transactions.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);

    private final ObjectMapper objectMapper;
    private final TransactionIngestionService ingestionService;

    public TransactionConsumer(ObjectMapper objectMapper, TransactionIngestionService ingestionService) {
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = "${transactions.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload,
                        @Header(KafkaHeaders.RECEIVED_KEY) String transactionId,
                        ConsumerRecord<String, String> record) throws JsonProcessingException {
        TransactionMessage message = objectMapper.readValue(payload, TransactionMessage.class);
        ingestionService.ingest(transactionId, message);
        log.info("Ingested transaction offset={} partition={} key={}",
                record.offset(), record.partition(), transactionId);
    }
}
