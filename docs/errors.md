# Erros REST e correlação

Os erros usam `application/problem+json`, com campos padrão Problem Details e extensões `code`, `correlationId` e, para validação, `errors` por campo.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Verifique os campos informados.",
  "instance": "/api/v1/receivables",
  "code": "INVALID_INPUT",
  "correlationId": "82fcb17a-cb03-4d55-8ad5-4239b4c30f2d",
  "errors": {"faceValue": "Valor de face fora dos limites permitidos."}
}
```

| Status | Código | Situação |
|---|---|---|
| 400 | INVALID_INPUT | JSON, tipos, campos ou parâmetros inválidos; regra de elegibilidade. |
| 401 | AUTHENTICATION_REQUIRED | Ausência de autenticação válida. |
| 403 | ACCESS_DENIED | Acesso negado ou CSRF inválido em sessão autenticada. |
| 404 | RESOURCE_NOT_FOUND | Recurso inexistente. |
| 405 | METHOD_NOT_ALLOWED | Verbo não suportado; resposta informa Allow. |
| 406 | NOT_ACCEPTABLE | Representação solicitada não disponível. |
| 409 | DATA_CONFLICT | Conflito de integridade ou versão de dados. |
| 409 | FX_RATE_EXISTS | Vigência já registrada; na sincronização, valor diferente do original. |
| 409 | PRICE_CHANGED | Pagamento ou cotação divergem da confirmação; simular/confirmar novamente. |
| 409 | IDEMPOTENCY_KEY_REUSED | Mesma chave por operador/rota já associada a outro conteúdo. |
| 409 | RECEIVABLE_ALREADY_SETTLED | Título liquidado ou disputa otimista perdida. |
| 409 | REQUEST_IN_PROGRESS | Espera pela chave excedeu 3 segundos; Retry-After: 1. |
| 415 | UNSUPPORTED_MEDIA_TYPE | Corpo em formato não suportado. |
| 500 | INTERNAL_ERROR | Falha inesperada, sem expor a exceção interna. |
| 503 | FX_RATE_UNAVAILABLE | Não há cotação local vigente com até 24 horas para USD. |
| 503 | FX_PROVIDER_UNAVAILABLE | Sincronização falhou após política limitada; histórico preservado. |

O filtro aceita X-Correlation-ID somente no formato UUID canônico; valor inválido é substituído por UUID gerado. Toda resposta recebe o cabeçalho, e erros devolvem o mesmo identificador no corpo. Ele também é colocado no MDC durante a requisição e retirado ao final, permitindo correlação nos logs JSON.

O handler não retorna SQL, stack trace, credenciais, valores rejeitados ou mensagem de exceção inesperada. A falha inesperada é registrada com o tipo de exceção e correlação, sem copiar o payload. Os testes injetam uma falha somente no contexto de teste para comprovar que detalhes internos não aparecem na resposta; não há rota de falha na aplicação entregue.

Os códigos de câmbio e liquidação são exercitados pelos testes HTTP com PostgreSQL real. No fluxo financeiro, falhas inesperadas de integridade ou commit são 500; conflitos de unicidade por recebível e versão são mapeados especificamente para RECEIVABLE_ALREADY_SETTLED, fora da transação abortada.
