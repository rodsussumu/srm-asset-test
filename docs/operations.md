# Execução e diagnóstico local

## Subida completa

Pré-requisitos: Docker com Compose v2 e recursos para backend, PostgreSQL, nginx e mock. Não é necessário Maven/Node/Python no host para subir as imagens.

```sh
cp .env.example .env
# Edite .env: senhas distintas, usuários e portas locais.
docker compose up --build -d --wait
docker compose ps -a
```

Senhas da aplicação exigem pelo menos 12 caracteres e nomes OPERATOR/ADMIN distintos. Uma configuração antiga precisa de ADMIN_USERNAME/ADMIN_PASSWORD e, se houver volume anterior, deve preservar as credenciais atuais de migração. O arquivo real não é versionado. [Credenciais e atualização sem apagar volume](database.md).

| Serviço | Porta padrão no host | Prontidão |
|---|---|---|
| frontend/nginx | 127.0.0.1:4200 | GET `/health`: entrega estática |
| backend | 127.0.0.1:8080 | GET `/actuator/health/readiness`: estado da aplicação e conexão SQL |
| postgres | 127.0.0.1:5432 | pg_isready |
| fx-mock | 127.0.0.1:8090 | GET `/rate` dentro do contêiner |
| db-bootstrap | sem porta | termina com código 0 antes da API |

O healthcheck do backend usa somente bash disponível na imagem JRE; não depende de curl. Hikari limita aquisição de conexão a 2 segundos e validação a 1 segundo, permitindo diagnosticar banco indisponível sem longa espera padrão. nginx depende de backend saudável para a primeira subida. A API não depende da saúde do mock: usar cotação local é uma decisão de negócio, não uma chamada remota durante a liquidação.

Acesse http://localhost:4200 e entre com a conta configurada; o proxy mantém cookie/CSRF na mesma origem. O health geral/liveness/readiness é público sem detalhes. Métricas e OpenAPI exigem sessão: `/actuator/metrics`, `/swagger-ui/index.html`, `/v3/api-docs`. HTTPS depende de terminação TLS externa; use COOKIE_SECURE=true nesse ambiente. [Sessão](authentication.md), [painel](workspace.md), [observabilidade](observability.md).

No build da imagem backend, `-DskipTests package` compila sem executar integrações: o builder não tem Docker para Testcontainers. Isso não é validação de testes; a CI executa `clean verify` completo com Docker disponível. Dependências Maven usam cache BuildKit, e o bundle Angular usa npm ci/lockfile. As imagens de runtime não contêm o toolchain do frontend ou o código de teste.

## Diagnóstico

```sh
docker compose logs --tail=100 backend
curl --fail http://localhost:4200/health
curl --fail http://localhost:4200/actuator/health/readiness
curl --fail http://localhost:4200/actuator/health/liveness
```

`/health` de nginx não prova que o banco funciona. Se o banco cair, readiness retorna 503 com `{"status":"DOWN"}`, enquanto liveness continua UP. Depois da recuperação, readiness volta a UP; não é necessário apagar dados. Healthchecks indicam saúde, não reiniciam automaticamente um processo com dependência indisponível.

Se o provedor cair, sincronização retorna 503 em até duas tentativas de 1 segundo, separadas por 100 ms. Observe `fx_sync_attempt` e `fx.sync.attempts`. BRL continua independente de FX e USD usa apenas a cotação local vigente com até 24h. Sem cotação válida, USD retorna FX_RATE_UNAVAILABLE; não há fallback silencioso. O mock fixa sua vigência ao iniciar: reinicie-o e sincronize explicitamente quando a taxa expirar.

```sh
docker compose restart fx-mock
# Entre como ADMIN e use Sincronizar cotação no painel.
```

`docker compose down` preserva o volume. `down --volumes` apaga o histórico e fica restrito a projetos descartáveis. Não use esse reset para resolver senha incorreta ou atualizar migrations em um ambiente que precisa preservar liquidações.

## Verificação operacional reproduzível

O script `scripts/verify-compose.py` usa Python 3.9+ com biblioteca padrão e dados sintéticos. **Executa somente contra um projeto descartável**: cria cedente/recebíveis/liquidações e para/restaura o mock e o PostgreSQL desse projeto. Pressupõe valores padrão de base (0.0100) e mock (5.4321), banco novo e nenhuma outra confirmação concorrente, para aferir C1/C3 e contar exatamente dois eventos novos. Não é teste de carga.

Para preservar o ambiente habitual, copie .env.example para um arquivo local de teste, mantenha senhas apenas demonstrativas e altere POSTGRES_PORT/BACKEND_PORT/FRONTEND_PORT/FX_MOCK_PORT (ex.: 55437/18087/14207/18097). O nome do projeto separa volume/rede, mas as portas também precisam ser distintas.

```sh
# .env.operational é um arquivo local confiável, com valores compatíveis com shell.
set -a
. ./.env.operational
set +a
docker compose -p srm-operational --env-file .env.operational up --build -d --wait
python3 -u scripts/verify-compose.py --project srm-operational --env-file .env.operational --base-url http://127.0.0.1:14207
docker compose -p srm-operational --env-file .env.operational down --volumes
```

Login é lido das variáveis exportadas; `--env-file` controla a interpolação do Compose, não carrega senhas automaticamente no script. O script não imprime credenciais. Apenas o projeto indicado recebe comandos stop/start. O fluxo restaura as dependências em finally após falhas; se a execução for interrompida à força, confira os serviços antes de prosseguir.


## Evidência local — 29/10/2026

O projeto descartável srm-new-task7 foi construído com docker compose up --build -d --wait e iniciou PostgreSQL 17.11, API, nginx e mock saudáveis, com bootstrap encerrado em código zero. Portas utilizadas: PostgreSQL 55447, API 18087, nginx 14207 e mock 18097. O build executou package do backend e build Angular (305,17 kB inicial); a suíte completa foi executada separadamente no host.

A verificação operacional retornou exit 0: o login SQL real srm_app mostrou conexões da aplicação, INSERT permitido e UPDATE/DELETE de histórico negados. Pelo proxy, verificou sessão HttpOnly/SameSite=Lax, CSRF, correlação, sincronização e duas operações C1/C3. Com o provedor parado, BRL e USD confirmaram usando cotação local, três replays por operação preservaram bytes/Location, contadores cresceram uma vez por moeda e o extrato apresentou totais separados. Sincronização com provedor fora falhou em menos de 3 segundos.

Com PostgreSQL parado, readiness retornou 503 com apenas status DOWN, liveness continuou 200 e a readiness recuperou após start. Os logs reais mostraram JSON/UTC, outcome, correlação e identificadores de auditoria, exatamente dois eventos de confirmação e ausência dos canários/senhas externas testados. [Saída da execução](evidence/operational-checks.txt).

Não houve alteração do .env ou volumes habituais. Esses checks não medem p95 nem comprovam CI/deploy remotos. O bloco 8 permanece responsável pela validação final, aferição e walkthrough. [Qualidade](quality.md).
