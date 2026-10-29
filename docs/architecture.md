# Arquitetura MVC

Monólito Java/Spring MVC, SPA Angular e PostgreSQL. O bloco 2 cria as camadas necessárias ao cadastro e ao motor. As demais funcionalidades seguirão as mesmas responsabilidades, sem antecipar camadas vazias.

| Camada | Responsabilidade |
|---|---|
| controller | HTTP, DTOs, validação de entrada e códigos de resposta; delegar ao service. |
| service | Casos de uso, autorização de negócio e fronteiras transacionais. |
| repository | Persistência JPA e SQL parametrizado para consultas analíticas. |
| model | Entidades, valores, política de prazo e motor financeiro; strategies em model.strategy. |
| dto | Contratos REST independentes das entidades persistidas. |
| config / exception | Configuração, segurança, observabilidade e tratamento global de erros. |

O controller não acessará repositories diretamente, inclusive no extrato. Evitar interfaces intermediárias sem função concreta. O cálculo deve ser testável sem iniciar Spring ou banco.

Angular separará apresentação, serviços de API e estado local por fluxo. Dinheiro e taxas serão representados por strings decimais, sem aritmética monetária binária no navegador.

A aquisição confirmará recebível, snapshot financeiro e resposta idempotente na mesma transação. Câmbio externo será sincronizado separadamente; a liquidação consultará cotação persistida válida. O extrato usará SQL parametrizado e snapshot consistente por resposta.

O [ER](er.md) reflete as tabelas e índices das migrations V1–V5. O C4 final será adicionado e confrontado com a implementação no bloco 7. O [desenho OpenSpec](../openspec/changes/build-srm-credit-engine/design.md) contém os modelos iniciais.

## Liquidação implementada

SettlementController delega a SettlementService, que valida a intenção e chama o proxy de SettlementTransactionService. Esse service abre uma transação nova para ler/controlar versão, reservar a chave, gravar auditoria e completar a resposta. JDBC e JPA compartilham a transação; a fachada só recebe o resultado após commit. O replay usa o JSON textual persistido para preservar bytes e Location. Detalhes em [liquidação](settlements.md).

O login de negócio e o de migration são distintos; o [bootstrap e as permissões](database.md) acompanham as migrations. O [ER](er.md) cobre V1–V5. Controllers anteriores e o novo fluxo documentam erros como Problem Details; a consulta individual da liquidação declara a resposta 200 com SettlementResponse.

## Extrato implementado

GET /settlements passa pelo mesmo controller e delega a StatementService → StatementRepository, mantendo a separação MVC. O repository usa NamedParameterJdbcTemplate com predicados de fragmentos fixos e todos os valores parametrizados. O mapeamento de SettlementResponse reutiliza o snapshot existente; não depende do motor ou de cotação atual.

O service abre uma transação read-only REQUIRES_NEW/REPEATABLE READ para página, contagem e totais. StatementQuery valida intervalo/paginação e calcula offset como long. Moedas são agregadas separadamente e valores saem como strings. A consistência vale para uma resposta; navegar entre páginas pode observar novas confirmações. [Detalhes](statements.md).

## Sessão e painel implementados

Spring Security autentica e encerra sessões pelos filtros do framework; AuthController expõe apenas CSRF e identidade. Não há acesso a repositories nesse controller. Credenciais externas de duas contas são codificadas com BCrypt na inicialização; ADMIN acumula OPERATOR. A sessão protege as rotas de negócio e mantém CSRF nas mutações.

Angular separa templates/componentes, CreditApi, Session e estados CreditState/StatementState. switchMap cancela a simulação anterior imediatamente, inclusive durante os 300 ms de espera da nova entrada. A confirmação guarda chave/corpo em memória até um resultado definido; nenhuma confirmação é repetida automaticamente. Dinheiro permanece string. Os filtros do extrato convertem datas civis São Paulo para instantes UTC. [Painel e limites](workspace.md).
