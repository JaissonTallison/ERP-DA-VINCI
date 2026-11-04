package br.com.davinci.erp.identity.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Falha a subida, antes de criar qualquer bean, se faltar configuração obrigatória. Credenciais e endereços não têm
 * valor padrão fora do perfil `local`; sem esta checagem, o Spring manteria o texto `${VARIAVEL}` literal e o serviço
 * poderia subir usando-o como senha.
 */
final class RequiredSettings implements BeanFactoryPostProcessor, EnvironmentAware {

    static final List<String> KEYS = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.kafka.bootstrap-servers",
            "spring.security.oauth2.client.registration.keycloak-admin.client-secret",
            "spring.security.oauth2.client.provider.keycloak.token-uri",
            "identity.keycloak.base-url");

    private Environment environment;

    RequiredSettings(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> missing = KEYS.stream().filter(this::isMissing).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Configuração obrigatória ausente: " + missing
                    + ". Defina as variáveis de ambiente correspondentes ou, em desenvolvimento, use o perfil local.");
        }
    }

    private boolean isMissing(String key) {
        try {
            String value = environment.getProperty(key);
            return value == null || value.isBlank();
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return true;
        }
    }
}
