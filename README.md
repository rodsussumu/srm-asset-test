# SRM Credit Engine

Plataforma de cessão de crédito com backend Java/Spring Boot, frontend Angular e PostgreSQL. Recebíveis em BRL poderão ser precificados e adquiridos com pagamento em BRL ou USD.

## Estado

Estrutura inicial disponível: backend Spring Boot com health, frontend Angular com página inicial, PostgreSQL de desenvolvimento via Compose e CI de formatação/lint/build. O backend ainda não acessa o banco. Cadastro, cálculo, câmbio, liquidação e autenticação serão implementados nos próximos blocos; ainda não existem testes de negócio.

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

Exemplos de branches: docs/project-specifications, chore/initial-structure e feat/receivable-pricing. Cada PR deve explicar o problema, a decisão e a validação real, conforme o [template](.github/pull_request_template.md). A CI valida pushes na main e pull requests, com jobs separados para backend e frontend. As suítes funcionais serão incluídas com as features; execução remota ainda não foi comprovada.

## Executar localmente

Use Java 21 e Node 22.16.0, conforme [.java-version](.java-version), [.nvmrc](.nvmrc) e [toolchain](docs/versions.md). Com nvm instalado, `nvm install` e `nvm use` na raiz selecionam o Node. Confirme `java -version` e ajuste JAVA_HOME para Java 21.

Backend, em um terminal:

```sh
cd backend
./mvnw spring-boot:run
```

Consulte [health](http://localhost:8080/actuator/health), que deve retornar `{"status":"UP"}`. A aplicação não exige banco nesta etapa. Nenhuma rota de negócio está disponível.

Frontend, em outro terminal:

```sh
cd frontend
npm ci
npm start
```

Abra [localhost:4200](http://localhost:4200). A página informa o estado inicial do projeto. O proxy de desenvolvimento encaminha /api para localhost:8080; esses endpoints serão implementados nos próximos blocos. Para portas ocupadas, use `SERVER_PORT=8081 ./mvnw spring-boot:run` e `npm start -- --port 4201`; ajuste o proxy quando a API for integrada.

PostgreSQL de desenvolvimento, opcional enquanto não há persistência:

```sh
cp .env.example .env
# Ajuste a senha e POSTGRES_PORT antes de iniciar.
docker compose up -d --wait postgres
docker compose down
```

O Compose contém somente PostgreSQL nesta etapa, com healthcheck e volume persistente. `down` preserva os dados. Não apague um volume para solucionar migrações futuras. Credenciais de migração/execução e orquestração completa serão adicionadas nos blocos previstos.

## Verificações e CI

No backend:

```sh
./mvnw -B -ntp clean verify
# Para corrigir formatação:
./mvnw spotless:apply
```

No frontend:

```sh
npm ci
npm run lint
npm run build
```

O verify inclui Spotless check e empacotamento do JAR executável. Ainda não existem testes Java; Maven informa `No tests to run`. ESLint e Prettier verificam o frontend, e Angular compila em modo estrito. O script npm test está preparado para os testes que acompanharão as funcionalidades; não faz parte desta CI inicial porque ainda não há casos.

O [workflow](.github/workflows/ci.yml) executa esses comandos, sem ignorar falhas. Testes financeiros, integração PostgreSQL, cobertura mínima e artefatos serão acrescentados nos blocos correspondentes. A [validação local desta etapa](docs/initial-validation.md) não comprova execução no GitHub Actions.

## Planejamento OpenSpec

Com o OpenSpec CLI instalado, executar na raiz:

```sh
openspec status --change build-srm-credit-engine
openspec instructions apply --change build-srm-credit-engine --json
openspec validate build-srm-credit-engine --strict
```

As seis specs definem os contratos: [precificação](openspec/changes/build-srm-credit-engine/specs/receivable-pricing/spec.md), [câmbio](openspec/changes/build-srm-credit-engine/specs/currency-rates/spec.md), [liquidação](openspec/changes/build-srm-credit-engine/specs/settlement-integrity/spec.md), [extrato](openspec/changes/build-srm-credit-engine/specs/settlement-statements/spec.md), [painel](openspec/changes/build-srm-credit-engine/specs/operator-workspace/spec.md) e [operação](openspec/changes/build-srm-credit-engine/specs/operational-readiness/spec.md).
