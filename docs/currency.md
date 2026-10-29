# Câmbio e resiliência

O par único é `USD/BRL`: uma unidade de USD custa `rate` BRL. Valores são strings decimais positivas de até oito casas e oito dígitos inteiros. O histórico guarda UUID, origem, vigência e registro em UTC. `V2__create_fx_rates.sql` aplica unicidade por par/vigência; a API oferece somente inserção e consulta. Correções precisam de nova vigência, preservando o registro anterior. O papel de execução agora não recebe UPDATE/DELETE/TRUNCATE do histórico; migrations e conexão de negócio usam credenciais distintas, conforme docs/database.md.

`POST /api/v1/fx-rates` requer ADMIN e CSRF válido:

```json
{"rate":"5.4321","origin":"manual","effectiveAt":"2026-10-28T14:00:00Z"}
```

Substitua effectiveAt do exemplo por uma vigência atual para testar seleção/simulação; datas antigas não se tornam válidas por estarem no exemplo.

A precisão temporal máxima da entrada é de microssegundos, compatível com o PostgreSQL. Excesso é rejeitado antes de gravar, evitando colisões silenciosas por arredondamento da vigência. Origem tem de 1 a 100 caracteres. Criação retorna 201/Location; vigência repetida retorna 409 `FX_RATE_EXISTS`. Futuras podem ser armazenadas e consultadas pelo UUID.

`GET /api/v1/fx-rates` pagina o histórico por vigência/id decrescentes; `GET /api/v1/fx-rates/{id}` consulta o original. `GET /api/v1/fx-rates/current` seleciona a maior vigência cujo `effectiveAt` e `recordedAt` não ultrapassem o instante do Clock injetado. Exatamente 24 horas ainda é válido; mais que isso, ou ausência, retorna 503 `FX_RATE_UNAVAILABLE`. A seleção consulta exclusivamente o banco local.

## Sincronização

`POST /api/v1/fx-rates/sync` requer ADMIN e CSRF: retorna 201/Location quando cria e 200 quando a mesma cotação já existe. O cliente faz GET em `FX_PROVIDER_URL` (local: `http://localhost:8090/rate`). O JSON do provedor segue o mesmo contrato de registro manual; números JSON, campos extras, valores inválidos e vigência ausente são rejeitados.

São duas tentativas totais, com timeout de 1 segundo por resposta completa e pausa de 100 ms. O limite inclui corpo que para de chegar após os headers. Somente falha de conexão, timeout e HTTP 5xx geram retry. 4xx, redirecionamento e payload inválido encerram na primeira tentativa. A chamada externa ocorre antes de abrir a transação de gravação. Falha retorna 503 `FX_PROVIDER_UNAVAILABLE`, com correlação e detalhe seguro, sem substituir o histórico. O orçamento das duas chamadas é de aproximadamente 2,1 segundos; os testes HTTP aferem término abaixo de 3 segundos no ambiente local, incluindo dois timeouts.

Inserção usa `ON CONFLICT DO NOTHING`. Mesma vigência e valor recuperam o registro original, incluindo UUID, origem e instante de registro. Mesma vigência com valor diferente retorna 409; nem o valor nem a origem original são reescritos. Registro manual repetido sempre retorna 409.

Logs de `event=fx_sync_attempt` informam tentativa e resultado e recebem o correlationId da requisição; não registram payload ou URL. O contador `fx.sync.attempts`, consultável no Actuator, usa somente a dimensão limitada `outcome`: success, http_5xx, http_rejected, invalid_payload, timeout, connection_failure ou interrupted. Conta tentativas HTTP, sem representar confirmações financeiras.

## Mock local

O Compose inclui `fx-mock`, Python stdlib, em localhost:8090. `docker compose up --build -d fx-mock` inicia somente o mock. `GET /rate` retorna `FX_MOCK_RATE` (padrão 5.4321), origem local-mock e vigência fixada no início do processo. Reiniciar gera uma nova vigência; o mock não renova automaticamente a cotação nem a importa no backend. Após 24 horas, reinicie e sincronize novamente. Para execução direta: `python3 infra/fx-mock/server.py`; `FX_MOCK_EFFECTIVE_AT` permite fixar a vigência nesse modo. Não é uma fonte de cotação de mercado.

No backend executado fora do Compose, exporte `FX_PROVIDER_URL` ou use o padrão localhost:8090/rate. Quando o backend for orquestrado no Compose, deverá usar http://fx-mock:8090/rate; nesta etapa ele é iniciado no host. O usuário local tem `APP_ROLES=OPERATOR` por padrão; configure `APP_ROLES=ADMIN` e reinicie o backend para operações administrativas. Contas separadas e fluxo próprio de sessão continuam no bloco 6.

## Evidência automatizada

`FxIntegrationTest` usa PostgreSQL real para precisão/constraints, duplicidade, histórico, seleção temporal com Clock fixo, restrição por papel e sincronização idempotente. `FxProviderClientTest` usa servidor HTTP real descartável e testa recuperação 5xx, conexão recusada, ausência de retry em 4xx/payload inválido e timeouts nos headers e corpo, com contagem de tentativas e limite de 3 segundos. Nenhuma operação de precificação chama o provedor.
