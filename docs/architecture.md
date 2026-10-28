# Arquitetura planejada

Monólito Java/Spring MVC, SPA Angular e PostgreSQL. As funcionalidades criarão seus pacotes conforme a necessidade; a estrutura inicial não deve antecipar camadas vazias.

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

Os diagramas ER e C4 finais serão adicionados e confrontados com a implementação no bloco 7. O [desenho OpenSpec](../openspec/changes/build-srm-credit-engine/design.md) contém os modelos iniciais.
