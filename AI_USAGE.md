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

## Bloco 2 e antecipação da CI — 28/10/2026

O autor autorizou o próximo bloco após integrar a estrutura inicial e pediu antecipar a parte de testes da pipeline. Na branch feat/receivable-pricing, o escopo passou a incluir 2.1–2.5 e o novo item 2.6 de CI/cobertura backend, preservando o painel e sua suíte para o bloco correspondente.

Foram implementados motor/strategies decimais, elegibilidade com Clock, migration de cedentes/recebíveis, cadastro/consulta MVC, DTOs estritos, OpenAPI e tratamento de erros com correlação. O plano financeiro usa potência inteira, divisão com 50 dígitos e half-even no VP BRL e no pagamento convertido; C1/C2/C3 são verificados diretamente por valores esperados, sem tolerância de centavos.

A validação limpa passou com 74 testes: 37 do motor, 9 de prazo e 28 de integração PostgreSQL real, sem falhas/erros/skips. O acesso ao Docker foi bloqueado no sandbox; a execução fora dele passou, sem substituir PostgreSQL por H2 ou ignorar testes. JaCoCo registrou 299/308 linhas (97,08%) e 71/86 branches (82,56%), aprovando os mínimos gerais e financeiros sem exclusões. Limites temporários de 100% passados por CLI reprovaram com Rule violated/exit 1; a repetição com os padrões passou sem alterações no fonte ou pom.

A CI publica Surefire/JaCoCo por 14 dias e executa clean verify. Ainda não há evidência de execução remota desta branch, teste de liquidação concorrente, smoke financeiro integrado ou desempenho. A versão otimista é testada com uma entidade antiga; duas intenções financeiras simultâneas virão no bloco 4. O erro real de implementação da IA exigido no relatório continua pendente; restrição de Docker não foi apresentada como erro financeiro inventado.
