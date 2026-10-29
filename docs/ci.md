# CI: configuração e evidência remota

## Execução verificada

Em 29/10/2026, a [execução 40931500284 da PR #10](https://github.com/rodsussumu/srm-asset-test/actions/runs/40931500284), commit `e931af4`, terminou com **Success**:

| Job | Resultado | Duração |
|---|---|---|
| backend | Aprovado | 1 min 31 s |
| frontend | Aprovado | 50 s |

O resumo do GitHub confirmou três artefatos publicados: `backend-test-results`, `backend-coverage` e `frontend-test-results`. A disponibilidade foi conferida na interface; o conteúdo dos downloads não foi inspecionado. O resultado comprova o workflow daquele commit. A revisão de Actions descrita abaixo ainda precisa ser executada no GitHub.

## Checks obrigatórios

O [workflow](../.github/workflows/ci.yml) roda em pull requests e pushes na `main`, com permissões `contents: read`, timeout de 15 minutos por job e cancelamento de execuções anteriores do mesmo grupo.

- Backend: `./mvnw -B -ntp clean verify`, incluindo formatação, testes unitários, integração PostgreSQL/Testcontainers, build e gates JaCoCo.
- Frontend: `npm ci`, lint, testes unitários Angular/Vitest sem watch e build. O comando `npm test -- --reporters=default --reporters=junit --output-file=test-results.xml` já fazia parte da pipeline; a etapa agora se chama **Run Angular unit tests (Vitest)**.
- Relatórios: Surefire, planos SQL, JaCoCo e JUnit Angular publicados por 14 dias, inclusive após falhas quando disponíveis. Upload de relatório não transforma falha em sucesso.

## Tratamento dos avisos

O runner está fixado em `ubuntu-24.04`, evitando a migração implícita do alias `ubuntu-latest`. As Actions passaram a versões cujo manifesto declara runtime Node 24:

| Action | Versão | Manifesto oficial |
|---|---|---|
| checkout | v5 | [action.yml](https://github.com/actions/checkout/blob/v5/action.yml) |
| setup-java | v5 | [action.yml](https://github.com/actions/setup-java/blob/v5/action.yml) |
| setup-node | v5 | [action.yml](https://github.com/actions/setup-node/blob/v5/action.yml) |
| upload-artifact | v6 | [action.yml](https://github.com/actions/upload-artifact/blob/v6/action.yml) |

Essas versões tratam os avisos de runtime Node 20 e setup-java v4 mostrados na execução anterior. O runtime interno das Actions é distinto das versões da aplicação: Java 21 vem de `.java-version` e Node 22.16.0 de `.nvmrc`. A ausência dos avisos na nova revisão só poderá ser confirmada após execução remota.

## Verificação desta revisão

O comando de testes frontend usado no workflow foi repetido localmente: **48 testes em seis arquivos aprovados**, com relatório JUnit gerado. YAML do workflow, links locais alterados e `git diff --check` também foram verificados. Não houve mudança de código Java nem repetição da suíte backend nesta revisão de CI/documentação.

## Limites da entrega

A CI não realiza deploy, aceite comercial, convite aos avaliadores ou defesa ao vivo. Smoke e benchmark possuem evidências locais próprias. Os relatos históricos em `AI_USAGE.md` e no documento de qualidade registram o estado de cada etapa; o resumo acima consolida a evidência remota disponível.
