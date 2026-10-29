# Testes, cobertura e CI

## Validação do bloco 2 — 28/10/2026

Validação local em 28/10/2026, com Java 21 e Docker. `./mvnw -B -ntp clean verify` passou com **74 testes**, zero falhas, erros ou skips:

| Suíte | Casos | Verifica |
|---|---:|---|
| PricingEngineTest | 37 | C1/C2/C3, half-even, conversão do VP BRL arredondado, limites, base configurável, moeda/cotação obrigatórias e strategies sem fallback |
| MonthlyTermPolicyTest | 9 | Dias 0/1/30/31/1080/1081, passado, ausência de vencimento e virada de data em São Paulo |
| ReceivablesIntegrationTest | 28 | PostgreSQL real, migration limpa, NUMERIC/FK/CHECK, versão antiga, cadastro/consulta/paginação, validação, CSRF/401, erros seguros e OpenAPI |

A suíte de integração usa PostgreSQL 17.11-alpine descartável via Testcontainers; não usa H2, não ignora falha de Docker e não modifica o volume Compose. A primeira execução no sandbox não conseguiu acessar o Docker e falhou; fora do sandbox, a suíte completa passou. Não foi alterado teste para contornar essa restrição.

## Cobertura

JaCoCo 0.8.16 emite HTML/XML em target/site/jacoco e bloqueia o verify. Não há exclusão de código de produção.

| Escopo | Linhas mínimas | Branches mínimos |
|---|---:|---:|
| Backend completo | 80% | 70% |
| Cada classe PricingEngine, FinancialAmounts, MonthlyTermPolicy e cada strategy | 95% | 90% |

Resultado geral extraído do XML: **299/308 linhas (97,08%)** e **71/86 branches (82,56%)**. Cada regra financeira foi aprovada:

| Classe | Linhas | Branches |
|---|---|---|
| ChequeStrategy | 3/3 (100.00%) | sem branches |
| DuplicataStrategy | 3/3 (100.00%) | sem branches |
| FinancialAmounts | 18/18 (100.00%) | 20/20 (100.00%) |
| MonthlyTermPolicy | 11/11 (100.00%) | 6/6 (100.00%) |
| PricingEngine | 32/32 (100.00%) | 24/24 (100.00%) |

Classes sem branches não precisam de testes artificiais para criar desvios. Os testes adicionais do motor verificam configurações inválidas, ausência de moeda/cotação e preservação do spread com base zero; não alteram a fórmula.

## Comprovação do bloqueio

Executado sobre a mesma medição, sem modificar pom.xml ou código:

```sh
./mvnw -B -ntp -Dcoverage.lines.minimum=1.0 -Dcoverage.branches.minimum=1.0 jacoco:check@coverage-check
```

O comando retornou exit 1 com Rule violated, como esperado. Em seguida, `./mvnw -B -ntp jacoco:check@coverage-check` passou com as regras persistidas. O teste negativo não conta como falha não resolvida da suíte; comprova que o build reprova cobertura insuficiente.

## Pipeline

O workflow executa clean verify no backend e npm ci/lint/build no frontend, em PRs e pushes na main, com timeout de 15 minutos por job. O runner Ubuntu fornece Docker para o Testcontainers. Surefire e JaCoCo são publicados por 14 dias, inclusive após falhas quando os arquivos existem; uma etapa posterior de publicação não remove a falha obrigatória.

A CI do frontend ainda não tem testes funcionais porque o painel permanece no esqueleto; eles acompanharão o bloco 6. Concorrência de liquidação, rollback e idempotência entram no mesmo verify quando implementados no bloco 4. Esta evidência é local; a execução remota desta branch ainda precisa ocorrer na PR.

## Reproduzir

Com Java 21 e Docker ativos, no backend:

```sh
./mvnw -B -ntp clean verify
./mvnw -Dtest=PricingEngineTest,MonthlyTermPolicyTest test
./mvnw -Dtest=ReceivablesIntegrationTest test
```

Relatórios gerados ficam ignorados no Git. Evite interpretar o teste isolado de entidade com versão antiga como prova da disputa entre duas liquidações: esse cenário pertence ao bloco 4. Não houve aferição HTTP, smoke completo ou testes do painel nesta etapa.

## Validação atual — bloco 3, 29/10/2026

`./mvnw -B -ntp clean verify` passou com **126 testes**, zero falhas/erros/skips, Spotless e todos os critérios JaCoCo aprovados. São 74 casos anteriores, 36 de FxIntegrationTest e 16 de FxProviderClientTest. Cobertura atual: **495/512 linhas (96,68%)** e **114/132 branches (86,36%)**. Os mínimos gerais 80%/70% e financeiros 95%/90% não foram alterados e nenhuma exclusão foi adicionada.

As integrações PostgreSQL verificam V1/V2 em banco limpo, precisão/constraints do câmbio, duplicidade, histórico e consulta por UUID, escolha temporal com Clock fixo, cotação ausente/futura, exatamente 24 horas e expiração um microssegundo depois. A simulação HTTP reproduz C1/C2/C3 com strings e preserva as contagens do cadastro/histórico; sem cotação, BRL continua e USD retorna 503. ADMIN/CSRF são exigidos nas mutações cambiais; OPERATOR não consegue gravá-las. A sincronização repetida recupera o mesmo UUID sem reescrever o original; mesma vigência com outro valor conflita.

O servidor HTTP de teste verifica recuperação 5xx, intervalo de 100 ms, duas tentativas totais, conexão recusada, ausência de retry em 4xx e payload inválido. Dois timeouts, inclusive com headers recebidos e corpo bloqueado, terminam entre 2 e 3 segundos no ambiente local. Esses limites são evidência do cenário testado, não garantia de latência em produção. A chamada externa precede a transação de persistência; a simulação consulta somente o banco.

O Compose foi validado com .env.example. A imagem fx-mock foi construída e iniciada isoladamente no projeto srm-new-task3, porta 18093. GET /rate retornou rate string 5.4321, origin local-mock e effectiveAt UTC com microssegundos. O teste não iniciou banco/API nem alterou o Compose habitual. O mock e a rede temporários foram encerrados após a consulta; não houve remoção de volume de dados existente.

A CI já executa as novas suítes por clean verify e publica os mesmos relatórios Surefire/JaCoCo por 14 dias. Não há evidência de execução remota desta branch nesta etapa. Liquidação, rollback financeiro, concorrência de confirmações, testes funcionais Angular, smoke integral e aferição HTTP permanecem nos blocos seguintes.

### Regressão real do contrato OpenAPI

A revisão detectou ausência de 200 na documentação gerada de GET /api/v1/fx-rates/current. Foi acrescentada uma asserção ao teste publishesSimulationAndCurrencyContractsInOpenApi antes de corrigir o controller; a execução isolada retornou exit 1 com No value at JSON path para responses['200']. A correção declarou 200 explicitamente. Os erros também passaram a declarar application/problem+json/ProblemDetail, com asserção no 503 de simulação, em vez de herdar o schema de sucesso. O número de casos permanece 126: foram fortalecidas as asserções de um teste existente.

Após as correções do contrato, spotless:apply clean verify foi repetido e passou com os mesmos 126 testes, zero falhas/erros/skips e todos os limites JaCoCo.

## Validação atual — bloco 4, 29/10/2026

`./mvnw -B -ntp spotless:apply clean verify` passou com **164 testes**, zero falhas/erros/skips. Permanecem os 126 casos anteriores, com mais 35 em SettlementIntegrationTest e 3 em SettlementSchemaTest. JaCoCo: **776/804 linhas (96,52%)** e **187/218 branches (85,78%)**; mínimos gerais e financeiros aprovados, sem exclusões ou redução dos limites.

### Evidências de integridade

- Duas transações reais leem version=0 sob CyclicBarrier e confirmam com chaves distintas. O teste exige 201/409, uma liquidação, um resultado idempotente e version=1. O controle da leitura fica em BeanPostProcessor apenas do contexto de teste.
- Mesma chave concorrente espera e recupera os mesmos bytes/status/Location; espera acima de 3 segundos gera REQUEST_IN_PROGRESS/Retry-After. Se a primeira operação desfaz, o retry pode assumir a reserva.
- Falhas antes/depois do INSERT e em constraint trigger diferido no commit desfazem estado, versão, auditoria e reserva. Uma falha de transporte após commit é recuperada por replay sem recálculo.
- A aplicação de integração conecta com login srm_app, distinto do dono das migrations. O bootstrap SQL real foi executado nos bancos descartáveis. O runtime consegue confirmar/completar a reserva e não consegue UPDATE/DELETE/TRUNCATE de liquidações/cotações, apagar/regravar idempotência confirmada ou alterar a constraint de unicidade.
- O limite NUMERIC(30,2), overflow, balanço BRL e unicidade por título são testados no PostgreSQL. Reserva incompleta não pode ser confirmada isoladamente.
- C1/C2/C3 são confirmados por HTTP com snapshots completos. PRICE_CHANGED, moeda inválida, estado/prazo inelegível e cotação ausente falham sem registros parciais. Nova cotação/nome de cedente não altera a leitura histórica ou o replay.

### Contrato e configuração

O teste OpenAPI verifica Idempotency-Key obrigatório, snapshot 201, consulta individual 200 e schema ProblemDetail/application/problem+json no 409. A primeira atualização deixou a anotação 409 sem esse schema; o teste reforçado falhou e a anotação foi corrigida. A repetição limpa completa passou com 164 testes. Não foi alterada a regra financeira para fabricar uma falha.

Compose foi validado com .env.example e inclui PostgreSQL, db-bootstrap e mock; a API continua no host. O bootstrap no Compose deve ser executado antes da API, sem apagar volumes existentes. A implementação usa credenciais distintas no atendimento e nas migrations, mas o processo backend ainda recebe ambas ao iniciar. A segregação não é isolamento contra comprometimento completo do processo.

Não houve alteração do .env real, criação/reinício do Compose habitual ou remoção de volume de dados. Os PostgreSQL de teste são descartáveis e encerrados pelo Testcontainers. CI já executa as novas suítes pelo mesmo clean verify; execução remota desta branch, extrato, painel, métricas de confirmações e smoke integrado permanecem nos blocos correspondentes.

## Validação atual — bloco 5, 29/10/2026

`./mvnw -B -ntp spotless:apply clean verify` passou com **190 testes**, zero falhas/erros/skips, Spotless e todos os checks JaCoCo. São 164 casos anteriores, 25 de StatementIntegrationTest e 1 de StatementQueryPlanTest. Cobertura atual: **842/870 linhas (96,78%)** e **207/238 branches (86,97%)**, sem alterar os mínimos gerais/financeiros ou excluir produção.

O extrato é exercitado com login real srm_app: período semiaberto, filtros combinados, 366 dias, paginação/limites, 45 instantes empatados, página vazia, ordem UTC/UUID e limites submicrossegundo. Os testes preservam snapshots após mudança de nome/cotação e somam decimais exatos, inclusive totais maiores que um pagamento individual. BRL e USD nunca são somados em uma moeda artificial.

Um teste com latches pausa a leitura após a página, confirma outra inserção por conexão independente e exige a contagem/totais da visão original. Também verifica transaction_isolation=repeatable read e transaction_read_only=on. A requisição seguinte vê a nova liquidação. Não há sleeps ou hooks no código de produção para coordenar esse cenário.

O teste de planos aplica V1–V5 em PostgreSQL 17.11 descartável, executa duas vezes o seed sem duplicar e verifica 10000 recebíveis, 5000 liquidações e 2500 pagamentos USD. O fixture tem 10 cedentes, datas/UUIDs fixos e cotações históricas válidas na operação. Executa as consultas reais do repository com statement_reader/srm_runtime, após VACUUM ANALYZE, e gera EXPLAIN (ANALYZE, BUFFERS) para página/contagem/totais em quatro combinações de filtros.

O [relatório desta execução](statement-query-plans.txt) foi copiado de target e os tempos extraídos estão em [extrato](statements.md). A CI publica esse relatório junto de Surefire e mantém JaCoCo, por 14 dias. Não foi forçado o uso de índices; o plano depende dos dados e pode escolher varredura sequencial em agregações amplas. Tempo SQL isolado com dados aquecidos não comprova p95 HTTP ou throughput de confirmação.

O seed foi executado somente nos bancos descartáveis dos testes, não no Compose habitual. Configuração YAML/Compose, OpenSpec e links foram conferidos no fechamento da etapa. Não houve teste funcional Angular, aferição HTTP ou execução remota desta branch.

## Validação atual — bloco 6, 29/10/2026

Backend: `./mvnw -B -ntp spotless:apply clean verify` aprovado com **195 testes**, zero falhas/erros/skips (190 existentes e 5 de AuthenticationTest). JaCoCo: **921/950 linhas (96,95%)** e **216/252 branches (85,71%)**. Todos os gates gerais e por classe financeira continuam aprovados, sem exclusões ou diminuição de limites.

AuthenticationTest percorre os filtros reais: login com senhas de teste, mudança do identificador da sessão, CSRF anterior rejeitado após login/logout, identidade e papéis, credenciais inválidas e ausência de redirects. Verifica 401 anônimo/Basic e 403 para papel desconhecido, operador em câmbio e mutação sem token válido. As integrações financeiras continuam com PostgreSQL real e OPERATOR/ADMIN explícitos, preservando os testes de schema, idempotência, rollback, concorrência e contratos OpenAPI dos blocos anteriores.

Frontend: Node 22.16.0, `npm run lint` e **48 testes em 6 arquivos** aprovados. `npm run build` aprovado (305,17 kB inicial, estimativa de transferência 80,16 kB). O processo esbuild abortou nas duas primeiras execuções dentro do sandbox; a execução com permissão fora do sandbox concluiu em 1,709 s, sem alterar dependências, regras de build ou budgets. Isso é uma restrição observada do ambiente local, sem atribuir causa ao código nem declarar sucesso nessas tentativas.

Os testes Angular incluem sessão/CSRF sem storage, strings monetárias no limite de face e totais maiores, campos acessíveis, debounce de 300 ms, cancelamento imediato de resposta antiga, entrada inválida, câmbio ausente, cadastro separado, duplo clique, timeout de 15 s, retry com chave/corpo originais, REQUEST_IN_PROGRESS, PRICE_CHANGED exigindo novo aceite, recuperação por consulta e filtros/paginação/totais. Datas são verificadas em São Paulo, inclusive transições históricas de horário de verão.

Conferência pontual no Chrome: Angular 4206 → API 18086, PostgreSQL 17.11 descartável em 55446 no projeto Compose srm-new-task6. Login OPERATOR, abertura do cadastro por teclado, cedente cadastrado com Enter, face 100.000,00/vencimento 27/01/2027 (90 dias desde 29/10), simulação C1, cadastro, confirmação explícita, estado Liquidado e extrato BRL 92.859,94 foram verificados. A liquidação criada foi 8ebddd46-188a-4c21-9277-a10c3f7f2a05 e o título 6dafe444-12ff-4c39-a258-6f56058950b1. Recarga preservou a sessão; consulta por UUID recuperou o estado e logout voltou ao acesso. Dados foram criados pela interface, sem edição manual do banco nem mudança do .env habitual.

A CI agora executa npm test antes do build Angular e mantém clean verify/artefatos backend. Execução remota desta branch não foi verificada. Esta conferência BRL não encerra o smoke completo BRL/USD nem a aferição HTTP do bloco 8. Compose completo/observabilidade ficam no bloco 7.
