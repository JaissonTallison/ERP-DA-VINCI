# ADR-0006: Backend — Java 25, Spring Boot 4.1, Gradle 9 com convenções

- Estado: Aceito
- Data: 2026-11-03

## Contexto

A stack foi definida pela direção técnica, condicionada à validação de compatibilidade do Spring Boot 4.1.

## Validação

- Spring Boot 4.1.2 (última da linha 4.1): documentação oficial declara suporte a Java 17 até 26 e Gradle 8.14+/9.x.
- Build da fundação executado com JDK 25.0.4 e Gradle 9.8.2: compilação com `-Xlint:all -Werror`, testes JUnit 6 e Spotless passando.

## Decisão

- Java 25 via _toolchain_; Spring Boot 4.1.x; Gradle 9.8.2 via wrapper com checksum.
- JDK provisionado automaticamente (Temurin 25, plugin `foojay-resolver-convention` + `gradle/gradle-daemon-jvm.properties`): o build não depende do JDK instalado na máquina. Motivo: a máquina de referência tinha apenas o JDK _headless_, sem `javac`.
- Dependências gerenciadas pelo BOM do Spring Boot como `platform()` nativo do Gradle (sem o plugin `io.spring.dependency-management`).
- Plugins de convenção: `davinci.java-conventions` (toolchain, compilação estrita, JUnit, JaCoCo, Spotless), `davinci.java-library`, `davinci.spring-boot-service`.
- Nulidade com JSpecify (`@NullMarked` por pacote), alinhado ao Spring Framework 7.
- Spring Data JDBC como padrão; JPA apenas quando o modelo de agregados justificar.
- Testes de integração com Testcontainers (bancos e brokers reais, nunca H2).

## Consequências

- Atualizações de patch do Spring Boot entram por Dependabot; mudança de linha (4.2) exige validação explícita.
- A convenção `davinci.spring-boot-service` só será exercitada de ponta a ponta quando o primeiro serviço existir (risco registrado).
