package br.com.davinci.erp.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl URL do Keycloak regional
 * @param realm realm de usuários da região
 * @param clientRegistrationId registro OAuth2 (client_credentials) usado na API administrativa
 */
@ConfigurationProperties("identity.keycloak")
public record KeycloakProperties(String baseUrl, String realm, String clientRegistrationId) {}
