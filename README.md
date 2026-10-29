# SRM Credit Engine

Plataforma de cessão de crédito com backend Java/Spring Boot, frontend Angular e PostgreSQL. Recebíveis em BRL poderão ser precificados e adquiridos com pagamento em BRL ou USD.

## Estado

Bloco 2 implementado: cadastro/consulta de cedentes e recebíveis, motor financeiro decimal, strategies DUPLICATA/CHEQUE, política de prazo, migration e erros correlacionados. O backend segue MVC com camadas concretas. C1/C2/C3 e integrações PostgreSQL passaram localmente; o frontend permanece no esqueleto.

A CI executa testes, lint, build e cobertura do backend, com relatórios por 14 dias. Câmbio/simulação HTTP, liquidação, extrato e painel funcional serão implementados nos próximos blocos. A autenticação provisória usa o usuário externo do Spring Security e CSRF; o fluxo próprio de sessão/papéis pertence ao bloco 6.

- [SPEC.md](SPEC.md): premissas, precisão, golden cases e critérios de aceite.
- [DECISIONS.md](DECISIONS.md): decisões de arquitetura e cortes.
- [AI_USAGE.md](AI_USAGE.md): colaboração com IA e evidências pendentes.
- [Arquitetura MVC](docs/architecture.md): responsabilidades das camadas.
- [Motor e prazo](docs/pricing.md), [API](docs/api.md), [erros](docs/errors.md), [ER](docs/er.md) e [testes/cobertura](docs/quality.md).
- [Proposta](openspec/changes/build-srm-credit-engine/proposal.md), [desenho](openspec/changes/build-srm-credit-engine/design.md) e [tasks](openspec/changes/build-srm-credit-engine/tasks.md).

Java/Spring Boot oferece tipagem forte, transações e segurança; Angular organiza formulário, HTTP e estado tipados; PostgreSQL fornece precisão decimal, integridade referencial e transações ACID. Um monólito permite confirmar cada aquisição em uma única transação, sem coordenação entre serviços.

## Etapas

1. Especificações e documentação inicial.
2. Estrutura inicial: Maven Wrapper, Angular, versões fixadas, lint e CI básica.
3. Motor financeiro e cadastro — bloco 2 das tasks.
4. Câmbio e simulação — bloco 3.
5. Liquidação, idempotência e auditoria — bloco 4.
6. Extrato analítico — bloco 5.
7. Autenticação e painel — bloco 6.
8. Operação e documentação — bloco 7.
9. Validação integrada e preparação da defesa — bloco 8.

## Versionamento e revisão

Adotar GitHub Flow: branches curtas por funcionalidade a partir da main, commits atômicos em inglês no formato Conventional Commits e integração por pull request. O fluxo mantém as mudanças revisáveis sem branches permanentes adicionais. Implementações não devem ser feitas diretamente na main.

Exemplos de branches: docs/project-specifications, chore/initial-structure e feat/receivable-pricing. Cada PR deve explicar o problema, a decisão e a validação real, conforme o [template](.github/pull_request_template.md). A CI valida pushes na main e pull requests, com jobs separados para backend e frontend. As suítes funcionais serão incluídas com as features; execução remota ainda não foi comprovada.

## Executar localmente

Use Java 21 e Node 22.16.0, conforme [.java-version](.java-version), [.nvmrc](.nvmrc) e [toolchain](docs/versions.md). Com nvm instalado, `nvm install` e `nvm use` na raiz selecionam o Node. Confirme `java -version` e ajuste JAVA_HOME para Java 21.

Primeira execução, na raiz:

```sh
cp .env.example .env
# Ajuste DATABASE_PASSWORD e APP_PASSWORD no arquivo local.
docker compose up -d --wait postgres
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run
```

Se .env já existir, acrescente APP_USERNAME/APP_PASSWORD e DATABASE_URL sem sobrescrever suas configurações. Para outra porta PostgreSQL, ajuste POSTGRES_PORT e a porta em DATABASE_URL. O Spring Boot iniciado pelo Maven não carrega .env automaticamente; exportar as variáveis no mesmo terminal é necessário.

Consulte [health](http://localhost:8080/actuator/health). O backend agora exige PostgreSQL e credenciais externas. Flyway aplica V1 e Hibernate valida o schema; esta etapa usa o mesmo usuário para migration/execução. A separação e as permissões restritas serão implementadas no bloco 4.

[Swagger UI](http://localhost:8080/swagger-ui/index.html) e [OpenAPI JSON](http://localhost:8080/v3/api-docs) exigem autenticação. O formulário padrão em /login permite entrar com APP_USERNAME/APP_PASSWORD; HTTP Basic também está disponível. Mutações exigem token CSRF da sessão, inclusive quando usam Basic. [Contrato e limites desta etapa](docs/api.md).

Frontend, em outro terminal:

```sh
cd frontend
npm ci
npm start
```

Abra [localhost:4200](http://localhost:4200). A página informa o estado inicial do projeto. O proxy de desenvolvimento encaminha /api para localhost:8080; esses endpoints serão implementados nos próximos blocos. Para portas ocupadas, use `SERVER_PORT=8081 ./mvnw spring-boot:run` e `npm start -- --port 4201`; ajuste o proxy quando a API for integrada.

O Compose contém PostgreSQL, com healthcheck e volume persistente; backend/frontend ainda iniciam pelos comandos acima. Na raiz, `docker compose down` encerra o banco preservando os dados. Não apague volumes para solucionar migrations. A orquestração completa será adicionada no bloco 7.

## Verificações e CI

No backend:

```sh
./mvnw -B -ntp clean verify
# Para corrigir formatação:
./mvnw spotless:apply
```

No frontend:

```sh
npm ci
npm run lint
npm run build
```

O verify inclui Spotless, 74 testes, integração em PostgreSQL real por Testcontainers, JAR e critérios JaCoCo. Docker deve estar ativo: falha ao acessá-lo reprova a suíte, sem substituir o banco por H2. ESLint/Prettier e build estrito continuam no frontend; testes Angular acompanharão as funcionalidades do painel.

O [workflow](.github/workflows/ci.yml) executa esses comandos, sem ignorar falhas obrigatórias, e publica Surefire/JaCoCo por 14 dias inclusive quando há falha e arquivos disponíveis. Cobertura mínima: backend 80% linhas/70% branches; cada classe financeira e strategy 95%/90%. A verificação local atingiu 97,08%/82,56% no geral. [Resultados, critérios e teste negativo](docs/quality.md). A validação local não comprova execução remota do workflow desta branch. O [registro da estrutura inicial](docs/initial-validation.md) permanece como evidência daquela etapa.

## Planejamento OpenSpec

Com o OpenSpec CLI instalado, executar na raiz:

```sh
openspec status --change build-srm-credit-engine
openspec instructions apply --change build-srm-credit-engine --json
openspec validate build-srm-credit-engine --strict
```

As seis specs definem os contratos: [precificação](openspec/changes/build-srm-credit-engine/specs/receivable-pricing/spec.md), [câmbio](openspec/changes/build-srm-credit-engine/specs/currency-rates/spec.md), [liquidação](openspec/changes/build-srm-credit-engine/specs/settlement-integrity/spec.md), [extrato](openspec/changes/build-srm-credit-engine/specs/settlement-statements/spec.md), [painel](openspec/changes/build-srm-credit-engine/specs/operator-workspace/spec.md) e [operação](openspec/changes/build-srm-credit-engine/specs/operational-readiness/spec.md).
