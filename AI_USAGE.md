# Colaboração com IA

## Planejamento

Instruções do autor: usar Java no backend, Angular no frontend, PostgreSQL, arquitetura MVC em camadas e pacotes criados junto das funcionalidades. Especificar antes do código e executar por etapas: documentação, estrutura inicial e blocos de funcionalidades, com branches e pull requests.

A IA auxilia na organização das seis capacidades OpenSpec, premissas financeiras, golden cases, critérios de segurança e plano de validação. As regras propostas precisam ser verificadas por testes e revisadas pelo autor; geração de arquivos não comprova corretude ou domínio na defesa.

## Decisões mantidas pelo autor

A stack, a arquitetura MVC, a sequência de trabalho e o fluxo de branches/PRs foram definidos pelo autor. A validação comercial das premissas, a revisão do código e a capacidade de modificá-lo na defesa continuam sob sua responsabilidade.

## Evidências e estado atual

- Prompts estratégicos de implementação e sua finalidade, sem transcrever sessões inteiras.
- Erro concreto detectado no bloco 3: omissão do 200 no OpenAPI de FX; teste de contrato falhou antes da correção. [Extrato do log original](docs/evidence/openapi-regression.txt) e [teste preservado](backend/src/test/java/com/srm/credit/integration/FxIntegrationTest.java).
- Resultados reais de golden cases, concorrência, rollback, retry e verificações da interface.
- Decisões aceitas, corrigidas ou rejeitadas pelo autor ao longo das etapas.

Os relatos abaixo são históricos: pendências descritas em uma etapa se referem àquele momento. O estado consolidado inclui testes backend/Angular, smoke e aferição HTTP locais, além da CI remota da PR #10 aprovada com três artefatos publicados. Defesa ao vivo e aceite comercial permanecem sem evidência. [CI verificada](docs/ci.md).

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

## Bloco 5 — 29/10/2026

O autor autorizou o próximo bloco após a PR #5, cujo merge foi confirmado antes de criar feat/settlement-statements a partir da main. Foram implementados os itens 5.1–5.3: extrato SQL parametrizado, filtros/paginação, totais por moeda no mesmo snapshot, índices V5 e massa/planos reproduzíveis. O frontend permaneceu no esqueleto.

A implementação mantém controller → service → repository e reutiliza o mapeamento do snapshot. StatementQuery valida intervalo de até 366 dias e página de no máximo 100; offset é long. O repository só concatena fragmentos fixos e eleva limites temporais à grade de microssegundos para preservar >=from/<to. O service usa read-only REQUIRES_NEW/REPEATABLE READ; três consultas compartilham a mesma visão, sem prometer consistência entre páginas de requisições distintas.

A validação limpa passou com 190 testes, zero falhas/erros/skips: 164 anteriores, 25 de extrato e 1 de planos. JaCoCo registrou 842/870 linhas (96,78%) e 207/238 branches (86,97%), aprovando todos os limites sem exclusões. Testes verificam filtros, fronteiras, 45 empates, vazio, totais decimais além do limite individual e reprodução após nova cotação/nome. O cenário de inserção concorrente usa latches apenas no harness, confirma em outra conexão entre página e agregações e comprova o snapshot e as flags de isolamento/read-only.

O teste de planos carregou duas vezes o SQL sintético e preservou 10000 títulos/5000 liquidações, com 2500 pagamentos por moeda. A análise executou as strings SQL reais em PostgreSQL 17.11 aarch64, login restrito e dados aquecidos após VACUUM ANALYZE; cliente Java 21.0.8/macOS arm64 com 8 processadores disponíveis. O relatório foi copiado da execução atual para docs/statement-query-plans.txt e incluído nos artefatos de CI. Não foi reutilizado um resultado de outra execução como se fosse novo, forçado o planejador ou alegado ganho de p95 HTTP.

A massa SQL é fixture de consulta: não executa confirmação nem cria resultado idempotente. Não foi carregada no Compose habitual e o .env real não foi alterado. Não foi inventado um erro novo da IA nesta etapa; os erros reais de contrato detectados nos blocos anteriores continuam documentados. CI remota desta branch, frontend funcional, observabilidade completa e smoke/aferição dos blocos seguintes permanecem pendentes.

## Autenticação e painel — 29/10/2026

Após o autor solicitar o próximo bloco, o merge da PR #6 foi confirmado e a branch feat/operator-workspace partiu da main atualizada. O trabalho cobriu 6.1–6.5: sessão/CSRF/papéis e painel Angular de cadastro, simulação, confirmação e extrato. Foram preservados os contratos OpenAPI explícitos e os testes financeiros existentes; testes que usavam USER genérico passaram a declarar OPERATOR, sem flexibilizar a autorização de produção.

O estado foi separado dos templates, com cancelamento da requisição anterior antes do debounce da nova entrada. Dinheiro permaneceu string e o retry conserva a mesma chave/corpo em memória. PRICE_CHANGED exige novo cálculo e aceite explícito; recarga consulta dados persistidos sem reenviar intenções. A pipeline recebeu npm test.

A validação local resultou em 195 testes backend, 48 Angular, lint/build e gates JaCoCo aprovados (96,95% linhas e 85,71% branches). O esbuild abortou nas tentativas dentro do sandbox; o build autorizado fora dele concluiu, sem mudança de dependências ou remoção de verificações. Isso foi registrado como limitação observada do ambiente, sem fabricar um erro de regra financeira.

O navegador confirmou login de operador, cadastro por teclado, C1, liquidação BRL, extrato, recarga/consulta e logout em banco descartável separado. Evidências e identificadores estão em docs/quality.md. Não houve mudança no .env real nem nos volumes habituais. Execução remota do CI, smoke completo BRL/USD e aferição HTTP permanecem pendentes nos blocos próprios.

## Operação, review e evidência consolidada — 29/10/2026

O autor autorizou o próximo bloco após integrar a PR #7. O merge foi confirmado e feat/operational-readiness partiu da main. O escopo 7.1–7.6 reuniu logs JSON UTC, contador pós-commit, tempo do motor, Compose completo/healthchecks, relatórios Angular na CI, REVIEW do Anexo A e ER/C4. O prompt estratégico foi manter as regras financeiras/contratos existentes e verificar falhas reais das dependências em um projeto descartável, com dados criados pela API.

BusinessMetrics foi aplicado nos services para manter o model puro. A fachada só registra confirmação depois do retorno do proxy transacional; replay não adiciona confirmação ou cálculo. Testes verificaram contador antes do commit, após sucesso, em três replays e após rollback. O script operacional confirmou cookie, usuário SQL restrito, provedor fora com C1/C3, diagnóstico sem banco e logs reais sem canários. Foram 197 testes backend, 48 Angular e builds/lint aprovados; não se inferiu desempenho HTTP das métricas do motor. Resultados e comandos estão em docs/quality.md e docs/operations.md.

O caso concreto de erro da IA permanece o observado no bloco 3: a primeira anotação de GET /api/v1/fx-rates/current declarava 503 e omitia 200. Inspeção do OpenAPI gerado motivou uma asserção de regressão que falhou com No value at JSON path e BUILD FAILURE. A correção declarou 200 explicitamente, preservou ProblemDetail nos erros e passou na suíte completa. O log temporário original foi conferido e seu extrato sem dados de ambiente foi versionado em docs/evidence/openapi-regression.txt, identificado como execução anterior. O teste continua no código. Não foi fabricado defeito financeiro nem um novo erro nesta etapa para cumprir o relatório.

Decisões mantidas pelo autor: Java/Angular/PostgreSQL, MVC com camadas criadas junto das features, sequência specs → estrutura → blocos e integração por branches/PRs. Não se delegam à IA a aprovação comercial das premissas, autorização para publicação/convites, aceite final ou a capacidade de explicar/modificar o próprio código na defesa. A revisão humana final continua sob responsabilidade do autor; este relatório não afirma que ela já ocorreu.

A IA auxiliou implementação, análise do Anexo A e verificação; a geração dos documentos não substitui avaliação humana. CI remota desta branch, smoke final, aferição HTTP e walkthrough permanecem explicitamente pendentes. O ambiente habitual foi preservado.

## Validação final — 29/10/2026

Após o autor informar o merge da PR #8 e autorizar a próxima etapa, a branch test/final-validation partiu da main dc6c562. O escopo foi 8.1–8.3: smoke limpo BRL/USD, aferição HTTP reproduzível e rastreabilidade/roteiro de defesa. Nenhum resultado anterior foi apresentado como nova execução.

O verificador HTTP criou registros por API em banco limpo; o navegador confirmou mais uma operação USD e conferiu extrato, recarga/filtro/logout. A aferição usou outro volume, fixture determinístico 10000/5000 e cotação atual sincronizada pela API. O driver usa 10 sessões/CSRF e conexões independentes, 200 requisições de aquecimento e 1000 medidas por rota, sem retry. CSV, metadados e percentis recalculados registram erro zero, p95 16,628/17,311 ms; isso é recorte local curto, não promessa de produção.

A suíte final passou com 198 testes backend, 48 Angular e os gates existentes. A fixture de strategy usa spread alternativo somente no teste e não introduz um novo tipo em produção. A matriz cobre 32 requisitos/59 cenários e o roteiro prepara 10 minutos com demonstração de concorrência e extensão. Os métodos e links citados foram conferidos. O .gitignore foi complementado para caches Python; resultados selecionados em docs/evidence são evidências intencionais.

O erro real de OpenAPI detectado no bloco 3 continua documentado com o log original e a asserção de regressão, sem fabricar outro defeito. Stack/MVC, sequência e autorização de publicação permaneceram decisões do autor. A aprovação comercial, revisão humana final e defesa continuam responsabilidade humana. Os 39 itens locais concluídos não atestam CI remota, convites, aceite do negócio ou defesa realizada.

## Ampliação do ensaio de desempenho — 29/10/2026

O autor pediu cenário menor e carga maior e confirmou duas escolhas: 2 CPUs/4 GiB para todo o conjunto e 100 mil medidas totais (50 mil por rota). A IA preparou o override de Compose e o observador, sem trocar a referência nem alterar as regras financeiras. A primeira tentativa foi preservada: 50 mil simulações sem erro, 33335 falhas no extrato e quase 12 horas de execução com gaps de coleta. Logs de energia do host confirmaram Sleep/DarkWake; isso compromete a medição, mas não prova causa única de todas as falhas.

A repetição usou banco limpo, mesmas cotas/fixture, prevenção temporária de idle sleep e timeout global de 900 s. Resultou em 100 mil HTTP 200 válidos, p95 60,923/94,195 ms, sem OOM ou reinício. CPU/memória e percentis foram auditados. Ambas as tentativas e hashes dos dados comprimidos foram mantidos.

Houve outro erro real da IA no observador: json.loads aplicado diretamente ao stream descartava a primeira linha com controles ANSI, omitindo backend. A inspeção da cobertura de nomes nas amostras detectou a lacuna. O parser agora remove esses controles, registra erros e verifica quatro serviços; um replay offline de payloads reais conferiu a correção. O relatório não inventa amostras contínuas de backend: usa os contadores cgroup de todos os serviços e identifica a limitação. Não houve terceira carga para apagar a evidência.

Spec central, cenário operacional, matriz (agora 32 requisitos/60 cenários) e roteiro foram alinhados. Arquivos brutos grandes permanecem ignorados; evidências selecionadas são gzip verificáveis. CI remota e defesa humana continuam sem evidência nesta etapa.

## Conferência da CI e manutenção — 29/10/2026

A execução remota da PR #10 foi inspecionada no GitHub: backend e frontend aprovados, com backend-test-results, backend-coverage e frontend-test-results publicados. O download dos arquivos não foi verificado. Os avisos de runtime Node 20 e setup-java v4 motivaram a atualização das Actions; o aviso de migração de ubuntu-latest motivou fixar Ubuntu 24.04. Os testes Angular/Vitest já rodavam na CI; a etapa recebeu nome explícito. A execução anterior comprova o workflow anterior, não a versão modificada localmente. [Evidência e fontes](docs/ci.md).
