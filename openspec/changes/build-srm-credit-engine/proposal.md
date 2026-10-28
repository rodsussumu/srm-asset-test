# Proposal

## Why

A mesa da SRM precisa precificar e registrar aquisições de recebíveis em BRL com pagamento em BRL ou USD, preservando precisão decimal e rastreabilidade. O desafio exige demonstrar que retries, concorrência e falhas de câmbio não produzem liquidações duplicadas ou parciais.

## What Changes

- Criar uma plataforma de cessão de crédito com backend Java/Spring Boot MVC, frontend Angular e PostgreSQL, em um monólito com camadas controller, service, repository, model e dto, apoiadas por config e exception.
- Cadastrar cedentes e recebíveis, simular valores líquidos com strategies de spread e reproduzir os três golden cases do enunciado.
- Manter cotações com vigência e histórico; integrar um provedor mockado com timeout e retry limitado.
- Registrar liquidações ACID, idempotentes, protegidas por optimistic locking e unicidade por recebível, com auditoria imutável.
- Entregar painel em português, simulação reativa e extrato com filtros e paginação no servidor.
- Incluir controle de acesso simples para a demonstração, validação, erros semânticos, OpenAPI, Docker Compose, logs estruturados, métricas e CI.
- Preparar SPEC.md antes de código, DECISIONS.md, REVIEW.md do Anexo A, AI_USAGE.md com evidências reais, README e diagramas ER/C4 níveis 1 e 2.
- Registrar cortes: sem transferência bancária, gestão de saldo do fundo, liquidação parcial, microserviços ou implementação de arquitetura para um milhão de transações/minuto. Um lote será tratado como uma sequência de recebíveis com atomicidade individual; importação de arquivos e atomicidade do lote ficam fora da primeira entrega.

## Capabilities

### New Capabilities

- `receivable-pricing`: cadastro, elegibilidade, prazo, estratégias, precisão e simulação financeira.
- `currency-rates`: cotações vigentes, histórico, atualização manual e sincronização resiliente.
- `settlement-integrity`: liquidação transacional, idempotência, concorrência e auditoria imutável.
- `settlement-statements`: consulta analítica paginada com filtros e totais por moeda.
- `operator-workspace`: acesso do operador, formulário, simulação e fluxo de liquidação no Angular.
- `operational-readiness`: diagnóstico, métricas, ambiente reproduzível, CI e documentação da entrega.

### Modified Capabilities

Nenhuma. O repositório está em planejamento, sem implementação ou capacidades existentes.

## Impact

Serão adicionados `backend/`, `frontend/`, migrations, configuração de infraestrutura e documentação na implementação futura. A API REST `/api/v1` será nova; não há consumidores existentes ou migração de dados. Dependências previstas: Spring Boot, Spring Security, persistência JPA, consultas SQL para extrato, Flyway, testes com PostgreSQL real, Angular e ferramentas de build/lint. As versões compatíveis e suportadas serão fixadas no início da implementação e registradas no README e nos arquivos de build. Esta mudança contém somente planejamento.
