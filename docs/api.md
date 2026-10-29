# API implementada no bloco 2

Base: `/api/v1`. As rotas abaixo exigem autenticação. O frontend ainda é o esqueleto; o painel de operação será implementado no bloco 6.

| Método e rota | Resultado |
|---|---|
| POST `/assignors` | Cria cedente; retorna 201 e Location. |
| GET `/assignors/{id}` | Consulta cedente; retorna 200 ou 404. |
| GET `/assignors?page=0&size=20` | Lista cedentes em página numerada a partir de zero. |
| POST `/receivables` | Cria título AVAILABLE, versão zero; retorna 201 e Location. |
| GET `/receivables/{id}` | Consulta título; retorna 200 ou 404. |
| GET `/receivables?page=0&size=20&status=AVAILABLE` | Lista títulos; estado é filtro opcional. |

Listagens retornam `{items, page, size, total}`. Tamanho entre 1 e 100, padrão 20. A ordem é createdAt decrescente e id decrescente como desempate. Página sem registros retorna 200 e lista vazia.

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

O fluxo de autenticação próprio do produto, o endpoint dedicado de CSRF e os papéis OPERATOR/ADMIN pertencem ao bloco 6. O health é público; dados e documentação exigem autenticação. Esta etapa apenas padroniza erros 401/403, sem implementar gestão de usuários ou permissões por papel.

## OpenAPI e verificação

Com o backend iniciado: `/swagger-ui/index.html` apresenta o contrato e `/v3/api-docs` retorna o OpenAPI gerado dos controllers/DTOs. O contrato declara strings monetárias, enums, parâmetros, respostas e autenticação Basic.

No diretório backend, com Java 21 e Docker ativos:

```sh
./mvnw verify
```

`ReceivablesIntegrationTest` exercita HTTP via MockMvc com PostgreSQL descartável real: cadastro, consulta, paginação, precisão, validação de entrada, autenticação, CSRF, erros e OpenAPI. Não usa H2 e não altera o banco do Compose.

Não há endpoints de simulação, câmbio, liquidação ou extrato de liquidações nesta etapa.
