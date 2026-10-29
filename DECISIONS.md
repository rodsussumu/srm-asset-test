# Decisões e limites

## Stack e arquitetura

Java/Spring Boot, Angular e PostgreSQL são as escolhas do projeto. O backend seguirá MVC com controller, service, repository, model e dto, apoiados por config e exception. As camadas terão responsabilidades concretas; pacotes serão criados somente ao implementar funcionalidades. Não antecipar pastas vazias no esqueleto.

Adotar monólito: uma aquisição exige atomicidade entre estado, auditoria e idempotência. Serviços distribuídos adicionariam coordenação sem benefício neste escopo. O extrato passará por service e repository com SQL parametrizado. Strategies escolherão spreads; um único motor aplicará a fórmula. Angular usará estado local por funcionalidade, sem gerenciador global inicialmente.

A estrutura fixa Java 21, Spring Boot 3.5.16, Maven 3.9.16, Angular 21.2.0, Node 22.16.0, TypeScript 5.9.2 e PostgreSQL 17.11. Dependências e compatibilidade estão em [toolchain](docs/versions.md). O bloco 2 acrescenta JPA, Flyway, PostgreSQL, Bean Validation, Spring Security, OpenAPI e Testcontainers para os casos de uso implementados. A autenticação inicial usa configuração externa/Basic e formulário padrão, com CSRF ativo; sessão própria e papéis vêm no bloco 6.

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

Bloco 2 implementado e validado localmente: motor decimal, prazo/elegibilidade, cadastro/consulta MVC, V1, precisão/constraints e erros correlacionados. O teste de versão antiga comprova o mecanismo otimista no modelo; a disputa entre liquidações permanece no bloco 4.

Por orientação do usuário, testes, cobertura e relatórios do backend foram antecipados para a task 2.6. A CI usa clean verify: 74 testes locais passaram, com PostgreSQL descartável real e os golden cases. JaCoCo exige 80%/70% geral e 95%/90% por classe financeira, sem exclusões de produção. Publicação de artefatos não transforma falha em sucesso. O frontend continua com lint/build até a implementação do painel; não há suíte vazia simulando validação funcional.

Flyway e aplicação ainda podem usar o mesmo login neste bloco; as credenciais segregadas e a proteção do histórico serão implantadas antes da liquidação, no bloco 4. Os testes usam banco próprio do Testcontainers e não dependem do volume Compose. Não houve execução remota da CI desta branch, smoke financeiro integrado ou aferição de desempenho. Resultados em [qualidade](docs/quality.md).

## Câmbio e simulação — bloco 3

V2 mantém histórico USD/BRL com NUMERIC(16,8), vigência/registro em UTC e unicidade por par/vigência. Entradas aceitam até microssegundos, evitando colisões silenciosas de precisão no PostgreSQL. A seleção ignora vigências futuras e registros posteriores ao instante da operação; exatamente 24 horas ainda é válido.

O provedor HTTP é chamado antes da transação de gravação, com duas tentativas totais e timeout de 1 segundo sobre a resposta inteira, incluindo corpo bloqueado, mais 100 ms entre tentativas. Somente timeout/conexão/5xx são transitórios neste contrato; 4xx/payload inválido falham sem retry. A sincronização usa ON CONFLICT DO NOTHING: mesma vigência/valor recupera o original; outro valor conflita. Não reescreve origem ou registro histórico.

A simulação captura um único instante para prazo e câmbio, é read-only e não cadastra recebível/liquidação. USD usa cotação local; BRL não depende dela. APP_ROLES protege mutações cambiais com ADMIN já nesta etapa, mantendo CSRF. A conta ainda é única; contas separadas e autenticação própria ficam no bloco 6.

O Compose acrescenta somente o mock em Python stdlib, sem acoplá-lo à inicialização da API. A vigência é fixada ao iniciar, com sincronização explícita; não há alegação de dados de mercado. A credencial do banco ainda será segregada e impedida de alterar histórico no bloco 4. Logs de tentativas e contador existem agora; o formato JSON e as métricas financeiras do produto permanecem no bloco 7.

Validação atual: 126 testes, 96,68% linhas/86,36% branches; limites JaCoCo preservados. A suíte HTTP prova as duas tentativas e término abaixo de 3 segundos nos cenários locais de timeout, sem inferir capacidade de produção. [Detalhes](docs/quality.md).
