# Testes, cobertura e CI — bloco 2

Validação local em 28/10/2026, com Java 21 e Docker. `./mvnw -B -ntp clean verify` passou com **74 testes**, zero falhas, erros ou skips:

| Suíte | Casos | Verifica |
|---|---:|---|
| PricingEngineTest | 37 | C1/C2/C3, half-even, conversão do VP BRL arredondado, limites, base configurável, moeda/cotação obrigatórias e strategies sem fallback |
| MonthlyTermPolicyTest | 9 | Dias 0/1/30/31/1080/1081, passado, ausência de vencimento e virada de data em São Paulo |
| ReceivablesIntegrationTest | 28 | PostgreSQL real, migration limpa, NUMERIC/FK/CHECK, versão antiga, cadastro/consulta/paginação, validação, CSRF/401, erros seguros e OpenAPI |

A suíte de integração usa PostgreSQL 17.11-alpine descartável via Testcontainers; não usa H2, não ignora falha de Docker e não modifica o volume Compose. A primeira execução no sandbox não conseguiu acessar o Docker e falhou; fora do sandbox, a suíte completa passou. Não foi alterado teste para contornar essa restrição.

## Cobertura

JaCoCo 0.8.16 emite HTML/XML em target/site/jacoco e bloqueia o verify. Não há exclusão de código de produção.

| Escopo | Linhas mínimas | Branches mínimos |
|---|---:|---:|
| Backend completo | 80% | 70% |
| Cada classe PricingEngine, FinancialAmounts, MonthlyTermPolicy e cada strategy | 95% | 90% |

Resultado geral extraído do XML: **299/308 linhas (97,08%)** e **71/86 branches (82,56%)**. Cada regra financeira foi aprovada:

| Classe | Linhas | Branches |
|---|---|---|
| ChequeStrategy | 3/3 (100.00%) | sem branches |
| DuplicataStrategy | 3/3 (100.00%) | sem branches |
| FinancialAmounts | 18/18 (100.00%) | 20/20 (100.00%) |
| MonthlyTermPolicy | 11/11 (100.00%) | 6/6 (100.00%) |
| PricingEngine | 32/32 (100.00%) | 24/24 (100.00%) |

Classes sem branches não precisam de testes artificiais para criar desvios. Os testes adicionais do motor verificam configurações inválidas, ausência de moeda/cotação e preservação do spread com base zero; não alteram a fórmula.

## Comprovação do bloqueio

Executado sobre a mesma medição, sem modificar pom.xml ou código:

```sh
./mvnw -B -ntp -Dcoverage.lines.minimum=1.0 -Dcoverage.branches.minimum=1.0 jacoco:check@coverage-check
```

O comando retornou exit 1 com Rule violated, como esperado. Em seguida, `./mvnw -B -ntp jacoco:check@coverage-check` passou com as regras persistidas. O teste negativo não conta como falha não resolvida da suíte; comprova que o build reprova cobertura insuficiente.

## Pipeline

O workflow executa clean verify no backend e npm ci/lint/build no frontend, em PRs e pushes na main, com timeout de 15 minutos por job. O runner Ubuntu fornece Docker para o Testcontainers. Surefire e JaCoCo são publicados por 14 dias, inclusive após falhas quando os arquivos existem; uma etapa posterior de publicação não remove a falha obrigatória.

A CI do frontend ainda não tem testes funcionais porque o painel permanece no esqueleto; eles acompanharão o bloco 6. Concorrência de liquidação, rollback e idempotência entram no mesmo verify quando implementados no bloco 4. Esta evidência é local; a execução remota desta branch ainda precisa ocorrer na PR.

## Reproduzir

Com Java 21 e Docker ativos, no backend:

```sh
./mvnw -B -ntp clean verify
./mvnw -Dtest=PricingEngineTest,MonthlyTermPolicyTest test
./mvnw -Dtest=ReceivablesIntegrationTest test
```

Relatórios gerados ficam ignorados no Git. Evite interpretar o teste isolado de entidade com versão antiga como prova da disputa entre duas liquidações: esse cenário pertence ao bloco 4. Não houve aferição HTTP, smoke completo ou testes do painel nesta etapa.
