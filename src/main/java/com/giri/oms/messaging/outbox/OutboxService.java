package com.giri.oms.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

import static com.giri.oms.common.correlation.CorrelationIdConstants.MDC_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper objectMapper;
    private final Clock clock;

    @Transactional
    public UUID enqueue(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            Object payload) {
        String serializedPayload = serializePayload(payload);

        // Whichever thread called enqueue() already has the right correlation id
        // in MDC by the time it gets here — CorrelationIdFilter put it there for
        // the original HTTP request thread, and every downstream KafkaListener
        // does the same for itself via MdcCorrelation before calling into any
        // service that might loop back around to another enqueue(). We just read
        // it, we don't set it — this method has no opinion on what put it there.
        String correlationId = MDC.get(MDC_KEY);

        OutboxEvent outboxEvent = OutboxEvent.pending(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                partitionKey,
                serializedPayload,
                correlationId,
                clock);

        outboxEventRepository.save(outboxEvent);
        log.debug("Enqueued outbox event id={} type={} aggregate={}/{}", eventId, eventType, aggregateType, aggregateId);
        return eventId;
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload for event type: "
                    + payload.getClass().getSimpleName(), ex);
        }
    }
}
