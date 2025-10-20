package com.yoordi.ingest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventIngestOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event Ingest Service")
                        .description("웹툰 이벤트 수집 및 Kafka 발행 서비스")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Yoordi Team")
                                .email("dev@yoordi.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8081")
                                .description("로컬 개발 서버")
                ));
    }
}