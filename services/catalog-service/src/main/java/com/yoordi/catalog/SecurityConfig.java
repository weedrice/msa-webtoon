package com.yoordi.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filter(HttpSecurity http, @Value("${security.permitAll:false}") boolean permitAll) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(a -> {
                a.requestMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                if (permitAll) {
                    a.anyRequest().permitAll();
                } else {
                    a.requestMatchers("/catalog/**").hasAuthority("SCOPE_write:catalog");
                    a.anyRequest().authenticated();
                }
            })
            .oauth2ResourceServer(o -> o.jwt(j -> {})).build();
    }
}
