package br.com.davinci.erp.identity.config;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;
import static org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver.principal;

import br.com.davinci.erp.identity.keycloak.OrganizationConflictException;
import br.com.davinci.erp.identity.tenant.InvalidTenantEventException;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class IdentityConfig {

    static final String DEAD_LETTER_TOPIC = "tenant-registry.tenant.v1-dlt";

    /** Estático: roda antes dos demais beans (e da conexão com o banco). */
    @Bean
    static RequiredSettings requiredSettings(Environment environment) {
        return new RequiredSettings(environment);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Sem API pública por enquanto: só os probes de saúde ficam abertos. */
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /** Tokens da conta de serviço (client_credentials), renovados automaticamente antes de expirar. */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    @Bean
    RestClient keycloakAdmin(
            RestClient.Builder builder, OAuth2AuthorizedClientManager clients, KeycloakProperties keycloak) {
        var oauth = new OAuth2ClientHttpRequestInterceptor(clients);
        oauth.setPrincipalResolver(new RequestAttributePrincipalResolver());
        return builder.clone()
                .baseUrl(keycloak.baseUrl() + "/admin/realms/" + keycloak.realm())
                .defaultRequest(request -> request.attributes(clientRegistrationId(keycloak.clientRegistrationId()))
                        .attributes(principal("identity-service")))
                .requestInterceptor(oauth)
                .build();
    }

    /** Erros transitórios são repetidos; mensagens fora do contrato ou conflitos vão direto para a dead letter. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka, ConsumerProperties consumer) {
        var handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafka),
                new FixedBackOff(consumer.retryInterval().toMillis(), consumer.retryAttempts()));
        handler.addNotRetryableExceptions(InvalidTenantEventException.class, OrganizationConflictException.class);
        return handler;
    }

    @Bean
    NewTopic tenantDeadLetterTopic(ConsumerProperties consumer) {
        return TopicBuilder.name(DEAD_LETTER_TOPIC)
                .partitions(consumer.deadLetterPartitions())
                .replicas(consumer.deadLetterReplicas())
                .build();
    }
}
