# Confirmação, idempotência e auditoria

`POST /api/v1/settlements` confirma a aquisição integral de um recebível persistido. Requer autenticação, CSRF e `Idempotency-Key` UUID. O operador vem da identidade autenticada, nunca do corpo. A confirmação é um registro interno: não envia dinheiro a um banco.

```json
{
  "receivableId": "c617e0a9-5906-4dbc-bff9-fab5f0366307",
  "currency": "USD",
  "expectedPaymentAmount": "17094.67",
  "expectedFxRateId": "e780a7b8-660b-46cc-bf6d-bd8aac154ac7"
}
```

Use os identificadores reais do cadastro e da simulação. Em BRL, omita expectedFxRateId; em USD, ele é obrigatório. expectedPaymentAmount é string decimal não negativa, com até duas casas e 28 dígitos inteiros. Zero é aceito porque um VP elegível muito pequeno pode arredondar para zero; o servidor ainda exige igualdade com o recálculo. Números JSON, expoentes e campos extras são rejeitados.

O sucesso retorna 201, Location `/api/v1/settlements/{id}` e o snapshot. Ele inclui cedente/nome, recebível, operador, face e moeda BRL, vencimento/tipo/prazo, data/instante da operação, base/spread, VP/deságio em BRL, pagamento/moeda, cotação completa quando USD, política, correlação original, instante e versão confirmada do recebível. Decimais são strings; dinheiro tem duas casas, taxas/câmbio têm oito. `GET /api/v1/settlements/{id}` lê os valores originais, sem usar taxa atual nem recalcular. Não há PUT/PATCH/DELETE do histórico.

## Protocolo

1. Validar a chave e normalizar recebível, moeda, valor com duas casas e cotação explícita ou nula. Calcular SHA-256 desse conteúdo. A chave é única por operador e rota, independente da sessão.
2. Abrir transação READ COMMITTED própria. Reservar a chave com INSERT ON CONFLICT DO NOTHING. Uma reserva concorrente pode bloquear por até 3 segundos, controlados por lock_timeout local.
3. Se já confirmada: hash igual devolve o status 201, corpo JSON e Location armazenados, byte a byte, antes de ler recebível ou câmbio. Hash diferente retorna 409 IDEMPOTENCY_KEY_REUSED.
4. Para chave nova, capturar instante UTC, carregar recebível/versão e validar AVAILABLE, moeda e vencimento. Calcular prazo em America/Sao_Paulo e selecionar cotação persistida para USD. Não chamar o provedor HTTP.
5. Comparar pagamento e UUID da cotação com a confirmação. Divergência retorna PRICE_CHANGED, sem reserva ou gravação confirmada. O cliente precisa simular e confirmar novamente.
6. Alterar o estado, forçar flush com @Version e inserir auditoria. Hibernate DynamicUpdate atualiza somente status/version, compatível com a credencial restrita. Unicidade por recebível protege o histórico independentemente da versão.
7. Completar o resultado idempotente e efetuar commit. Só depois o service externo à transação registra o evento de confirmação e o controller devolve sucesso. Falha inclusive no commit impede o corpo de sucesso.

Liquidação, estado/versão e resultado idempotente pertencem à mesma transação. Não há reserva pendente confirmada: um constraint trigger diferido rejeita commit incompleto. Uma falha desfaz tudo e libera a chave para retry. A aplicação nunca consulta novamente uma transação PostgreSQL já abortada para tentar recuperar resultado.

## Repetição e concorrência

Mantenha a mesma chave e o mesmo corpo ao repetir após timeout ou perda de resposta. Formatos monetários equivalentes, como 100, 100.0 e 100.00, têm o mesmo hash. Retenção de sucessos é sem expiração nesta entrega. Alterações posteriores de câmbio, nome do cedente ou parâmetros para novas operações não reescrevem o resultado original.

Na mesma chave, a segunda operação aguarda a primeira: se ela confirma, recupera o resultado; se desfaz, a segunda pode assumir a reserva. Ao ultrapassar 3 segundos, retorna 409 REQUEST_IN_PROGRESS com Retry-After: 1 e pode ser repetida. Com chaves diferentes sobre o mesmo título, ambas podem ler a mesma versão; somente uma atualiza/efetiva o commit. A perdedora recebe 409 RECEIVABLE_ALREADY_SETTLED e sua reserva é desfeita.

O corpo do replay mantém o correlationId da confirmação original para preservar a resposta exata. O cabeçalho X-Correlation-ID identifica a requisição atual. Erros usam a correlação atual. Não confunda perda da resposta com rollback: após commit, o retry é a forma de recuperar a confirmação.

## Respostas e limites

| Código | Situação |
|---|---|
| 400 INVALID_INPUT | Dados/chave inválidos, moeda divergente ou vencimento inelegível. |
| 401/403 | Autenticação ausente ou CSRF inválido. |
| 404 RESOURCE_NOT_FOUND | Recebível ou liquidação não encontrados. |
| 409 PRICE_CHANGED | Valor ou cotação divergem da confirmação. |
| 409 IDEMPOTENCY_KEY_REUSED | Chave confirmada associada a outro conteúdo. |
| 409 RECEIVABLE_ALREADY_SETTLED | Título liquidado ou disputa otimista perdida. |
| 409 REQUEST_IN_PROGRESS | Espera pela mesma chave excedeu 3 segundos. |
| 503 FX_RATE_UNAVAILABLE | USD sem cotação local vigente com até 24 horas. |
| 500 INTERNAL_ERROR | Falha inesperada de persistência/commit; detalhe seguro e rollback. |

Falha externa não impede BRL ou USD com cotação local válida. A proteção pelo usuário real do banco está em [credenciais e histórico](database.md). O extrato filtrado/paginado está implementado em GET /api/v1/settlements; a consulta individual permanece para leitura do snapshot por UUID. Veja [extrato](statements.md). Não há garantia de execução única fora deste banco ou de transferência bancária.

## Verificação

`./mvnw -Dtest=SettlementSchemaTest,SettlementIntegrationTest test` usa PostgreSQL real e login restrito. O harness de teste coordena duas leituras da versão zero com CyclicBarrier, sem sleeps nem hooks/endpoints de teste no código da aplicação. Spies e latches ficam exclusivamente nos testes. Também são injetadas falhas entre gravações, no commit diferido e na camada de transporte após commit, verificando estado, versão, auditoria, reserva e replay. `./mvnw clean verify` acrescenta as demais suítes, lint/build e limites de cobertura.
