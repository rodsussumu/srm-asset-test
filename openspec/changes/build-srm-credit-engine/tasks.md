# Tasks

Executar por etapas, com uma branch por funcionalidade e pull request para main. Por orientação do usuário, o bloco 2 inclui a antecipação dos testes/cobertura do backend na CI (2.6); a suíte do painel será incorporada quando houver funcionalidades Angular. Marcar cada item somente após sua implementação e validação neste repositório. Seguir MVC com controller → service → repository, model, dto, config e exception, conforme `docs/architecture.md`. Criar os pacotes somente quando houver implementação correspondente.

## 1. Premissas, documentação inicial e base do projeto

- [x] 1.1 Criar `SPEC.md` na raiz antes de código, com 1–2 páginas de premissas, perguntas ao negócio, precisão e critérios de aceite; verificar alinhamento com as seis specs e registrar a convenção teto(dias/30), validade cambial de 24 horas e atomicidade por recebível.
- [x] 1.2 Criar `DECISIONS.md` e iniciar `AI_USAGE.md` com decisões reais, prompts estratégicos e lacunas explícitas; verificar que nenhum teste, erro da IA ou resultado ainda inexistente é apresentado como concluído.
- [x] 1.3 Documentar GitHub Flow com branches por funcionalidade, conventional commits e PRs no README/DECISIONS; verificar a branch atual e exigir problema, decisão e validação nas descrições de PR.
- [x] 1.4 Fixar versões compatíveis e suportadas de Java/Spring Boot, Angular/Node e PostgreSQL com referência à documentação oficial; criar backend Maven Wrapper e frontend TypeScript estrito e verificar builds iniciais reproduzíveis com lockfile.
- [x] 1.5 Configurar lint e CI básica desde o início; verificar execução dos comandos locais de build/lint nos dois projetos e documentá-los no README.

## 2. Motor financeiro e cadastro de recebíveis

- [x] 2.1 Implementar strategies DUPLICATA/CHEQUE em model.strategy e motor decimal no model com política versionada; verificar C1, C2 e C3 em teste automatizado com comparação ao centavo, incluindo deságio e moedas.
- [x] 2.2 Implementar prazo mensal, Clock injetável e elegibilidade; verificar dias 0, 1, 30, 31, 1080 e 1081, virada de data em São Paulo, prazo zero apenas no motor puro, limites de face e empates half-even.
- [x] 2.3 Criar migrations de cedentes/recebíveis, constraints e versão otimista; verificar aplicação em PostgreSQL limpo com teste de integração e inserir o ER inicial em `docs/`.
- [x] 2.4 Implementar cadastro e consulta de cedentes/recebíveis separando controller, service, repository, model e dto, com validação e strings decimais; verificar 201/Location, 400 para campos inválidos e 404 para consulta inexistente; documentar endpoints no OpenAPI.
- [x] 2.5 Implementar exception handler e correlação comuns; verificar respostas semânticas sem stack trace, SQL ou segredo, com erros de campo e correlationId.

- [x] 2.6 Antecipar testes do backend na CI: executar golden cases, unitários, migrations e integrações PostgreSQL reais; exigir cobertura JaCoCo geral 80%/70% e financeira 95%/90%, publicar resultados/cobertura por 14 dias e comprovar reprovação abaixo dos limites.

## 3. Câmbio e simulação

- [x] 3.1 Criar histórico de câmbio com vigência, origem e constraints; verificar duplicidade, valores inválidos, cotação futura e consulta por identificador em PostgreSQL.
- [x] 3.2 Implementar seleção temporal com validade de 24 horas; verificar ausência, exatamente 24 horas, expiração e escolha da última cotação elegível com clock controlado.
- [x] 3.3 Implementar provedor HTTP mockado e sincronização com duas tentativas totais, timeout de 1 segundo e intervalo de 100 ms; verificar recuperação após falha, limite de 3 segundos, ausência de retry em 4xx/payload inválido e sincronização repetida sem duplicar a mesma cotação.
- [x] 3.4 Expor simulação indicativa sem persistência financeira; verificar BRL sem câmbio, USD com cotação válida, 503 sem cotação e presença de todos os parâmetros de reprodução; documentar simulação e resiliência no OpenAPI/README.

## 4. Liquidação transacional, idempotência e auditoria

- [x] 4.1 Criar migrations de liquidações e idempotência com snapshots, unicidade por recebível e chave por ator/rota; verificar precisão de pagamento `NUMERIC(30,2)` e limites máximos no PostgreSQL.
- [x] 4.2 Separar papéis de migração e execução e proteger histórico; verificar que a credencial real da aplicação não altera/exclui liquidações ou cotações e não altera resultado idempotente já confirmado, mas consegue completar sua reserva na transação.
- [x] 4.3 Implementar reserva da chave, hash canônico e replay antes de recálculo; verificar retry sequencial, chave com outro corpo, duas requisições simultâneas com mesma chave, espera limitada e recuperação após rollback; documentar semântica e retenção.
- [x] 4.4 Implementar confirmação com recálculo, valor/cotação esperados, optimistic locking, flush e commit atômico; verificar sucesso 201, PRICE_CHANGED sem efeito, título indisponível e moeda divergente.
- [x] 4.5 Demonstrar concorrência com duas transações lendo a mesma versão e barreira determinística; verificar exatamente uma confirmação, um 409, uma liquidação e um incremento de versão, sem sleeps arbitrários.
- [x] 4.6 Demonstrar rollback por falha entre gravações e retry após resposta perdida; verificar ausência de registros parciais e reprodução exata de status/corpo/Location após commit.
- [x] 4.7 Implementar consulta da liquidação e concluir OpenAPI do fluxo; verificar reprodução histórica após nova cotação e registrar no README o protocolo de confirmação e seus limites.

## 5. Extrato analítico

- [x] 5.1 Implementar consulta SQL parametrizada com filtros, período semiaberto de até 366 dias, ordenação determinística e paginação até 100; verificar combinações, limites, empates, página vazia e erros de validação em PostgreSQL.
- [x] 5.2 Implementar contagem e totais por moeda para todo o filtro em snapshot consistente por resposta; verificar duas moedas, valores originais, independência da página e consistência perante inserção concorrente.
- [x] 5.3 Adicionar índices iniciais e massa reproduzível de 10000 recebíveis/5000 liquidações; registrar EXPLAIN ANALYZE das consultas principais e documentar limites de paginação no OpenAPI/README.

## 6. Autenticação e painel Angular

- [x] 6.1 Implementar sessão, login/logout, papéis OPERATOR/ADMIN e CSRF com credenciais externas; verificar 401 anônimo, 403 por papel/token inválido, renovação da sessão/token e ausência de credenciais no armazenamento do navegador; documentar setup local.
- [x] 6.2 Criar formulário em português com estado separado da apresentação e valores como strings; verificar teclado, rótulos, mensagens de campo e formatação sem perda de centavos ou conversão monetária para Number.
- [x] 6.3 Conectar simulação com debounce de 300 ms e cancelamento de respostas antigas; verificar entradas inválidas, respostas fora de ordem, indisponibilidade de câmbio e apresentação de parâmetros/moedas.
- [x] 6.4 Integrar cadastro, confirmação e recuperação de retry; verificar chave/corpo estáveis após timeout, duplo clique, PRICE_CHANGED com nova confirmação e consulta de estado após recarga.
- [x] 6.5 Criar grid com filtros remotos, paginação, totais separados e estados vazio/erro/carregando; verificar retorno à página zero quando filtros mudam e conversão dos limites de datas São Paulo/UTC.

## 7. Operação e documentação da plataforma

- [x] 7.1 Adicionar logs JSON e métricas de confirmações/latência; verificar correlação, ausência de segredos/labels de alta cardinalidade e contador que só cresce após commit e não cresce no replay; documentar exemplos de consulta e limites das métricas.
- [x] 7.2 Completar Docker Compose com frontend/proxy, backend, PostgreSQL e mock, healthchecks e configuração externa; verificar subida limpa, credencial restrita da aplicação, readiness sem banco e operação BRL/USD com provedor fora e cotação local válida.
- [x] 7.3 Completar GitHub Actions com testes unitários, integrações PostgreSQL, lint e build dos dois projetos; verificar execução dos golden cases, concorrência, rollback, migrations e testes Angular, sem sucesso aparente após falha obrigatória.
- [x] 7.4 Escrever `REVIEW.md` do Anexo A priorizando impacto financeiro e segurança, com correção e prevenção por achado; verificar cobertura de injeção SQL, taxas percentuais, precisão, idempotência, transação, concorrência, auditoria e falso sucesso.
- [x] 7.5 Atualizar ER e C4 níveis 1 e 2 para refletir a implementação e finalizar README/DECISIONS; verificar setup, comandos, restrições, escolhas de stack e estratégia de branches com links válidos.
- [x] 7.6 Atualizar `AI_USAGE.md` com colaboração efetiva, ao menos um erro real detectado e decisões mantidas pelo autor; verificar a evidência e manter este item pendente se o requisito ainda não puder ser sustentado.

## 8. Validação integrada e preparação da defesa

- [x] 8.1 Executar smoke completo em ambiente limpo: autenticação, cadastro, simulação, liquidação BRL/USD, retry e consulta do extrato; verificar resultado e registrar evidência sem depender de edição manual do banco.
- [x] 8.2 Executar aferição de referência com 10 clientes e ao menos 1000 requisições por rota; acrescentar cenário com orçamento agregado de 2 CPUs/4 GiB, 100000 medidas (50000 por rota) após aquecimento, cotas efetivas e coleta de recursos; registrar hardware, comandos, erros e p95 contra alvos 300/500 ms, preservando tentativas interrompidas e explicando limitações.
- [x] 8.3 Revisar rastreabilidade entre specs, testes e documentação; verificar ausência de alegações não aferidas e preparar roteiro de walkthrough de 10 minutos com demonstração de concorrência e extensão por strategy.

## Workflow follow-up

- Após revisão e implementação concluídas, solicitar o arquivamento da mudança pelo workflow OpenSpec apropriado.
- Publicar branches/PRs e convidar avaliadores somente conforme autorização e destinatários fornecidos; não tratar planejamento local como entrega publicada.
