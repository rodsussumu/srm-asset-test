# Colaboração com IA

## Planejamento

Instruções do autor: usar Java no backend, Angular no frontend, PostgreSQL, arquitetura MVC em camadas e pacotes criados junto das funcionalidades. Especificar antes do código e executar por etapas: documentação, estrutura inicial e blocos de funcionalidades, com branches e pull requests.

A IA auxilia na organização das seis capacidades OpenSpec, premissas financeiras, golden cases, critérios de segurança e plano de validação. As regras propostas precisam ser verificadas por testes e revisadas pelo autor; geração de arquivos não comprova corretude ou domínio na defesa.

## Decisões mantidas pelo autor

A stack, a arquitetura MVC, a sequência de trabalho e o fluxo de branches/PRs foram definidos pelo autor. A validação comercial das premissas, a revisão do código e a capacidade de modificá-lo na defesa continuam sob sua responsabilidade.

## Evidências a registrar

- Prompts estratégicos de implementação e sua finalidade, sem transcrever sessões inteiras.
- Ao menos um erro concreto da IA e o teste ou review que o detectou. Este item ainda está pendente; não há erro de implementação comprovado nesta etapa.
- Resultados reais de golden cases, concorrência, rollback, retry e verificações da interface.
- Decisões aceitas, corrigidas ou rejeitadas pelo autor ao longo das etapas.

Ainda não foram executados testes da aplicação, smoke ou aferição de desempenho neste repositório. A defesa ao vivo também permanece pendente.

## Estrutura inicial — 28/10/2026

O autor autorizou as tasks 1.4 e 1.5 na branch chore/initial-structure. O escopo permaneceu no bootstrap Spring Boot, Angular estrito, PostgreSQL de desenvolvimento e CI básica. Não foram criados controller/service/repository vazios nem implementadas regras de negócio.

As matrizes oficiais de Spring Boot, Angular e PostgreSQL foram consultadas para as versões fixadas. npm ci, Maven clean verify, ESLint/Prettier e build Angular passaram. A API empacotada iniciou em porta temporária e respondeu health UP. Não há testes de negócio: Maven informa No tests to run, e a CI não declara aferição financeira ou cobertura nesta etapa.

O sandbox bloqueou o bind do Tomcat e encerrou a primeira execução do build Angular; repetir fora do sandbox permitiu verificar os dois. Isso é uma restrição de execução, não um erro financeiro da IA, e não substitui o caso concreto de erro de implementação ainda pendente. A execução remota do workflow também permanece pendente.

## Bloco 2 e antecipação da CI — 28/10/2026

O autor autorizou o próximo bloco após integrar a estrutura inicial e pediu antecipar a parte de testes da pipeline. Na branch feat/receivable-pricing, o escopo passou a incluir 2.1–2.5 e o novo item 2.6 de CI/cobertura backend, preservando o painel e sua suíte para o bloco correspondente.

Foram implementados motor/strategies decimais, elegibilidade com Clock, migration de cedentes/recebíveis, cadastro/consulta MVC, DTOs estritos, OpenAPI e tratamento de erros com correlação. O plano financeiro usa potência inteira, divisão com 50 dígitos e half-even no VP BRL e no pagamento convertido; C1/C2/C3 são verificados diretamente por valores esperados, sem tolerância de centavos.

A validação limpa passou com 74 testes: 37 do motor, 9 de prazo e 28 de integração PostgreSQL real, sem falhas/erros/skips. O acesso ao Docker foi bloqueado no sandbox; a execução fora dele passou, sem substituir PostgreSQL por H2 ou ignorar testes. JaCoCo registrou 299/308 linhas (97,08%) e 71/86 branches (82,56%), aprovando os mínimos gerais e financeiros sem exclusões. Limites temporários de 100% passados por CLI reprovaram com Rule violated/exit 1; a repetição com os padrões passou sem alterações no fonte ou pom.

A CI publica Surefire/JaCoCo por 14 dias e executa clean verify. Ainda não há evidência de execução remota desta branch, teste de liquidação concorrente, smoke financeiro integrado ou desempenho. A versão otimista é testada com uma entidade antiga; duas intenções financeiras simultâneas virão no bloco 4. O erro real de implementação da IA exigido no relatório continua pendente; restrição de Docker não foi apresentada como erro financeiro inventado.

## Bloco 3 — 29/10/2026

O autor autorizou continuar após a PR #3; o merge foi confirmado no Git e o trabalho partiu da main atualizada, na branch feat/fx-rates-and-simulations. O escopo 3.1–3.4 incluiu V2/histórico cambial, seleção temporal de até 24 horas, mock HTTP, sincronização limitada e simulação indicativa sem gravação financeira. Liquidação e frontend funcional não foram antecipados.

O motor financeiro foi reutilizado pelo service de simulação, com um único instante para prazo e câmbio. O cliente HTTP limita resposta completa a 1 segundo, cancela a operação no timeout e faz no máximo duas tentativas com intervalo de 100 ms, somente para falhas transitórias. A sincronização chama a rede antes da transação; o banco usa ON CONFLICT DO NOTHING para preservar registros. O papel ADMIN já protege as mutações, com CSRF; a conta local ainda é única até o bloco 6.

A validação limpa, fora do sandbox para Docker/portas, passou com 126 testes, sem falhas/erros/skips. Foram 36 casos novos de integração cambial e 16 do cliente HTTP, além dos 74 anteriores. JaCoCo registrou 495/512 linhas (96,68%) e 114/132 branches (86,36%), preservando os critérios gerais e financeiros. Os testes HTTP demonstram falhas 5xx/4xx, payload inválido, conexão recusada e dois timeouts completos abaixo de 3 segundos, inclusive com corpo bloqueado. Isso não é teste de carga ou garantia de produção.

A imagem do mock foi construída e iniciada em projeto temporário, na porta 18093, sem iniciar PostgreSQL/API. A consulta real retornou rate 5.4321 como string, origem local-mock e vigência UTC. Os serviços/rede temporários foram removidos ao finalizar, preservando o ambiente habitual. O .env real não foi alterado.

OpenAPI e documentação foram atualizados para diferenciar 201 de criação e 200 de sincronização repetida, os limites do mock e os itens ainda não implementados. Não houve execução remota da CI desta branch, liquidação confirmada, smoke integral ou aferição p95. A revisão final encontrou um erro concreto na primeira versão das anotações OpenAPI: GET /api/v1/fx-rates/current declarava 503, mas omitia 200. O teste foi ampliado antes da correção e falhou com exit 1 e No value at JSON path para responses['200']. A resposta 200 foi então declarada explicitamente. A revisão do mesmo contrato também corrigiu os schemas de erro dos controllers para ProblemDetail/application/problem+json, evitando documentar erros como DTOs de sucesso; o teste verifica essa representação. A detecção veio da inspeção do contrato gerado e de uma asserção de regressão, sem alterar a regra financeira para fabricar um erro.

## Bloco 4 — 29/10/2026

O autor autorizou a próxima etapa após integrar a PR #4. O merge foi confirmado e a branch feat/transactional-settlements partiu da main atualizada. O escopo foi 4.1–4.7: snapshots/idempotência, credenciais distintas, confirmação ACID, concorrência otimista, rollback e leitura histórica. Não foi antecipado extrato ou frontend funcional.

A transação ficou em service separado com REQUIRES_NEW; a fachada recebe sucesso somente após o proxy efetivar o commit. @Version/DynamicUpdate e JDBC compartilham a transação. A resposta é serializada e persistida como TEXT para replay exato; reserva incompleta é rejeitada por trigger diferido. O lock_timeout é traduzido por SQLSTATE sem tentar recuperar uma transação já abortada. Runtime e Flyway têm conexões/logins distintos.

A validação final passou com 164 testes: 126 anteriores, 35 de liquidação e 3 de esquema/permissões. O login real srm_app confirmou operações e foi impedido de alterar/excluir histórico. Duas transações leram a versão zero sob barreira e produziram 201/409, uma liquidação e um incremento. Latches coordenaram chave repetida, espera de 3 segundos e retomada após rollback. Falhas entre gravações/no commit e perda de transporte após commit verificaram ausência de parcialidade e replay dos bytes/status/Location. Os controles ficaram apenas no harness, sem sleeps arbitrários ou hooks de produção.

JaCoCo registrou 776/804 linhas (96,52%) e 187/218 branches (85,78%), aprovando todos os limites sem exclusões. O teste de contrato foi reforçado para exigir 200 da consulta e Problem Details no 409; ele detectou uma falha real da primeira atualização de anotações, que deixou o 409 sem schema de erro. A anotação foi corrigida e a suíte limpa completa passou. O diagrama também foi conferido contra as migrations: settlement_id é FK sem UNIQUE, então a cardinalidade foi corrigida em vez de adicionar uma constraint apenas para acomodar o desenho.

O setup preserva volumes e exige manter a credencial original do dono em MIGRATION_USER/MIGRATION_PASSWORD, criando outro login para a aplicação. O .env real e o Compose habitual não foram modificados. O bootstrap real foi executado nos PostgreSQL descartáveis dos testes; a configuração Compose foi validada sem afirmar um smoke integrado desta etapa. O processo ainda recebe a credencial de migração ao iniciar, limite explícito que recomenda job separado em produção. Não foram declarados CI remota desta branch, pagamento bancário, extrato ou métricas financeiras ainda inexistentes.
