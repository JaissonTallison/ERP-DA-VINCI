package br.com.davinci.erp.tenantregistry.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class RequiredSettingsTest {

    @Test
    void falhaListandoTudoQueFaltaInclusivePlaceholderSemVariavel() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "${DATABASE_URL}");

        assertThatThrownBy(() ->
                        new RequiredSettings(environment).postProcessBeanFactory(new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url")
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void sobeQuandoTudoEstaDefinido() {
        var environment = new MockEnvironment();
        RequiredSettings.KEYS.forEach(key -> environment.setProperty(key, "valor"));

        assertThatCode(() -> new RequiredSettings(environment).postProcessBeanFactory(new DefaultListableBeanFactory()))
                .doesNotThrowAnyException();
    }
}
