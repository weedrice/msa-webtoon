package com.yoordi.gw.config;

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
    public OpenAPI apiGatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MSA Webtoon Platform - API Gateway")
                        .description("실시간 웹툰 랭킹/검색 플랫폼의 API Gateway")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Yoordi Team")
                                .email("dev@yoordi.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("https://api.webtoon.yoordi.com")
                                .description("운영 서버 (향후)")
                ));
    }
}