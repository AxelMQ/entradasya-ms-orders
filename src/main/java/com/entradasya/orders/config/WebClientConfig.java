package com.entradasya.orders.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${services.events.url:http://localhost:8002}")
    private String eventsUrl;

    @Value("${services.payments.url:http://localhost:8004}")
    private String paymentsUrl;

    @Value("${services.tickets.url:http://localhost:8005}")
    private String ticketsUrl;

    @Value("${services.notifications.url:http://localhost:8006}")
    private String notificationsUrl;

    private WebClient buildWebClient(String baseUrl, int connectTimeoutMs, int readTimeoutSeconds) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebClient eventsWebClient() {
        // Connect timeout 2s, Read timeout 3s
        return buildWebClient(eventsUrl, 2000, 3);
    }

    @Bean
    public WebClient paymentsWebClient() {
        // Connect timeout 2s, Read timeout 5s
        return buildWebClient(paymentsUrl, 2000, 5);
    }

    @Bean
    public WebClient ticketsWebClient() {
        // Connect timeout 2s, Read timeout 10s
        return buildWebClient(ticketsUrl, 2000, 10);
    }

    @Bean
    public WebClient notificationsWebClient() {
        // Connect timeout 2s, Read timeout 3s
        return buildWebClient(notificationsUrl, 2000, 3);
    }
}
