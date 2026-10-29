# Decisões e limites

## Stack e arquitetura

Java/Spring Boot, Angular e PostgreSQL são as escolhas do projeto. O backend seguirá MVC com controller, service, repository, model e dto, apoiados por config e exception. As camadas terão responsabilidades concretas; pacotes serão criados somente ao implementar funcionalidades. Não antecipar pastas vazias no esqueleto.

Adotar monólito: uma aquisição exige atomicidade entre estado, auditoria e idempotência. Serviços distribuídos adicionariam coordenação sem benefício neste escopo. O extrato passará por service e repository com SQL parametrizado. Strategies escolherão spreads; um único motor aplicará a fórmula. Angular usará estado local por funcionalidade, sem gerenciador global inicialmente.

A estrutura fixa Java 21, Spring Boot 3.5.16, Maven 3.9.16, Angular 21.2.0, Node 22.16.0, TypeScript 5.9.2 e PostgreSQL 17.11. Dependências e compatibilidade estão em [toolchain](docs/versions.md). O bloco 2 acrescenta JPA, Flyway, PostgreSQL, Bean Validation, Spring Security, OpenAPI e Testcontainers para os casos de uso implementados. A autenticação usa duas contas externas, sessão e CSRF desde o bloco 6.

## Política financeira

Base de 1% a.m.; spreads de 1,5% e 2,5%; prazo por teto(dias/30), limitado a 36 meses. Half-even no VP BRL e no pagamento convertido; cotação BRL por USD válida por até 24 horas. São premissas revisáveis do case, registradas no SPEC, não regras comerciais confirmadas. A política será versionada e preservada no histórico.

## Consistência e disponibilidade

Transação por recebível, optimistic locking e unicidade no banco. Idempotência por ator/rota/chave com hash do corpo e retenção dos sucessos sem expiração. O histórico financeiro será imutável pela credencial de execução; administradores do banco continuam uma fronteira de confiança.

Atualização cambial separada da confirmação evita dependência externa dentro da transação. USD dependerá de cotação local válida; BRL continuará durante falha do provedor. O modelo local de identidade, a retenção idempotente sem expiração e o uso de offset precisarão ter seus limites explicitados na implementação.

## Cortes deliberados

Sem pagamentos reais, saldo, liquidação parcial, estorno, importação de arquivos, atomicidade de lote, multi-tenancy, broker ou Redis. Identidade corporativa, escala distribuída, EDA e post-mortem do Anexo B ficam fora desta entrega. O foco é corretude financeira, integridade e validação do fluxo operacional.

## Versionamento

GitHub Flow: branches curtas por funcionalidade e integração por pull requests na main. Conventional Commits em inglês e mudanças focadas. Cada PR explicará problema, decisão e evidências reais de validação; o template está em .github/pull_request_template.md. A CI começará com a estrutura e evoluirá junto das features. Não declarar testes, revisões, proteção de branch ou publicação que ainda não tenham ocorrido.

## Estado e validação

Blocos 2 a 8 implementados e validados localmente. A CI evoluiu com as funcionalidades: testes/cobertura backend foram antecipados para 2.6 por decisão do autor; npm test entrou no bloco 6 e os relatórios Angular no bloco 7. A validação atual inclui PostgreSQL real com login restrito, golden cases, transações concorrentes e falhas de persistência/commit. Não se usa H2 ou exclusões de produção para cumprir limites. [Evidências por etapa](docs/quality.md).

Operação usa Compose completo e credenciais SQL distintas. A API ainda recebe o segredo de migração ao iniciar; isso não isola um processo comprometido. Smoke final e p95 HTTP foram aferidos no bloco 8, com roteiro de walkthrough preparado. Resultados locais não comprovam execução remota do CI desta branch.

## Câmbio e simulação — bloco 3

V2 mantém histórico USD/BRL com NUMERIC(16,8), vigência/registro em UTC e unicidade por par/vigência. Entradas aceitam até microssegundos, evitando colisões silenciosas de precisão no PostgreSQL. A seleção ignora vigências futuras e registros posteriores ao instante da operação; exatamente 24 horas ainda é válido.

O provedor HTTP é chamado antes da transação de gravação, com duas tentativas totais e timeout de 1 segundo sobre a resposta inteira, incluindo corpo bloqueado, mais 100 ms entre tentativas. Somente timeout/conexão/5xx são transitórios neste contrato; 4xx/payload inválido falham sem retry. A sincronização usa ON CONFLICT DO NOTHING: mesma vigência/valor recupera o original; outro valor conflita. Não reescreve origem ou registro histórico.

A simulação captura um único instante para prazo e câmbio, é read-only e não cadastra recebível/liquidação. USD usa cotação local; BRL não depende dela. As mutações cambiais exigem ADMIN e CSRF. A conta inicial única foi substituída por contas separadas no bloco 6.

O bloco 3 acrescentou o mock em Python stdlib, sem acoplá-lo à inicialização da API; o bloco 7 completou a orquestração. A vigência é fixada ao iniciar, com sincronização explícita; não há alegação de dados de mercado. A credencial do banco foi segregada no bloco 4. Logs JSON e métricas financeiras foram concluídos no bloco 7.

Validação atual: 126 testes, 96,68% linhas/86,36% branches; limites JaCoCo preservados. A suíte HTTP prova as duas tentativas e término abaixo de 3 segundos nos cenários locais de timeout, sem inferir capacidade de produção. [Detalhes](docs/quality.md).

## Liquidação e credenciais — bloco 4

A fachada SettlementService não é transacional; ela chama SettlementTransactionService separado, com REQUIRES_NEW/READ COMMITTED. O proxy efetiva o commit antes de retornar à fachada e ao controller. Um sucesso HTTP não pode preceder o commit real. JPA faz a transição de status/version com DynamicUpdate/flush e JDBC grava snapshots/idempotência na mesma conexão transacional.

A chave UUID é escopada por ator/rota e associada a SHA-256 do pedido normalizado. Reserva, resultado e auditoria estão na mesma transação. Replay recupera bytes/status/Location antes de recálculo. O lock_timeout local limita a espera a 3 segundos e SQLSTATE 55P03 vira REQUEST_IN_PROGRESS fora da transação abortada. Uma constraint diferida rejeita commit de reserva incompleta. Sucessos permanecem sem expiração nesta entrega.

Liquidações e câmbio não têm UPDATE/DELETE/TRUNCATE pelo runtime, que não é dono das tabelas. O bootstrap cria login separado com papel NOLOGIN srm_runtime; Flyway usa o dono do esquema. A aplicação ainda recebe a credencial de migração ao iniciar: a separação de conexões não protege contra comprometimento completo do processo. Executar migrations em job independente é evolução operacional explícita.

O teste financeiro usa login real srm_app e duas transações lendo a mesma versão sob barreira; espera um commit, um conflito, uma auditoria e um incremento. Falhas antes/depois do INSERT, no commit diferido e após commit na resposta verificam rollback e recuperação. Os mecanismos de coordenação/injeção permanecem exclusivamente no harness de testes. Não há hooks no código de produção.

O diagrama acompanha as constraints: settlement_id da idempotência é FK sem UNIQUE, portanto não se promete relação 1:1 no banco. O fluxo normal cria um resultado por intenção; não foi adicionada migration apenas para acomodar um diagrama. O extrato foi acrescentado no bloco 5 e as métricas pós-commit no bloco 7, preservando essa fronteira transacional.

## Extrato e consultas SQL — bloco 5

O extrato usa três consultas parametrizadas em uma transação própria read-only REPEATABLE READ: página, contagem e totais por moeda. Isso evita misturar snapshots quando outra liquidação confirma durante a resposta. REQUIRES_NEW garante a fronteira mesmo se o service for chamado a partir de outro contexto transacional. Não se promete o mesmo snapshot entre requisições de páginas distintas.

Filtros opcionais são fragmentos fixos e valores são vinculados por parâmetros. Ordenação é fixa por instante/UUID decrescentes; offset usa long. Limites ISO com nanossegundos são elevados à grade de microssegundos do PostgreSQL nas comparações, preservando o intervalo semiaberto contra os instantes armazenados. Totais não recalculam preço nem misturam BRL/USD e podem exceder a capacidade de um pagamento individual.

V5 adiciona três índices iniciais. O teste de planos executa as mesmas strings do repository com papel restrito em PostgreSQL descartável, após carga idempotente de 10000 recebíveis/5000 liquidações e VACUUM ANALYZE. Mantivemos os índices sem forçar o planejador ou criar um índice combinado sem evidência. Contagens e totais amplos ainda percorrem todo o filtro; offset alto pode custar mais. O fixture SQL não executa a API de confirmação nem resultados idempotentes e não comprova throughput financeiro ou p95 HTTP.

Resultado atual: 190 testes, cobertura geral 96,78% linhas/86,97% branches, preservando todos os mínimos. A CI inclui o relatório de planos nos artefatos de testes. O painel foi concluído no bloco 6; a aferição HTTP do bloco 8 foi registrada com seus limites locais. A execução remota da branch atual ainda não foi verificada. [Planos e método](docs/statements.md).

## Sessão e painel — bloco 6

Duas contas externas em memória substituem a conta provisória e HTTP Basic. ADMIN também opera; nomes distintos e estáveis preservam autoria/escopo idempotente. PasswordEncoder delega a BCrypt, login usa o filtro do Spring Security para renovar a sessão/CSRF e logout invalida cookie. Credenciais e tokens não vão ao armazenamento do navegador. Sessão de 30 minutos, HttpOnly/SameSite=Lax; COOKIE_SECURE=false apenas para HTTP local. Este modelo demonstrativo não inclui MFA, federação, governança, limitação de tentativas ou sessões distribuídas.

O Angular usa serviços locais por fluxo, sem store global. O switchMap externo cancela a requisição antiga assim que uma entrada muda; timer(300) espera a estabilização da nova entrada. O cliente não calcula dinheiro: normaliza e agrupa strings, inclusive valores acima do limite seguro de inteiros JavaScript. A intenção de confirmação conserva UUID/corpo em memória para retry manual; PRICE_CHANGED requer novo preço e aceite. Após recarga, consulta-se estado/histórico antes de tentar novamente. Cadastro de título não é idempotente; resposta perdida requer consulta antes de recadastro.

Datas do extrato pertencem a São Paulo, com dia final inclusivo convertido ao início exclusivo do dia seguinte, incluindo transições históricas de horário de verão. Contagem/totais vêm do servidor. A CI passa a executar a suíte Angular, mantendo os gates backend. O bloco 7 completou Compose/observabilidade; a validação final local está registrada no bloco 8.

## Operação e prevenção — bloco 7

Logs JSON padronizam evento, outcome e correlação; timestamp é normalizado a UTC sem mudar o relógio financeiro. Evitamos body, paths/queries arbitrários, headers e detalhes SQL nos eventos da aplicação. IDs de auditoria ficam nos logs, sem virar tags de métricas. O contador de confirmações fica na fachada depois do retorno do proxy transacional, apenas em confirmação nova. Ele pode perder eventos entre commit/emissão ou reinício: o extrato persistido é a autoridade. Timer envolve somente o motor e aceita moeda/resultado como dimensões limitadas; não representa p95 HTTP. [Semântica](docs/observability.md).

Compose fornece nginx/proxy, API, PostgreSQL, bootstrap e mock com healthchecks. A API independe do mock para iniciar/confirmar; readiness verifica SQL e liveness verifica o processo. Hikari limita aquisição/validação de conexões a 2 s/1 s. O build da imagem não executa integrações que exigem Docker; clean verify obrigatório da CI faz essa validação fora do builder. Não há deploy automático, coletor Prometheus ou orquestração de produção.

REVIEW prioriza perda financeira/segurança no Anexo A, com correção e prevenção por achado, distinguindo ausência no trecho de ausência em toda a aplicação. O ER e C4 representam o sistema implementado, sem contêineres fictícios para as camadas MVC. GitHub Flow/PRs mantêm as decisões revisáveis. Não há estudo de alta escala, EDA ou post-mortem do Anexo B nesta entrega.

## Validação final e limites — bloco 8

Smoke e benchmark usam volumes distintos: smoke cria registros por API/SPA, e a massa SQL serve somente à consulta analítica. A medição usa 10 clientes, 1000 amostras/rota após 200 de aquecimento, sem retry, pelo proxy de mesma origem. p95 foi recalculado do CSV, com 2000 HTTP 200 e erro zero. Os alvos foram atendidos nessa amostra curta/aquecida; não se conclui capacidade sustentada, saturação ou throughput de confirmação. [Relatório](docs/performance.md).

O exemplo de extensão é uma strategy alternativa somente em teste, preservando API/schema e fórmula de produção. Um tipo novo real exige enum, registro da strategy, nova migration das CHECKs, contrato/frontend e novos casos de aceite. [Roteiro e comandos](docs/walkthrough.md).

A matriz relaciona 32 requisitos/59 cenários a testes/documentos, com nomes e links conferidos. Os 39 itens de implementação estão concluídos localmente; a aprovação de negócio, revisão humana final, CI remota/artefatos, convites e defesa não foram presumidos. O projeto continua sem transferência bancária ou garantias de produção além do recorte testado.

## Cenário restrito de desempenho — decisão posterior à referência

O autor escolheu orçamento para todo o conjunto (não apenas API) e 100 mil medidas totais, 50 mil por rota. Mantivemos a referência anterior e criamos override de Compose: API 1,25 CPU/2 GiB, PostgreSQL 0,5 CPU/1,5 GiB, nginx 0,15 CPU/256 MiB e mock 0,1 CPU/256 MiB, sem swap adicional. Cotas fixas por serviço somam 2 CPUs/4 GiB, sem prometer equivalência a uma VM que permita compartilhar capacidade ociosa.

A primeira tentativa foi comprometida por suspensão do host e preservada com falhas. Após recriar o banco, inibir temporariamente suspensão por inatividade e limitar o driver a 900 s, a segunda concluiu 100 mil respostas válidas sem erro, p95 60,923/94,195 ms e sem OOM/reinício. Não foram alteradas queries/aplicação para selecionar resultado favorável. O coletor omitia backend por controles ANSI; a lacuna foi registrada, o parser corrigido e conferido por replay, sem refazer a carga para ocultá-la. Contadores cgroup antes/depois cobrem os quatro serviços. [Relatório e limites](docs/performance-constrained.md).
