package com.jitendra.RideOrbit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.rabbitmq.host}")
    private String relayHost;

    @Value("${rabbitmq.stomp.port:61613}")
    private int relayPort;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Relay STOMP messages to RabbitMQ instead of handling them in-memory.
        // RabbitMQ becomes the central broker — it manages subscriptions, delivers
        // messages across all app instances, persists undelivered messages, and
        // supports acknowledgements. This replaces both the in-memory broker and
        // the Redis pub/sub that was previously bridging instances.
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setClientLogin(username)       // used for client STOMP connections
                .setClientPasscode(password)
                .setSystemLogin(username)       // used for the system relay connection
                .setSystemPasscode(password);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // clients connect here: ws://localhost:8080/ws
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }
}
