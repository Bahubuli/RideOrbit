package com.jitendra.RideOrbit.listener;

// THIS CLASS IS NO LONGER USED — SAFE TO DELETE.
// WebSocket cross-instance delivery is now handled by RabbitMQ STOMP relay.
// Previously this class received events from Redis pub/sub and forwarded them
// to the in-memory STOMP broker. That entire chain has been replaced by
// enableStompBrokerRelay in WebSocketConfig pointing to RabbitMQ.
