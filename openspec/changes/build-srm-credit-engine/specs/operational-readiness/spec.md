# Spec Delta

## Purpose

Tornar a aplicação verificável e operável em ambiente local, com diagnóstico de falhas, validação automatizada e documentação suficiente para reproduzir a operação e explicar as decisões técnicas.

## ADDED Requirements

### Requirement: Logs estruturados e correlação
O sistema SHALL emitir logs estruturados com timestamp UTC, nível, evento, correlationId e resultado. Liquidações SHALL registrar identificadores de operador, recebível e liquidação quando disponíveis. Logs não SHALL conter senhas, cookies, tokens ou dados completos de pedidos. A correlação SHALL ser retornada ao cliente.

#### Scenario: Falha rastreável
- **WHEN** uma liquidação falha por conflito ou falha de persistência
- **THEN** o código de resultado e correlationId permitem relacionar a resposta à ocorrência nos logs

### Requirement: Métricas financeiras e latência
O sistema SHALL expor contador de liquidações confirmadas por moeda e temporizador da precificação, com dimensões limitadas. Retries idempotentes não SHALL incrementar o contador de confirmações. A documentação SHALL explicar que métricas em memória não substituem o extrato financeiro persistido.

#### Scenario: Retry não aumenta produção aparente
- **WHEN** uma liquidação é confirmada e sua requisição é repetida três vezes
- **THEN** há uma confirmação adicional na métrica, não quatro

### Requirement: Ambiente reproduzível e diagnóstico
A entrega SHALL iniciar frontend, backend, PostgreSQL e provedor mockado via Docker Compose, com migrations e dados demonstrativos opcionais. SHALL separar credenciais de migração e execução. Readiness SHALL sinalizar banco indisponível; falha do provedor SHALL ser observável sem bloquear operações BRL ou USD com cotação válida.

#### Scenario: Inicialização limpa
- **WHEN** o avaliador segue o README em ambiente com Docker Compose e configura as variáveis exigidas
- **THEN** consegue autenticar, simular e liquidar um recebível sem ajustes manuais no banco

#### Scenario: Banco indisponível
- **WHEN** o banco fica inacessível
- **THEN** readiness informa indisponibilidade sem expor credenciais ou detalhes de conexão

### Requirement: CI como critério de integração
A CI SHALL executar em pull requests testes/lint/build do backend e frontend, incluindo golden cases, rollback, idempotência e concorrência com PostgreSQL real. SHALL testar as migrations e impedir aprovação automática de uma execução com etapa obrigatória falha. O teste de concorrência SHALL demonstrar disputa da mesma versão, sem depender de sleeps arbitrários.

#### Scenario: Regressão financeira
- **WHEN** uma alteração muda qualquer centavo de C1, C2 ou C3
- **THEN** a CI falha e identifica o caso divergente

#### Scenario: Concorrência verificável
- **WHEN** o teste coordena duas operações com a mesma versão disponível
- **THEN** confirma um sucesso, um conflito e somente uma liquidação no banco

### Requirement: Cobertura mínima verificável
O backend SHALL exigir no `mvn verify`, via JaCoCo, 80% das linhas e 70% dos branches no código de produção. Cada classe PricingEngine, FinancialAmounts, MonthlyTermPolicy e cada strategy SHALL atingir 95% das linhas e 90% dos branches. Não SHALL excluir código de produção apenas para atingir os limites.

#### Scenario: Cobertura abaixo do limite
- **WHEN** os testes passam, mas uma regra de cobertura não é atingida
- **THEN** o build Maven e o job de backend falham e o relatório identifica a lacuna

### Requirement: Publicação de evidências da CI
A CI SHALL publicar relatórios HTML/XML de cobertura e resultados de testes por 14 dias, inclusive após falhas quando houver dados disponíveis. A publicação não SHALL ocultar a reprovação do job.

#### Scenario: Relatório disponível para revisão
- **WHEN** a execução produz dados de testes e cobertura
- **THEN** os artefatos ficam disponíveis na CI por 14 dias, com contadores e fontes navegáveis no relatório HTML

### Requirement: Critérios mensuráveis de desempenho
A entrega SHALL medir simulação e extrato com 10000 recebíveis, 5000 liquidações, 10 clientes concorrentes e ao menos 1000 requisições por rota após aquecimento. O alvo SHALL ser p95 de até 300 ms para simulação e 500 ms para extrato, em ambiente local com ao menos 4 vCPUs e 8 GB RAM disponíveis. Hardware, comandos e resultados SHALL ser documentados.

#### Scenario: Aferição local
- **WHEN** o cenário de aferição é executado com filtros e página de tamanho 20
- **THEN** o relatório registra p95, erros, volume e ambiente, comparando resultados com os alvos sem declarar garantia de produção

### Requirement: Documentação específica e autoria verificável
A entrega SHALL conter SPEC.md de 1–2 páginas anterior ao código, README, DECISIONS.md, REVIEW.md priorizado do Anexo A, AI_USAGE.md, OpenAPI e diagramas ER/C4 1 e 2. AI_USAGE.md SHALL distinguir evidências reais de itens pendentes e registrar um erro real da IA com sua detecção antes da entrega final, sem fabricar histórico.

#### Scenario: Preparação da defesa
- **WHEN** o avaliador lê a documentação
- **THEN** consegue reproduzir os golden cases, entender a transação e seus cortes, consultar a API e executar o cenário de concorrência

#### Scenario: Evidência de uso de IA
- **WHEN** ainda não existe erro real da IA documentado
- **THEN** AI_USAGE.md identifica a lacuna como pendente e a entrega não declara esse requisito concluído

### Requirement: Histórico de desenvolvimento rastreável
O desenvolvimento SHALL usar GitHub Flow, com branches por funcionalidade e integração na main por pull requests. Commits SHALL seguir Conventional Commits. Descrições de PR SHALL explicar problema, decisão e validação. O README SHALL registrar a estratégia de branches e sua justificativa. Publicação de PRs e convites SHALL usar somente acessos e destinatários fornecidos pelo usuário.

#### Scenario: Revisão de mudança financeira
- **WHEN** uma alteração de cálculo ou liquidação é apresentada para integração
- **THEN** sua descrição informa a mudança de comportamento e a evidência de validação sem inventar commits ou revisões
