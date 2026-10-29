# Colaboração com IA

## Planejamento

Instruções do autor: usar Java no backend, Angular no frontend, PostgreSQL, arquitetura MVC em camadas e pacotes criados junto das funcionalidades. Especificar antes do código e executar por etapas: documentação, estrutura inicial e blocos de funcionalidades, com branches e pull requests.

A IA auxilia na organização das seis capacidades OpenSpec, premissas financeiras, golden cases, critérios de segurança e plano de validação. As regras propostas precisam ser verificadas por testes e revisadas pelo autor; geração de arquivos não comprova corretude ou domínio na defesa.

## Decisões mantidas pelo autor

A stack, a arquitetura MVC, a sequência de trabalho e o fluxo de branches/PRs foram definidos pelo autor. A validação comercial das premissas, a revisão do código e a capacidade de modificá-lo na defesa continuam sob sua responsabilidade.

## Evidências a registrar

- Prompts estratégicos de implementação e sua finalidade, sem transcrever sessões inteiras.
- Ao menos um erro concreto da IA e o teste ou review que o detectou. Este item ainda está pendente; não há erro de implementação comprovado nesta etapa.
- Resultados reais de golden cases, concorrência, rollback, retry e verificações da interface.
- Decisões aceitas, corrigidas ou rejeitadas pelo autor ao longo das etapas.

Ainda não foram executados testes da aplicação, smoke ou aferição de desempenho neste repositório. A defesa ao vivo também permanece pendente.

## Estrutura inicial — 28/10/2026

O autor autorizou as tasks 1.4 e 1.5 na branch chore/initial-structure. O escopo permaneceu no bootstrap Spring Boot, Angular estrito, PostgreSQL de desenvolvimento e CI básica. Não foram criados controller/service/repository vazios nem implementadas regras de negócio.

As matrizes oficiais de Spring Boot, Angular e PostgreSQL foram consultadas para as versões fixadas. npm ci, Maven clean verify, ESLint/Prettier e build Angular passaram. A API empacotada iniciou em porta temporária e respondeu health UP. Não há testes de negócio: Maven informa No tests to run, e a CI não declara aferição financeira ou cobertura nesta etapa.

O sandbox bloqueou o bind do Tomcat e encerrou a primeira execução do build Angular; repetir fora do sandbox permitiu verificar os dois. Isso é uma restrição de execução, não um erro financeiro da IA, e não substitui o caso concreto de erro de implementação ainda pendente. A execução remota do workflow também permanece pendente.
