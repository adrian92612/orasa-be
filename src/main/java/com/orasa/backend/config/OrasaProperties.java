package com.orasa.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "orasa")
@Data
public class OrasaProperties {

    private App app = new App();
    private Jwt jwt = new Jwt();
    private Google google = new Google();
    private Philsms philsms = new Philsms();
    private Redis redis = new Redis();
    private Payloro payloro = new Payloro();

    @Data
    public static class App {
        private String frontendUrl;
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expiration;
    }

    @Data
    public static class Google {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }

    @Data
    public static class Philsms {
        private String apiToken;
        private String senderId = "Orasa";
        private String baseUrl;
    }

    @Data
    public static class Redis {
        private String host;
        private int port;
    }

    @Data
    public static class Payloro {
        private String merchantNo;
        private String privateKey;
        private String baseUrl = "https://testgateway.payloro.ph/api";
        private String notifyUrl;
    }
}
