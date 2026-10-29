# SRM Credit Engine

Plataforma de cessão de crédito com backend Java/Spring Boot, frontend Angular e PostgreSQL. Recebíveis em BRL poderão ser precificados e adquiridos com pagamento em BRL ou USD.

## Estado

Etapa de especificação. Ainda não há aplicação executável, imagens, testes ou CI configurados. Os comandos de execução serão adicionados junto da estrutura inicial e validados antes de serem apresentados como funcionais.

- [SPEC.md](SPEC.md): premissas, precisão, golden cases e critérios de aceite.
- [DECISIONS.md](DECISIONS.md): decisões de arquitetura e cortes.
- [AI_USAGE.md](AI_USAGE.md): colaboração com IA e evidências pendentes.
- [Arquitetura MVC](docs/architecture.md): responsabilidades das camadas.
- [Proposta](openspec/changes/build-srm-credit-engine/proposal.md), [desenho](openspec/changes/build-srm-credit-engine/design.md) e [tasks](openspec/changes/build-srm-credit-engine/tasks.md).

Java/Spring Boot oferece tipagem forte, transações e segurança; Angular organiza formulário, HTTP e estado tipados; PostgreSQL fornece precisão decimal, integridade referencial e transações ACID. Um monólito permite confirmar cada aquisição em uma única transação, sem coordenação entre serviços.

## Etapas

1. Especificações e documentação inicial.
2. Estrutura inicial: Maven Wrapper, Angular, versões fixadas, lint e CI básica.
3. Motor financeiro e cadastro — bloco 2 das tasks.
4. Câmbio e simulação — bloco 3.
5. Liquidação, idempotência e auditoria — bloco 4.
6. Extrato analítico — bloco 5.
7. Autenticação e painel — bloco 6.
8. Operação e documentação — bloco 7.
9. Validação integrada e preparação da defesa — bloco 8.

## Versionamento e revisão

Adotar GitHub Flow: branches curtas por funcionalidade a partir da main, commits atômicos em inglês no formato Conventional Commits e integração por pull request. O fluxo mantém as mudanças revisáveis sem branches permanentes adicionais. Implementações não devem ser feitas diretamente na main.

Exemplos de branches: docs/project-specifications, chore/initial-structure e feat/receivable-pricing. Cada PR deve explicar o problema, a decisão e a validação real, conforme o [template](.github/pull_request_template.md). A CI será introduzida com a estrutura inicial e ampliada conforme as funcionalidades; nenhuma execução remota é declarada nesta etapa.

## Planejamento OpenSpec

Com o OpenSpec CLI instalado, executar na raiz:

```sh
openspec status --change build-srm-credit-engine
openspec instructions apply --change build-srm-credit-engine --json
openspec validate build-srm-credit-engine --strict
```

As seis specs definem os contratos: [precificação](openspec/changes/build-srm-credit-engine/specs/receivable-pricing/spec.md), [câmbio](openspec/changes/build-srm-credit-engine/specs/currency-rates/spec.md), [liquidação](openspec/changes/build-srm-credit-engine/specs/settlement-integrity/spec.md), [extrato](openspec/changes/build-srm-credit-engine/specs/settlement-statements/spec.md), [painel](openspec/changes/build-srm-credit-engine/specs/operator-workspace/spec.md) e [operação](openspec/changes/build-srm-credit-engine/specs/operational-readiness/spec.md).
