package com.jitendra.RideOrbit.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.RideOrbit.dto.BookingStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBookingEventListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Receives booking events from the Redis "booking-events" channel and
     * forwards them to STOMP subscribers on /topic/booking.{bookingId}.
     *
     * This runs on every app instance, so each instance pushes the event
     * to whichever WebSocket clients happen to be connected to it.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            BookingStatusEvent event = objectMapper.readValue(message.getBody(), BookingStatusEvent.class);
            messagingTemplate.convertAndSend("/topic/booking." + event.getBookingId(), event);
        } catch (Exception e) {
            log.error("Failed to forward booking event from Redis to WebSocket", e);
        }
    }
}
