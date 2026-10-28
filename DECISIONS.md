# Decisões e limites

## Stack e arquitetura

Java/Spring Boot, Angular e PostgreSQL são as escolhas do projeto. O backend seguirá MVC com controller, service, repository, model e dto, apoiados por config e exception. As camadas terão responsabilidades concretas; pacotes serão criados somente ao implementar funcionalidades. Não antecipar pastas vazias no esqueleto.

Adotar monólito: uma aquisição exige atomicidade entre estado, auditoria e idempotência. Serviços distribuídos adicionariam coordenação sem benefício neste escopo. O extrato passará por service e repository com SQL parametrizado. Strategies escolherão spreads; um único motor aplicará a fórmula. Angular usará estado local por funcionalidade, sem gerenciador global inicialmente.

As versões compatíveis de Java LTS/Spring Boot, Angular/Node e PostgreSQL serão fixadas na estrutura inicial, com referências e lockfiles. Esta etapa não contém dependências executáveis.

## Política financeira

Base de 1% a.m.; spreads de 1,5% e 2,5%; prazo por teto(dias/30), limitado a 36 meses. Half-even no VP BRL e no pagamento convertido; cotação BRL por USD válida por até 24 horas. São premissas revisáveis do case, registradas no SPEC, não regras comerciais confirmadas. A política será versionada e preservada no histórico.

## Consistência e disponibilidade

Transação por recebível, optimistic locking e unicidade no banco. Idempotência por ator/rota/chave com hash do corpo e retenção dos sucessos sem expiração. O histórico financeiro será imutável pela credencial de execução; administradores do banco continuam uma fronteira de confiança.

Atualização cambial separada da confirmação evita dependência externa dentro da transação. USD dependerá de cotação local válida; BRL continuará durante falha do provedor. O modelo local de identidade, a retenção idempotente sem expiração e o uso de offset precisarão ter seus limites explicitados na implementação.

## Cortes deliberados

Sem pagamentos reais, saldo, liquidação parcial, estorno, importação de arquivos, atomicidade de lote, multi-tenancy, broker ou Redis. Identidade corporativa, escala distribuída, EDA e post-mortem do Anexo B ficam fora desta entrega. O foco é corretude financeira, integridade e validação do fluxo operacional.

## Versionamento

GitHub Flow: branches curtas por funcionalidade e integração por pull requests na main. Conventional Commits em inglês e mudanças focadas. Cada PR explicará problema, decisão e evidências reais de validação; o template está em .github/pull_request_template.md. A CI começará com a estrutura e evoluirá junto das features. Não declarar testes, revisões, proteção de branch ou publicação que ainda não tenham ocorrido.

## Estado

Somente especificações, planejamento e documentação inicial. Implementação, testes, resultados de desempenho, smoke, defesa e acesso dos avaliadores permanecem pendentes. O checklist OpenSpec registra o progresso deste repositório.
