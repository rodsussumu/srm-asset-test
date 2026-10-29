# Validação da estrutura inicial

Executada localmente em 28/10/2026, com Java 21.0.8 e Node 22.16.0, na branch chore/initial-structure.

| Verificação | Resultado |
|---|---|
| backend: ./mvnw -B -ntp clean verify | BUILD SUCCESS; JAR executável; Spotless aprovado; sem testes nesta etapa |
| frontend: npm ci --no-audit --no-fund | Instalação de 546 pacotes usando lockfile |
| frontend: npm run lint | ESLint e Prettier aprovados |
| frontend: npm run build | Bundle de produção gerado; tamanho inicial 187,63 kB |
| API: JAR com SERVER_PORT=18081 | Inicialização concluída; GET /actuator/health retornou 200 com status UP |
| docker compose --env-file .env.example config --quiet | Configuração válida para PostgreSQL 17.11-alpine |

O backend e o build Angular precisaram executar fora do sandbox para abrir porta/terminar o build. Não foram removidas verificações ou alteradas regras para contornar a restrição.

Esta validação confirma a compilação e inicialização do esqueleto. Não demonstra persistência, fluxo de negócio, golden cases, cobertura, smoke integrado, desempenho ou execução remota da CI. O Compose foi validado como configuração; não houve carga de dados nem teste de integração com banco nesta etapa. As evidências dessas funcionalidades acompanharão seus respectivos blocos.
