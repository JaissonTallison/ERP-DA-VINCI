package br.com.davinci.erp.organization.shared.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Falha a subida, antes de criar qualquer bean, se faltar configuração obrigatória (credenciais e endereços não têm
 * valor padrão fora do perfil {@code local}).
 */
final class RequiredSettings implements BeanFactoryPostProcessor, EnvironmentAware {

    static final List<String> KEYS = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.flyway.user",
            "spring.flyway.password",
            "spring.kafka.bootstrap-servers",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri");

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
