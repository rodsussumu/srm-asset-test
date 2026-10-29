# Toolchain

Versões fixadas para a estrutura inicial:

| Componente | Versão | Onde |
|---|---|---|
| Java | 21 LTS | .java-version e backend/pom.xml |
| Spring Boot | 3.5.16 | parent Maven |
| Maven | 3.9.16 | Maven Wrapper 3.3.4, distribuição only-script |
| Node | 22.16.0 | .nvmrc |
| npm | 10.9.2 | packageManager do frontend |
| Angular / CLI / build | 21.2.0 | package.json e lockfile |
| TypeScript | 5.9.2 | package.json e lockfile |
| PostgreSQL | 17.11-alpine | compose.yaml e Testcontainers |
| Springdoc OpenAPI | 2.8.17 | backend/pom.xml |
| JaCoCo | 0.8.16 | backend/pom.xml |
| Mock cambial | Python 3.13-alpine, stdlib | infra/fx-mock/Dockerfile |

As dependências diretas do frontend têm versões exatas; npm ci usa o lockfile. O backend herda versões gerenciadas do parent Spring Boot fixado; o Wrapper fixa a distribuição Maven. Java fixa a linha LTS, não um patch específico do JDK. O tag da imagem fixa o patch PostgreSQL, não o digest da imagem.

Compatibilidade consultada em 28/10/2026:

- [Requisitos Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html): Java 17–25 e Maven ≥3.6.3; Java 21/Maven 3.9.16 estão nessa faixa.
- [Matriz Angular](https://angular.dev/reference/versions): Angular 21.2 aceita Node ^22.12.0, TypeScript ≥5.9 e <6 e RxJS ^7.4.0; as versões escolhidas são compatíveis.
- [Política PostgreSQL](https://www.postgresql.org/support/versioning/): série 17 suportada até novembro de 2029; patch 17.11 listado como atual na consulta.

As escolhas não significam usar a versão mais recente de cada ferramenta. Atualizações devem preservar a compatibilidade e passar nas verificações do projeto.
