# API implementada nos blocos 2 a 5

Base: `/api/v1`. As rotas abaixo exigem autenticação. O frontend ainda é o esqueleto; o painel de operação será implementado no bloco 6.

| Método e rota | Resultado |
|---|---|
| POST `/assignors` | Cria cedente; retorna 201 e Location. |
| GET `/assignors/{id}` | Consulta cedente; retorna 200 ou 404. |
| GET `/assignors?page=0&size=20` | Lista cedentes em página numerada a partir de zero. |
| POST `/receivables` | Cria título AVAILABLE, versão zero; retorna 201 e Location. |
| GET `/receivables/{id}` | Consulta título; retorna 200 ou 404. |
| GET `/receivables?page=0&size=20&status=AVAILABLE` | Lista títulos; estado é filtro opcional. |
| POST `/fx-rates` | ADMIN registra cotação; 201/Location ou 409 por vigência duplicada. |
| GET `/fx-rates/{id}` | Consulta histórico por UUID; 200 ou 404. |
| GET `/fx-rates?page=0&size=20` | Histórico paginado por vigência/id decrescentes. |
| GET `/fx-rates/current` | Última cotação elegível até agora; 503 se ausente/expirada. |
| POST `/fx-rates/sync` | ADMIN sincroniza o mock; 201/Location se criada, 200 se já existe com mesmo valor. |
| POST `/pricing/simulations` | Simulação indicativa sem persistência financeira; 200 ou 503 em USD sem câmbio válido. |
| POST `/settlements` | Confirma com recálculo e Idempotency-Key UUID; 201/Location inclusive no replay exato. |
| GET `/settlements/{id}` | Snapshot original de auditoria; 200 ou 404. |
| GET `/settlements?from=...&to=...&assignorId=...&currency=USD&page=0&size=20` | Extrato histórico; período obrigatório, cedente/moeda opcionais, totais por moeda para todo o filtro. |

Listagens retornam `{items, page, size, total}`. Tamanho entre 1 e 100, padrão 20. Cedentes e recebíveis usam createdAt/id decrescentes; câmbio usa effectiveAt/id decrescentes. Página sem registros retorna 200 e lista vazia.

## Cadastro

Cedente:

```json
{"name":"Empresa Cedente Ltda."}
```

Recebível, substituindo assignorId pelo identificador criado e usando vencimento elegível na data da operação:

```json
{
  "assignorId": "f62796ab-2802-4c42-8ac5-24d67a729551",
  "faceValue": "100000.00",
  "dueDate": "2026-11-27",
  "type": "DUPLICATA",
  "paymentCurrency": "USD"
}
```

`faceValue` precisa ser string decimal positiva, com no máximo duas casas e 16 dígitos inteiros. Números JSON, expoentes, tipos ou moedas desconhecidos e campos extras são rejeitados. Tipos aceitos: DUPLICATA/CHEQUE; pagamento: BRL/USD. O cadastro não consulta câmbio. Vencimento precisa estar de 1 a 1080 dias após a data da operação em America/Sao_Paulo. Cedente inexistente no cadastro gera 400; consulta de recurso inexistente gera 404.

As respostas usam DTOs e faceValue como string de duas casas, com faceCurrency BRL, dueDate, type, paymentCurrency, status, version e createdAt UTC. Não são expostas entidades JPA ou relacionamentos internos.

## Autenticação e CSRF nesta etapa

O usuário configurado por APP_USERNAME/APP_PASSWORD continua sendo o usuário local do Spring Security. HTTP Basic e formulário padrão `/login` estão disponíveis. CSRF permanece ativo nas mutações: um cliente autenticado deve enviar também o token válido de sua sessão. Basic isoladamente não dispensa CSRF. Os testes HTTP usam o suporte oficial Spring Security Test para simular autenticação e token e passam pela cadeia real de filtros.

O usuário local recebe `APP_ROLES=OPERATOR` por padrão. Configure `APP_ROLES=ADMIN` para registrar ou sincronizar câmbio; as duas mutações já exigem esse papel e CSRF, retornando 403 para OPERATOR. O fluxo próprio de login/logout, endpoint dedicado de CSRF e contas separadas pertencem ao bloco 6. O health é público; dados e documentação exigem autenticação. Para testes manuais nesta etapa, use `/login`, obtenha o token CSRF do formulário e preserve o cookie de sessão; o token após autenticação pode ser obtido ao consultar novamente `/login` com essa sessão. Envie-o no cabeçalho `X-CSRF-TOKEN` junto do cookie nas mutações. A rotação do token no login exige usar o token da sessão autenticada.

## Câmbio

Exemplo de corpo para `POST /fx-rates` (ajuste a vigência ao momento da execução):

```json
{"rate":"5.4321","origin":"manual","effectiveAt":"2026-10-28T14:00:00Z"}
```

O par é fixo USD/BRL, direção BRL por USD. Rate é string positiva com até oito casas e oito dígitos inteiros; origem tem até 100 caracteres e vigência aceita até microssegundos. As respostas incluem id, pair, direction, rate, origin, effectiveAt e recordedAt. Futuras são armazenadas, mas ignoradas pela seleção atual. Exatamente 24 horas é válido. Mais detalhes e orçamento de retry em [câmbio e resiliência](currency.md).

## Simulação indicativa

Não exige cedente ou título persistidos. Use vencimento elegível na data atual; este exemplo reproduz C3 quando a operação é em 28/10/2026 e o câmbio local vigente é 5.4321:

```json
{"faceValue":"100000.00","dueDate":"2027-01-26","type":"DUPLICATA","paymentCurrency":"USD"}
```

A resposta inclui `indicative: true`, faceValue/faceCurrency, dueDate/type, presentValueBrl (`"92859.94"`), discountBrl (`"7140.06"`), paymentAmount (`"17094.67"`), paymentCurrency, months (3), baseRate (`"0.0100"`), spread (`"0.0150"`), operationAt, operationDate, businessTimezone, termConvention, roundingMode, policyVersion e fxRate com o snapshot completo. Valores/taxas são strings; em BRL fxRate é null. `operationAt` é capturado uma vez para prazo e câmbio. A taxa base configurável `PRICING_BASE_RATE` tem padrão 0.0100 e é devolvida no resultado. Não se aceita taxa, prazo ou cotação enviados pelo cliente para substituir o cálculo do servidor.

BRL não consulta câmbio; USD só consulta o banco, sem HTTP externo. Falta/expiração gera 503 `FX_RATE_UNAVAILABLE`. A simulação não insere nem altera cedentes, recebíveis, cotações ou liquidações. O preço é indicativo, sem reserva ou confirmação financeira; a confirmação usa o endpoint de liquidação separado.

## OpenAPI e verificação

Com o backend iniciado: `/swagger-ui/index.html` apresenta o contrato e `/v3/api-docs` retorna o OpenAPI gerado dos controllers/DTOs. O contrato declara strings monetárias, enums, parâmetros, respostas e autenticação Basic.

No diretório backend, com Java 21 e Docker ativos:

```sh
./mvnw verify
```

`ReceivablesIntegrationTest` exercita HTTP via MockMvc com PostgreSQL descartável real: cadastro, consulta, paginação, precisão, validação de entrada, autenticação, CSRF, erros e OpenAPI. Não usa H2 e não altera o banco do Compose.

`FxIntegrationTest` acrescenta câmbio/simulação; `FxProviderClientTest`, retries/timeouts HTTP. `SettlementSchemaTest` e `SettlementIntegrationTest` verificam permissões, confirmação de C1–C3, replay, conflitos, concorrência, rollback e perda de resposta. `StatementIntegrationTest` cobre filtros, paginação, snapshots, totais e inserção concorrente; `StatementQueryPlanTest` gera massa e planos reais.

## Liquidação

`POST /settlements` exige Idempotency-Key UUID e corpo `{receivableId, currency, expectedPaymentAmount, expectedFxRateId?}`. Currency deve coincidir com o cadastro; expectedPaymentAmount é string não negativa com até duas casas. ExpectedFxRateId é obrigatório em USD e ausente em BRL. O operador vem da autenticação. Sucesso 201/Location somente após commit; o corpo é o snapshot auditável, com valores, parâmetros, operador, versão, correlação e cotação original.

Retry com mesma chave e conteúdo normalizado devolve exatamente 201/corpo/Location originais, antes de consultar recebível/câmbio. Mesma chave com outro conteúdo gera IDEMPOTENCY_KEY_REUSED. Preço/UUID de cotação diferentes geram PRICE_CHANGED, exigindo nova simulação e confirmação. Título já liquidado ou disputa perdida gera RECEIVABLE_ALREADY_SETTLED. Espera pela mesma chave é limitada a 3 segundos; REQUEST_IN_PROGRESS retorna Retry-After: 1. Todos esses conflitos usam 409. Falha inesperada de persistência ou commit retorna 500 e desfaz todas as gravações. Em USD, falta de cotação local válida retorna 503.

`GET /settlements/{id}` não recalcula e preserva o histórico após novas cotações ou alteração do nome do cedente. Não há endpoints de alteração/exclusão de auditoria. Exemplos, retenção e semântica completa em [liquidação e retry](settlements.md); configuração de banco em [credenciais](database.md).

## Extrato

GET `/settlements` exige from/to como instantes com fuso em intervalo `[from,to)` de até 366 dias. Filtros opcionais assignorId UUID e currency BRL/USD; page a partir de zero, size padrão 20 e máximo 100. A resposta contém `{items,page,size,total,totalsByCurrency}`. Itens são snapshots SettlementResponse; totais têm `{currency,count,paymentTotal}` com valor decimal como string para todo o filtro, independente da página. Moedas sem registros são omitidas; página além da última permanece 200 e mantém contagem/totais.

Ordem fixa settledAt/id decrescente, sem parâmetro de ordenação SQL. Página/contagem/totais usam um único snapshot read-only REPEATABLE READ. Novas requisições podem ver novas confirmações. Entradas inválidas retornam 400 com campos e correlação. [Contrato detalhado, índices, massa e EXPLAIN ANALYZE](statements.md).
