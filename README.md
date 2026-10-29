# SRM Credit Engine

Plataforma de cessão de crédito com Java/Spring Boot MVC, Angular e PostgreSQL. Permite cadastrar cedentes e recebíveis em BRL, simular preços, confirmar aquisições com pagamento em BRL/USD e consultar o extrato com totais por moeda.

As operações usam precisão decimal, histórico imutável, idempotência e controle de concorrência. As premissas financeiras, os golden cases e os critérios de aceite estão na [SPEC](SPEC.md).

## Execução rápida

Requer Docker com Compose v2. Java, Maven e Node do host não são necessários para construir e executar as imagens.

```sh
cp .env.example .env
# Ajuste senhas e portas no .env antes de iniciar.
docker compose up --build -d --wait
docker compose ps -a
```

Abra [localhost:4200](http://localhost:4200), considerando a porta padrão. As credenciais são definidas no `.env`:

| Papel | Usuário | Senha | Permissões |
|---|---|---|---|
| OPERATOR | `APP_USERNAME` | `APP_PASSWORD` | Cadastro, simulação, liquidação e consulta |
| ADMIN | `ADMIN_USERNAME` | `ADMIN_PASSWORD` | Operações do painel e gestão de câmbio |

Use usuários distintos e senhas com pelo menos 12 caracteres. `COOKIE_SECURE=false` atende HTTP local; em HTTPS, configure terminação TLS e `COOKIE_SECURE=true`.

O Compose inicia PostgreSQL, bootstrap da credencial restrita, API, nginx com Angular e mock de câmbio. Flyway aplica as migrations automaticamente. As portas ficam em loopback e podem ser ajustadas no `.env`.

`docker compose down` preserva os dados. A opção `--volumes` apaga o banco. Para um ambiente já existente, siga a [atualização sem apagar dados](docs/database.md#volume-já-existente), preservando as credenciais atuais de migração.

## Fluxo de uso

1. Entre no painel e cadastre ou selecione um cedente.
2. Informe o valor de face em formato brasileiro, vencimento de 1 a 1080 dias, tipo `DUPLICATA`/`CHEQUE` e moeda de pagamento `BRL`/`USD`.
3. Confira a simulação: VP e deságio em BRL, pagamento, prazo, taxas e cotação quando aplicável.
4. Cadastre o recebível, revise a simulação dos dados persistidos e confirme a liquidação. O servidor recalcula o preço antes de concluir.
5. Consulte recebíveis e extrato, com filtros, paginação e totais separados por moeda.

Para operar em USD, entre como ADMIN e use **Sincronizar cotação**. O mock fornece `5.4321` BRL por USD, com vigência fixada ao iniciar. Após 24 horas, reinicie o mock e sincronize novamente. BRL funciona sem cotação; USD exige cotação local válida. [Política de câmbio](docs/currency.md).

Retries de confirmação preservam a mesma chave e corpo para recuperar o resultado original. Mudança de preço exige novo aceite; intenções concorrentes para o mesmo título resultam em um sucesso e um conflito. Após perda de resposta ou recarga, consulte o estado antes de iniciar outra intenção. [Liquidação e idempotência](docs/settlements.md).

## Estrutura e arquitetura

```text
backend/     API Spring Boot, migrations e testes Java
frontend/    Painel Angular e testes de interface
scripts/     Verificação operacional e carga HTTP
docs/        Arquitetura, contratos, operação e evidências
openspec/    Proposta, especificações e tarefas
```

O backend segue MVC com controllers, services, repositories, DTOs e model financeiro independente da infraestrutura. Java usa `BigDecimal` e estratégias de spread; PostgreSQL fornece `NUMERIC`, constraints e transações. Liquidação, snapshot e resposta idempotente são persistidos atomicamente. Angular separa apresentação, acesso à API e estado.

Consulte a [arquitetura](docs/architecture.md), o [diagrama ER](docs/er.md), os [diagramas C4](docs/c4.md) e as [decisões técnicas](DECISIONS.md).

## Desenvolvimento e testes

Versões fixadas: Java 21, Spring Boot 3.5.16, Angular 21.2.0, Node 22.16.0, TypeScript 5.9.2 e PostgreSQL 17.11. Use `nvm use` na raiz e configure `JAVA_HOME` para Java 21. [Compatibilidade](docs/versions.md).

Para executar a API no host, inicie as dependências e carregue as variáveis do seu `.env`:

```sh
docker compose up -d --wait postgres fx-mock
docker compose run --rm -T db-bootstrap
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run
```

Em outro terminal, inicie o frontend:

```sh
cd frontend
npm ci
npm start
```

O proxy Angular aponta para `localhost:8080`. Se alterar as portas do PostgreSQL ou do mock, ajuste também `DATABASE_URL` e `FX_PROVIDER_URL` para a execução no host; para a API, ajuste `frontend/proxy.conf.json`.

Execute a validação backend dentro de `backend`, com Java 21 e Docker ativos:

```sh
./mvnw -B -ntp clean verify
# Para corrigir formatação:
./mvnw spotless:apply
```

Execute a validação frontend dentro de `frontend`:

```sh
npm ci
npm run lint
npm test -- --reporters=default --reporters=junit --output-file=test-results.xml
npm run build
```

A [CI](.github/workflows/ci.yml) executa esses checks em PRs e pushes na `main`. O backend usa PostgreSQL real via Testcontainers. JaCoCo exige 80% de linhas/70% de branches no geral e 95%/90% por classe financeira ou estratégia. Relatórios de testes e cobertura ficam disponíveis por 14 dias. [Detalhes da qualidade](docs/quality.md) e [execução remota verificada](docs/ci.md).

## Resultados da validação

Execuções locais em 29/10/2026:

| Verificação | Resultado | Evidência |
|---|---|---|
| Backend | 198 testes, sem falhas, erros ou skips | [Testes e cobertura](docs/quality.md) |
| Cobertura Java | 97,12% de linhas e 86,26% de branches; gates aprovados | [Resumo](docs/evidence/quality-summary.txt) |
| Frontend | 48 testes, lint e build aprovados | [Testes e cobertura](docs/quality.md) |
| Fluxo completo | BRL/USD, sessão, replay, métricas e falhas de dependências; operação pelo Angular | [Smoke](docs/smoke.md) |
| Carga de referência | 1.000 requisições por rota; p95 de 16,628 ms na simulação e 17,311 ms no extrato | [Método e resultados](docs/performance.md) |
| Carga com 2 CPUs/4 GiB agregados | 100.000 requisições, 50.000 por rota; zero erros; p95 de 60,923 ms na simulação e 94,195 ms no extrato | [Método, cotas e limites](docs/performance-constrained.md) |

As medições usaram dez clientes e atenderam aos alvos de p95 de 300 ms para simulação e 500 ms para extrato. São testes locais com dados sintéticos e aquecimento. O perfil restrito usa cotas por serviço cuja soma é 2 CPUs/4 GiB; gerador e host ficam fora desse orçamento. O relatório preserva a tentativa interrompida e descreve as limitações da coleta. Os resultados não representam SLA de produção.

A [matriz de rastreabilidade](docs/traceability.md) relaciona 32 requisitos e 60 cenários aos testes e às evidências. O [roteiro de demonstração](docs/walkthrough.md) cobre golden cases, concorrência e extensão de estratégia.

## API e operação

Após login no painel, a mesma sessão permite acessar [Swagger UI](http://localhost:4200/swagger-ui/index.html), [OpenAPI JSON](http://localhost:4200/v3/api-docs) e [métricas](http://localhost:4200/actuator/metrics). Clientes HTTP devem seguir o [protocolo de sessão e CSRF](docs/authentication.md).

```sh
docker compose logs --tail=100 backend
curl --fail http://localhost:4200/actuator/health/readiness
```

Readiness verifica a disponibilidade do banco; liveness permanece independente dele. Logs JSON incluem timestamp UTC e correlação. O [guia operacional](docs/operations.md) reúne diagnóstico e instruções do script de smoke, que deve ser usado em ambiente descartável porque cria dados e interrompe dependências.

## Documentação

| Tema | Referências |
|---|---|
| Requisitos e planejamento | [SPEC](SPEC.md), [proposta](openspec/changes/build-srm-credit-engine/proposal.md), [desenho](openspec/changes/build-srm-credit-engine/design.md), [tarefas](openspec/changes/build-srm-credit-engine/tasks.md) |
| Regras de negócio | [Motor e prazo](docs/pricing.md), [câmbio](docs/currency.md), [liquidação](docs/settlements.md), [extrato](docs/statements.md) |
| Contratos e segurança | [API](docs/api.md), [erros](docs/errors.md), [sessão](docs/authentication.md), [credenciais e permissões SQL](docs/database.md) |
| Interface e operação | [Painel](docs/workspace.md), [operação](docs/operations.md), [observabilidade](docs/observability.md) |
| Revisão e autoria | [Análise do Anexo A](REVIEW.md), [uso de IA](AI_USAGE.md) |

O projeto usa GitHub Flow, Conventional Commits em inglês e PRs conforme o [template](.github/pull_request_template.md). As especificações OpenSpec podem ser verificadas com `openspec validate build-srm-credit-engine --strict`.
