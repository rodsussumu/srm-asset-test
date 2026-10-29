# Extrato de liquidações

`GET /api/v1/settlements` exige autenticação e consulta os snapshots originais, sem recalcular valores nem consultar câmbio atual. Todos os cedentes pertencem ao mesmo fundo nesta demonstração. OPERATOR e ADMIN podem consultar.

| Parâmetro | Regra |
|---|---|
| from | Instante ISO-8601 com fuso, obrigatório; início inclusivo. |
| to | Instante ISO-8601 com fuso, obrigatório; fim exclusivo, após from e a até 366 dias. |
| assignorId | UUID opcional; ausência inclui todos os cedentes. UUID válido sem registros retorna vazio. |
| currency | BRL ou USD opcional; filtra moeda do pagamento. |
| page | Inteiro a partir de zero; padrão 0. |
| size | De 1 a 100; padrão 20. |

Exemplo: `/api/v1/settlements?from=2026-01-01T00:00:00Z&to=2026-07-01T00:00:00Z&currency=USD&page=0&size=20`. O intervalo usa settledAt, não vencimento, criação do título ou data da simulação. Limites com offset são normalizados por Instant. A comparação também preserva limites ISO com nanossegundos contra os instantes armazenados em microssegundos: ambos os limites são elevados à próxima posição da grade de microssegundos apenas quando necessário.

O controller valida tipos e delega ao StatementService. StatementQuery valida o intervalo e a paginação; StatementRepository monta somente fragmentos SQL fixos e liga todos os valores por parâmetros. Ordenação sempre settledAt decrescente e UUID decrescente como desempate. O offset é calculado como long, evitando overflow de página × tamanho. Não há ordenação SQL fornecida pelo cliente.

## Resposta e totais

```json
{
  "items": [],
  "page": 10,
  "size": 20,
  "total": 3,
  "totalsByCurrency": [
    {"currency":"BRL","count":1,"paymentTotal":"92859.94"},
    {"currency":"USD","count":2,"paymentTotal":"34189.34"}
  ]
}
```

O exemplo mostra uma página além da última: a lista é vazia, mas a contagem e os totais continuam representando todo o filtro. Cada item usa SettlementResponse, incluindo cedente/nome histórico, recebível, instante, face/VP/deságio em BRL, pagamento e moeda e parâmetros de auditoria. Valores e totais são strings decimais; count/total são inteiros. O total monetário é somente por moeda: BRL e USD nunca são somados em um valor único. Moedas sem registros são omitidas; filtro vazio retorna total 0, items [] e totalsByCurrency [].

Página, contagem e agregações são três consultas com o mesmo predicado em uma transação própria read-only REPEATABLE READ. Uma inserção confirmada durante a resposta só aparece na consulta seguinte. Essa garantia é por resposta; navegar entre páginas não mantém um snapshot entre requisições. O teste pausa a leitura após a página, confirma uma inserção em outra conexão e verifica que contagem/totais continuam na visão original.

HTTP 200 inclui página vazia. Período ausente, invertido ou maior que 366 dias, UUID/moeda inválidos e paginação fora dos limites retornam 400 INVALID_INPUT com errors por campo e correlationId. Acesso anônimo retorna 401. O OpenAPI declara parâmetros, limites e schemas da resposta.

## Índices e massa reproduzível

V5 adiciona `(settled_at DESC,id DESC)`, `(assignor_id,settled_at DESC,id DESC)` e `(payment_currency,settled_at DESC,id DESC)`. Não altera privilégios ou o histórico existente.

No diretório backend, com Java 21 e Docker:

```sh
./mvnw -Dtest=StatementQueryPlanTest test
```

O teste aplica as migrations em PostgreSQL 17.11 descartável, executa duas vezes [seed-statements.sql](../scripts/benchmark/seed-statements.sql), confirma 10000 títulos/5000 liquidações e gera `backend/target/statement-query-plans.txt`. São 10 cedentes, 2500 pagamentos de cada moeda e cotações históricas dentro de 24 horas da operação. UUIDs e datas são fixos; o script usa INSERT com ON CONFLICT DO NOTHING e não apaga ou reescreve histórico.

A massa é sintética e destina-se a um banco descartável migrado. Seus snapshots reproduzem C1/C3; o carregamento SQL não executa o endpoint de confirmação nem cria resultados idempotentes, e não mede throughput financeiro. Para carregar manualmente em um banco de benchmark já migrado: `psql -X -v ON_ERROR_STOP=1 -f scripts/benchmark/seed-statements.sql`, com conexão configurada por PGHOST/PGDATABASE/PGUSER/PGPASSWORD. Não há carga automática no banco do Compose.

Os planos vêm das mesmas strings SQL/parametrização do repository, com login restrito, após VACUUM ANALYZE. Cobrem período completo, cedente, moeda e filtros combinados. Evidência registrada em [planos SQL](statement-query-plans.txt). Execução local de 29/10/2026: PostgreSQL 17.11 aarch64 em Docker, Java 21.0.8, cliente macOS arm64 com 8 processadores disponíveis. As consultas usam login statement_reader associado ao papel restrito, página 0/tamanho 20, dados/índices aquecidos após carga e VACUUM ANALYZE.

Execution Time SQL (ms, sem somar Planning Time), extraído do relatório desta execução:

| Filtro | Página | Contagem | Totais |
|---|---:|---:|---:|
| Período completo | 0.061 | 0.400 | 1.209 |
| Cedente | 0.070 | 0.074 | 0.326 |
| USD | 0.046 | 0.220 | 0.821 |
| Cedente + USD + 1 dia | 0.087 | 0.079 | 0.073 |

Tempos SQL isolados não são latência HTTP ou p95. Não foi configurado enable_seqscan para forçar índices. A massa distribui dados de forma uniforme, com cedentes associados a uma moeda; o plano pode mudar com outra distribuição, estatísticas, cache ou volume. Offset elevado percorre mais entradas; contagem e totais ainda leem todo o filtro. A aferição HTTP com 10 clientes/1000 requisições por rota permanece no bloco 8.
