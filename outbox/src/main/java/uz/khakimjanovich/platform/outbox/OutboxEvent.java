package uz.khakimjanovich.platform.outbox;

import java.util.UUID;

/**
 * One event for Kafka, from the module that writes it: its own id (consumers drop repeats by it),
 * topic, key (events with one key keep their order), type and payload.
 */
public record OutboxEvent(UUID id, String topic, String key, String type, String payload) {}
