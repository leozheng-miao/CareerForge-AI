package com.leo.careerforgeai.auth.config;

import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.ResultUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 配置本地兼容模式及正式无状态Bearer JWT安全边界
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    @Profile({"local", "dev", "test"})
    @ConditionalOnProperty(
            prefix = "careerforge.auth",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
    SecurityFilterChain authenticatedSecurityFilterChain(
            HttpSecurity http,
            JsonMapper jsonMapper
    ) throws Exception {
        DefaultBearerTokenResolver tokenResolver = new DefaultBearerTokenResolver();
        tokenResolver.setAllowFormEncodedBodyParameter(false);
        tokenResolver.setAllowUriQueryParameter(false);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(tokenResolver)
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType("application/json");
                            jsonMapper.writeValue(
                                    response.getOutputStream(),
                                    ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR)
                            );
                        }))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType("application/json");
                            jsonMapper.writeValue(
                                    response.getOutputStream(),
                                    ResultUtils.error(ErrorCode.NO_AUTH_ERROR)
                            );
                        }))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
    SecretKey authSecretKey(AuthProperties properties) {
        return properties.secretKey();
    }

    @Bean
    @ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
    JwtDecoder jwtDecoder(
            SecretKey secretKey,
            AuthProperties properties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> defaults =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                "aud",
                values -> values != null && values.contains(properties.audience())
        );
        OAuth2TokenValidator<Jwt> tokenType = new JwtClaimValidator<String>(
                "token_type",
                "access"::equals
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                defaults,
                audience,
                tokenType
        ));
        return decoder;
    }
}