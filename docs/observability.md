# Observabilidade

## Logs JSON

Spring Boot emite Logstash JSON no console. UtcTimestampJsonCustomizer normaliza `@timestamp` para UTC com `Z`, sem mudar o Clock nem o fuso financeiro. Eventos da aplicação têm `event` e `outcome`; `level` é o nível do logger. Eventos de requisições incluem correlationId no MDC e no cabeçalho de resposta. Logs de inicialização/bibliotecas continuam em JSON, mas não representam uma operação financeira.

| Evento | Campos próprios | Momento |
|---|---|---|
| `http_request` | method, status, outcome | fim do processamento; não registra path/query/body/headers |
| `request_failed` | code, status, outcome, actorId/receivableId quando disponíveis | handler semântico ou segurança; sem mensagem SQL/stack de negócio |
| `settlement_confirmed` | actorId, receivableId, settlementId, outcome | retorno do proxy transacional após commit; nunca no replay |
| `fx_sync_attempt` | attempt, outcome | tentativa de provedor; resultado limitado a valores internos |

Um X-Correlation-ID UUID válido é normalizado; outro valor é descartado e substituído por UUID gerado. Não aceitamos texto arbitrário do header no MDC. A correlação do pedido atual permite investigar o replay; o snapshot reproduzido mantém a correlação da confirmação original. A ausência de um novo evento de confirmação no retry é intencional.

As chamadas de logging não incluem senhas, cookies, Authorization, CSRF, Idempotency-Key, nomes de cedentes, body ou resposta financeira completa. UUIDs/ator nos logs servem à investigação e requerem controle de acesso e retenção em uma implantação real. Não são labels de métricas. Um erro de dependência pode gerar logs próprios da biblioteca; não configure DEBUG de SQL/HTTP em ambiente com dados reais.

Para filtrar eventos no Compose com Python padrão:

```sh
docker compose logs --no-color --no-log-prefix backend | python3 -c 'import sys,json; [print(json.dumps(e,ensure_ascii=False)) for l in sys.stdin if l.startswith("{") and (e:=json.loads(l)).get("event")=="settlement_confirmed"]'
```

Para relacionar uma falha, filtre `correlationId` do Problem Details/cabeçalho. Eventos de sucesso são evidência operacional, não a autoridade de saldo ou contagem financeira.

## Métricas

| Nome Micrometer | Dimensões | Semântica |
|---|---|---|
| `settlements.confirmed` | currency = BRL/USD | uma confirmação nova depois do commit; inicializa em zero para cada moeda |
| `pricing.duration` | currency = BRL/USD; outcome = success/failure | tempo somente de `PricingEngine.calculate`, em simulação ou confirmação; não inclui busca cambial, HTTP ou commit |
| `fx.sync.attempts` | outcome limitado | tentativas externas, incluindo sucesso, timeout, rejeição e payload inválido |

O timer nasce na primeira execução do cálculo. Entradas recusadas antes de chegar ao motor não têm amostra; um PRICE_CHANGED pode ter cálculo e nenhuma confirmação. Replay é resolvido antes de calcular e não adiciona amostra. IDs de usuário/recebível/liquidação/chave e valores monetários não são tags.

Actuator está protegido por sessão, por exemplo após autenticar conforme [protocolo](authentication.md):

```sh
curl --cookie /tmp/srm.cookies 'http://localhost:4200/actuator/metrics/settlements.confirmed?tag=currency:BRL'
curl --cookie /tmp/srm.cookies 'http://localhost:4200/actuator/metrics/pricing.duration?tag=currency:USD'
```

Actuator informa COUNT/TOTAL_TIME/MAX do timer; não há exportador Prometheus ou histograma configurado nesta etapa. Para confirmações por minuto, um coletor externo precisa amostrar o contador e tratar reinícios; esse coletor não faz parte do Compose. A duração do cálculo não representa p95 HTTP, que será medido no bloco 8.

O contador é memória do processo. Reinícios zeram-no, e existe uma janela entre o commit e a emissão na qual uma queda pode perder o evento. O extrato SQL é a autoridade para reconciliação. Retry financeiro não refaz a métrica perdida. Não se deve somar métricas após reinício como se fossem um ledger.

## Verificação

SettlementIntegrationTest lê o contador dentro da conclusão idempotente e comprova que ainda não cresceu antes do commit. Depois confirma uma vez e repete três vezes sem novo incremento/amostra; falhas antes/depois do INSERT e no commit também preservam o contador. BusinessMetricsTest verifica propagação de falha do cálculo e tags limitadas.

A verificação [do Compose](operations.md) testa logs reais do container: campos JSON, UTC, correlação, identificadores da confirmação e ausência de canários/senhas externas. Também verifica contadores por moeda, replay, queda do provedor e diagnóstico sem banco. Não depende de inferir sucesso a partir de um log emitido antes do commit.

Referências do mecanismo utilizado: [logging estruturado do Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/reference/features/logging.html) e [customizador de membros JSON](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/logging/structured/StructuredLoggingJsonMembersCustomizer.html).

O mock desabilita o log de requisições de BaseHTTPRequestHandler, evitando paths arbitrários nos logs. A conferência isolada de /rate e de um path inválido com canário verificou respostas 200/404 e logs vazios.
