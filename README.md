# SRM Credit Engine

Plataforma de cessão de crédito com backend Java/Spring Boot, frontend Angular e PostgreSQL. Recebíveis em BRL poderão ser precificados e adquiridos com pagamento em BRL ou USD.

## Estado

Blocos 2 a 5 implementados: cadastro/consulta, motor decimal, câmbio/simulação, liquidação idempotente/auditável e extrato SQL com filtros, paginação e totais por moeda, com credencial de banco restrita. O backend segue MVC com camadas concretas. C1/C2/C3 e integrações PostgreSQL passaram localmente; o frontend permanece no esqueleto.

A CI executa testes, lint, build e cobertura do backend, com relatórios por 14 dias. O painel Angular funcional será implementado no bloco 6. A autenticação provisória usa uma conta externa do Spring Security e CSRF; APP_ROLES seleciona OPERATOR (padrão) ou ADMIN. A gestão cambial já exige ADMIN; contas separadas e fluxo próprio de sessão pertencem ao bloco 6.

- [SPEC.md](SPEC.md): premissas, precisão, golden cases e critérios de aceite.
- [DECISIONS.md](DECISIONS.md): decisões de arquitetura e cortes.
- [AI_USAGE.md](AI_USAGE.md): colaboração com IA e evidências pendentes.
- [Arquitetura MVC](docs/architecture.md): responsabilidades das camadas.
- [Motor e prazo](docs/pricing.md), [câmbio e resiliência](docs/currency.md), [liquidação e retry](docs/settlements.md), [extrato e planos SQL](docs/statements.md), [credenciais do banco](docs/database.md), [API](docs/api.md), [erros](docs/errors.md), [ER](docs/er.md) e [testes/cobertura](docs/quality.md).
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
# Ajuste MIGRATION_PASSWORD, DATABASE_PASSWORD e APP_PASSWORD.
docker compose up -d --wait postgres
docker compose run --rm -T db-bootstrap
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run
```

Se .env já existir, preserve o dono atual do banco e sua senha em MIGRATION_USER/MIGRATION_PASSWORD; defina DATABASE_USER=srm_app e outra DATABASE_PASSWORD para execução. Acrescente as configurações ausentes do exemplo sem sobrescrever credenciais ou apagar o volume. Execute db-bootstrap antes de reiniciar a API. [Atualização de volume existente](docs/database.md#volume-já-existente). Para outra porta PostgreSQL, ajuste POSTGRES_PORT e a porta em DATABASE_URL. O Spring Boot iniciado pelo Maven não carrega .env automaticamente; exportar as variáveis no mesmo terminal é necessário.

Consulte [health](http://localhost:8080/actuator/health). O backend agora exige PostgreSQL e credenciais externas. Flyway aplica V1–V5 e Hibernate valida o schema; a conexão de negócio usa DATABASE_USER/DATABASE_PASSWORD e a migração usa MIGRATION_USER/MIGRATION_PASSWORD. O bootstrap cria o login restrito; V3/V4 adicionam auditoria e proteção do histórico. Nesta demonstração a API ainda recebe ambas as credenciais ao iniciar.

[Swagger UI](http://localhost:8080/swagger-ui/index.html) e [OpenAPI JSON](http://localhost:8080/v3/api-docs) exigem autenticação. O formulário padrão em /login permite entrar com APP_USERNAME/APP_PASSWORD; HTTP Basic também está disponível. Mutações exigem token CSRF da sessão, inclusive quando usam Basic. [Contrato e limites desta etapa](docs/api.md).

Frontend, em outro terminal:

```sh
cd frontend
npm ci
npm start
```

Abra [localhost:4200](http://localhost:4200). A página informa o estado inicial do projeto. O proxy de desenvolvimento encaminha /api para localhost:8080; esses endpoints serão implementados nos próximos blocos. Para portas ocupadas, use `SERVER_PORT=8081 ./mvnw spring-boot:run` e `npm start -- --port 4201`; ajuste o proxy quando a API for integrada.

O Compose contém PostgreSQL, db-bootstrap e mock HTTP de câmbio; backend/frontend ainda iniciam pelos comandos acima. Na raiz, `docker compose down` encerra o banco preservando os dados. Não apague volumes para solucionar migrations. A orquestração completa será adicionada no bloco 7.

## Câmbio e simulação

```sh
docker compose up --build -d fx-mock
```

O mock responde em [localhost:8090/rate](http://localhost:8090/rate), com vigência fixada ao iniciar. Ele não importa a cotação automaticamente; reiniciar o mock e sincronizar é necessário após expiração. Se alterar FX_MOCK_PORT, ajuste também FX_PROVIDER_URL para o backend no host.

- POST /api/v1/fx-rates registra cotação; POST /api/v1/fx-rates/sync importa o mock. Ambos exigem ADMIN e CSRF válido. Configure APP_ROLES=ADMIN antes de iniciar/reiniciar o backend para administrar com a conta local; contas simultâneas separadas vêm no bloco 6.
- GET /api/v1/fx-rates/current seleciona a cotação local registrada e vigente mais recente, de até 24 horas. Histórico e consulta por UUID preservam os registros.
- POST /api/v1/pricing/simulations recebe faceValue como string, dueDate, type e paymentCurrency. Retorna VP/deságio BRL, pagamento, prazo, base/spread, política, relógio e cotação quando USD, sem cadastrar recebível ou liquidação.

BRL independe de câmbio; USD sem cotação local válida retorna 503 FX_RATE_UNAVAILABLE. Simulação não chama o provedor. A sincronização usa duas tentativas de 1 segundo e intervalo de 100 ms, apenas em timeout/conexão/5xx. 4xx e payload inválido não geram retry. Tentativas têm logs correlacionados e contador fx.sync.attempts, com acesso autenticado em /actuator/metrics; logs JSON e métricas de confirmações continuam no bloco 7.

[Contratos e exemplos](docs/api.md), [política de câmbio, limites do mock e testes](docs/currency.md). A resposta é indicativa e não reserva preço; o recálculo na confirmação será implementado com a liquidação.

## Confirmar liquidação

POST /api/v1/settlements recebe receivableId, currency, expectedPaymentAmount como string e expectedFxRateId em USD. Envie Idempotency-Key UUID e o token CSRF da sessão. O ator vem da autenticação; o servidor recalcula e compara valor/cotação antes de confirmar.

A transação grava estado/versão, auditoria e resultado idempotente juntos e retorna 201/Location apenas após commit. GET /api/v1/settlements/{id} consulta o snapshot original, sem recálculo. Não há transferência bancária ou edição/exclusão de liquidação.

Após perda de resposta, repita chave e corpo: status, JSON e Location originais são recuperados antes de consultar câmbio. Outro conteúdo na mesma chave gera 409 IDEMPOTENCY_KEY_REUSED. Alteração do pagamento/cotação gera PRICE_CHANGED e exige nova simulação e aceite. Duas intenções sobre o mesmo título produzem um sucesso e um conflito, com uma única confirmação.

O histórico é protegido pelo login restrito real; somente status/version e conclusão de reserva podem ser alterados. A espera por chave concorrente é limitada a 3 segundos; REQUEST_IN_PROGRESS inclui Retry-After: 1. [Protocolo, exemplos, respostas e limites](docs/settlements.md).

## Consultar extrato

GET /api/v1/settlements aceita from/to obrigatórios, assignorId e currency opcionais, page e size. O período UTC é [início, fim), limitado a 366 dias; filtra o instante da liquidação e a moeda de pagamento. Página inicial zero, tamanho padrão 20 e máximo 100, com ordem settledAt/id decrescente.

Exemplo: /api/v1/settlements?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z&page=0&size=20. A resposta inclui items, page, size, total e totalsByCurrency. Contagem e totais abrangem todo o filtro, mesmo em página vazia; BRL e USD ficam separados e os valores são strings decimais.

Página, contagem e totais usam SQL parametrizado em uma transação read-only REPEATABLE READ própria. Inserção durante a leitura aparece somente na resposta seguinte; páginas de requisições distintas não mantêm um snapshot compartilhado. O histórico não usa câmbio atual nem recalcula preços.

O teste StatementQueryPlanTest carrega 10000 títulos/5000 liquidações sintéticas em PostgreSQL descartável e gera EXPLAIN ANALYZE/BUFFERS das consultas reais. O relatório fica em backend/target/statement-query-plans.txt e também está [registrado em docs](docs/statement-query-plans.txt). A carga SQL não executa confirmação/idempotência nem mede latência HTTP. [Contrato, massa e reprodução](docs/statements.md).

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

O verify inclui Spotless, 190 testes, integração em PostgreSQL real por Testcontainers, JAR e critérios JaCoCo. Docker deve estar ativo: falha ao acessá-lo reprova a suíte, sem substituir o banco por H2. ESLint/Prettier e build estrito continuam no frontend; testes Angular acompanharão as funcionalidades do painel.

O [workflow](.github/workflows/ci.yml) executa esses comandos, sem ignorar falhas obrigatórias, e publica Surefire, planos SQL e JaCoCo por 14 dias inclusive quando há falha e arquivos disponíveis. Cobertura mínima: backend 80% linhas/70% branches; cada classe financeira e strategy 95%/90%. A verificação local atual atingiu 96,78%/86,97% no geral, preservando todos os limites. [Resultados, critérios e teste negativo](docs/quality.md). A validação local não comprova execução remota do workflow desta branch. O [registro da estrutura inicial](docs/initial-validation.md) permanece como evidência daquela etapa.

## Planejamento OpenSpec

Com o OpenSpec CLI instalado, executar na raiz:

```sh
openspec status --change build-srm-credit-engine
openspec instructions apply --change build-srm-credit-engine --json
openspec validate build-srm-credit-engine --strict
```

As seis specs definem os contratos: [precificação](openspec/changes/build-srm-credit-engine/specs/receivable-pricing/spec.md), [câmbio](openspec/changes/build-srm-credit-engine/specs/currency-rates/spec.md), [liquidação](openspec/changes/build-srm-credit-engine/specs/settlement-integrity/spec.md), [extrato](openspec/changes/build-srm-credit-engine/specs/settlement-statements/spec.md), [painel](openspec/changes/build-srm-credit-engine/specs/operator-workspace/spec.md) e [operação](openspec/changes/build-srm-credit-engine/specs/operational-readiness/spec.md).
