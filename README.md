# SRM Credit Engine

Plataforma de cessão de crédito com Java/Spring Boot MVC, Angular e PostgreSQL. Cadastra recebíveis em BRL, simula preços e registra aquisições com pagamento BRL/USD, histórico imutável, idempotência e controle otimista de concorrência.

## Estado e documentos

Blocos 2 a 7 implementados: motor decimal, cadastro, câmbio, liquidação, extrato, sessão/painel, observabilidade e Compose completo. A validação local inclui 197 testes backend, 48 Angular e verificações operacionais de falha do provedor/banco. O bloco 8 ainda reúne smoke final, aferição HTTP/p95, rastreabilidade e walkthrough. Execução local não comprova CI ou deploy remotos.

- [SPEC](SPEC.md): premissas, precisão, golden cases e critérios de aceite.
- [DECISIONS](DECISIONS.md): escolhas e cortes; [REVIEW](REVIEW.md): análise priorizada do Anexo A; [AI_USAGE](AI_USAGE.md): colaboração, erro real detectado e limites de autoria.
- [Arquitetura MVC](docs/architecture.md), [ER](docs/er.md) e [C4 níveis 1 e 2](docs/c4.md).
- [Motor/prazo](docs/pricing.md), [câmbio](docs/currency.md), [liquidação/retry](docs/settlements.md) e [extrato/planos SQL](docs/statements.md).
- [Sessão](docs/authentication.md), [painel](docs/workspace.md), [API](docs/api.md), [erros](docs/errors.md) e [credenciais do banco](docs/database.md).
- [Operação/diagnóstico](docs/operations.md), [observabilidade](docs/observability.md) e [testes/cobertura](docs/quality.md).

## Rodar com Docker Compose

Requer Docker com Compose v2. Maven, Java e Node do host não são necessários para construir as imagens.

```sh
cp .env.example .env
# Ajuste senhas e portas no .env antes de iniciar.
docker compose up --build -d --wait
docker compose ps -a
```

Abra [localhost:4200](http://localhost:4200). Entre com APP_USERNAME/APP_PASSWORD (OPERATOR) ou ADMIN_USERNAME/ADMIN_PASSWORD (ADMIN, também operador). Use nomes distintos e senhas de pelo menos 12 caracteres. COOKIE_SECURE=false serve apenas HTTP local; HTTPS exige terminação TLS e COOKIE_SECURE=true.

O Compose sobe PostgreSQL, bootstrap do login restrito, API, nginx com bundle Angular e mock de câmbio. Flyway aplica V1–V5; a aplicação usa DATABASE_USER/DATABASE_PASSWORD, enquanto migrações usam MIGRATION_USER/MIGRATION_PASSWORD. Bootstrap termina antes da API; nginx espera readiness. As portas publicadas ficam em loopback e são configuráveis por POSTGRES_PORT, BACKEND_PORT, FRONTEND_PORT e FX_MOCK_PORT.

Se já existe .env/volume, preserve o usuário/senha atuais do dono do banco em MIGRATION_USER/MIGRATION_PASSWORD, acrescente as variáveis ausentes do exemplo e mantenha outra credencial para srm_app. [Atualização sem apagar dados](docs/database.md#volume-já-existente). Não sobrescreva o arquivo real com o exemplo. `docker compose down` preserva dados; `down --volumes` apaga o histórico e só deve ser usado em projeto descartável.

## Percorrer a operação

1. Cadastre ou selecione um cedente. Preencha face em formato brasileiro, vencimento de 1 a 1080 dias, tipo DUPLICATA/CHEQUE e moeda BRL/USD.
2. Aguarde a simulação indicativa (300 ms após a última alteração válida). O painel mostra VP/deságio em BRL, pagamento, meses, base/spread, política e cotação quando USD.
3. Cadastre o recebível e confirme explicitamente a liquidação após a nova simulação dos dados persistidos. O servidor recalcula e só responde sucesso após commit.
4. Consulte recebíveis e extrato, com filtros remotos, paginação e totais separados por moeda. Depois de uma resposta perdida/recarga, verifique o estado antes de uma nova intenção.

Para habilitar USD, entre como ADMIN e use **Sincronizar cotação**. O mock fornece 5.4321 BRL por USD, com vigência fixada ao iniciar; não é dado de mercado. A sincronização é explícita. Após 24 horas, reinicie o mock e sincronize novamente. BRL não depende de câmbio; USD utiliza cotação local válida, sem chamada remota durante a liquidação. Sem cotação válida retorna 503 FX_RATE_UNAVAILABLE. [Limites de timeout/retry](docs/currency.md).

A confirmação usa Idempotency-Key UUID por ator/rota/intenção e hash do corpo. Retry preserva chave e corpo para recuperar status/JSON/Location originais; não recalcula nem cria outra liquidação. PRICE_CHANGED exige novo preço e aceite. Duas intenções para o mesmo título produzem um sucesso e um conflito, protegidos por @Version e UNIQUE(receivable_id). Não há transferência bancária, saldo ou edição/exclusão de liquidação.

## Diagnóstico e contrato

[Readiness](http://localhost:4200/actuator/health/readiness), [liveness](http://localhost:4200/actuator/health/liveness) e [health estático](http://localhost:4200/health) são públicos, sem detalhes internos. Banco fora resulta em readiness 503; o health estático do nginx não comprova disponibilidade SQL. A API não depende da saúde do mock para confirmar BRL/USD com cotação local válida.

Após login no painel, a mesma sessão permite abrir [Swagger UI](http://localhost:4200/swagger-ui/index.html), [OpenAPI JSON](http://localhost:4200/v3/api-docs) e [métricas](http://localhost:4200/actuator/metrics). Login/logout e todas as mutações exigem CSRF; [protocolo para clientes HTTP](docs/authentication.md). Não há HTTP Basic.

Logs JSON trazem timestamp UTC, evento, resultado e correlação. `settlements.confirmed` conta uma confirmação por moeda após commit; replay/rollback não incrementam. `pricing.duration` mede apenas o motor, não latência HTTP. Métricas são memória e não substituem o extrato SQL. [Consultas e limites](docs/observability.md).

```sh
docker compose logs --tail=100 backend
curl --fail http://localhost:4200/actuator/health/readiness
```

O [script operacional](scripts/verify-compose.py) verifica proxy, sessão, credencial SQL real, provedor parado, C1/C3, replay, métricas/logs e banco indisponível. Ele cria dados sintéticos e para/restaura dependências: execute apenas em projeto descartável com portas próprias, conforme [instruções](docs/operations.md).

## Desenvolvimento e testes

Versões fixadas: Java 21, Spring Boot 3.5.16, Angular 21.2.0, Node 22.16.0, TypeScript 5.9.2 e PostgreSQL 17.11. [Compatibilidade e referências](docs/versions.md). Com nvm, execute nvm use na raiz; ajuste JAVA_HOME para Java 21.

Para executar API/Angular no host: inicie postgres/bootstrap/fx-mock com Compose, exporte as variáveis do .env confiável no mesmo terminal e use os comandos abaixo. O proxy Angular aponta para localhost:8080; ajuste proxy.conf.json se mudar a porta da API.

```sh
docker compose up -d --wait postgres fx-mock
docker compose run --rm -T db-bootstrap
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run
```

Em outro terminal:

```sh
cd frontend
npm ci
npm start
```

Validação backend (Java 21 e Docker ativos), dentro de backend:

```sh
./mvnw -B -ntp clean verify
# Corrigir formatação, se necessário:
./mvnw spotless:apply
```

Validação Angular, dentro de frontend:

```sh
npm ci
npm run lint
npm test -- --reporters=default --reporters=junit --output-file=test-results.xml
npm run build
```

A [CI](.github/workflows/ci.yml) roda esses checks em PRs/pushes na main. Backend usa PostgreSQL real por Testcontainers: golden cases, migrations, permissões, rollback e concorrência, sem H2 ou skips por ausência de Docker. JaCoCo exige 80% linhas/70% branches no geral e 95%/90% por classe financeira/strategy. Relatórios Surefire/JaCoCo, planos SQL e JUnit Angular são publicados por 14 dias, inclusive após falhas quando disponíveis; publicação não torna o job aprovado. Falhas obrigatórias não são ignoradas. [Resultados e limites](docs/quality.md).

Imagens compilam artefatos; o builder backend usa -DskipTests package porque não tem Docker para Testcontainers. Isso não substitui clean verify na CI. O bundle Angular usa npm ci e build estrito. Os runtimes não carregam Maven/Node ou testes.

## Arquitetura e versionamento

Java/Spring Boot reúne tipagem, transações e segurança; Angular separa apresentação/API/estado; PostgreSQL oferece NUMERIC, constraints e atomicidade. O monólito MVC permite confirmar estado, snapshot e resultado idempotente na mesma transação. O model financeiro usa BigDecimal/strategies sem infraestrutura. O extrato usa SQL parametrizado com três consultas no mesmo snapshot REPEATABLE READ por resposta; páginas distintas podem observar novos commits. [Decisões e cortes](DECISIONS.md).

GitHub Flow: branch curta por funcionalidade a partir da main, Conventional Commits em inglês e integração por PR. Isso mantém uma mudança revisável por etapa sem branches permanentes adicionais. Cada PR explica problema, decisão e validação conforme o [template](.github/pull_request_template.md). Proteção de branch, aprovação externa e convites não são presumidos nem configurados pela aplicação.

## Planejamento OpenSpec

```sh
openspec status --change build-srm-credit-engine
openspec instructions apply --change build-srm-credit-engine --json
openspec validate build-srm-credit-engine --strict
```

[Proposta](openspec/changes/build-srm-credit-engine/proposal.md), [desenho](openspec/changes/build-srm-credit-engine/design.md), [tasks](openspec/changes/build-srm-credit-engine/tasks.md) e [seis specs](openspec/changes/build-srm-credit-engine/specs/). O bloco 8 permanece pendente; a mudança ainda não deve ser arquivada.
