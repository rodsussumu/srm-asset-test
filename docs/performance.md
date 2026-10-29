# Referência de aferição HTTP local — 29/10/2026

Resultado desta execução, sem garantia de capacidade de produção. São 10 clientes concorrentes, 200 requisições de aquecimento descartadas e 1000 medidas por rota. Nesta referência houve uma execução de medição; não foram selecionados os melhores resultados entre repetições. O cenário adicional com cotas e duas tentativas está documentado separadamente em [2 CPUs/4 GiB e 100 mil requisições](performance-constrained.md).

| Rota | Medidas | Erros | p50 ms | p95 ms | Máximo ms | Alvo p95 ms |
|---|---:|---:|---:|---:|---:|---:|
| Simulação | 1000 | 0 | 7.367 | 16.628 | 34.211 | 300 |
| Extrato | 1000 | 0 | 8.280 | 17.311 | 36.256 | 500 |

Todos os resultados medidos foram HTTP 200 com payload validado. O CSV contém as 2000 medidas, incluindo cliente, sequência, grupo, status e erro. O p95 usa nearest-rank (posição teto(0,95 × N)); uma auditoria independente do resumo conferiu contagens, 100 medidas por cliente/rota e os percentis.

| Grupo | Medidas | Erros | p95 ms |
|---|---:|---:|---:|
| simulation / BRL | 500 | 0 | 14.782 |
| simulation / USD | 500 | 0 | 18.456 |
| statement / all | 250 | 0 | 18.039 |
| statement / assignor | 250 | 0 | 18.105 |
| statement / combined | 250 | 0 | 16.393 |
| statement / currency | 250 | 0 | 16.409 |

## Ambiente e método

Host Apple M2, 8 CPUs físicas/lógicas e 24 GiB de memória. Docker disponibilizou 8 CPUs e 12591218688 bytes (aproximadamente 11,7 GiB), Linux aarch64. São recursos disponíveis/compartilhados, sem reserva exclusiva por contêiner. A API medida usa Temurin 21.0.12.1, Spring Boot 3.5.16 e PostgreSQL 17.11; nginx serve o bundle Angular 21.2.0. Metadados e hashes estão no [ambiente](evidence/benchmark-environment.json).

O projeto srm-new-task8-bench foi criado limpo, em volume distinto do smoke. Recebeu a massa determinística de 10000 recebíveis e 5000 liquidações (2500 por moeda), seguida de VACUUM ANALYZE. A cotação atual usada nas simulações foi sincronizada pela API administrativa; as cotações históricas do fixture servem apenas aos snapshots do extrato. [Inventário antes/depois](evidence/benchmark-inventory.txt).

A aplicação de smoke foi encerrada antes da medição. O driver Python usa HTTP pelo nginx em loopback, uma conexão persistente e sessão/CSRF independentes por cliente. Login fica fora da latência individual medida; phase_seconds inclui setup/login. Cada cliente aguarda sua resposta antes de enviar outra: carga closed-loop, sem pausas artificiais nem retry automático. Latência inclui serialização, HTTP/proxy/API/SQL quando aplicável, leitura/decodificação e validação do JSON no cliente.

Simulação alterna BRL/USD, DUPLICATA, face 100000.00 e vencimento de 90 dias. Verifica pagamento exato 92859.94/17094.67 e deságio 7140.06. O extrato usa página zero/tamanho 20 em quatro grupos: período janeiro–junho/2026 inteiro (5000 registros), cedente 1 (500), USD (2500) e cedente 1/USD no dia 01/01 (28). Espera 20 itens em cada página. O estado do banco permaneceu 10000/5000 após a aferição.

A massa SQL insere snapshots diretamente para medir consulta; não equivale a executar 5000 confirmações. O smoke, em outro volume, criou suas operações por HTTP. Não foi medido throughput de gravação/liquidação, latência de login, páginas altas, cold start, rede externa, TLS, saturação ou operação prolongada. O timer Micrometer do motor não foi usado como substituto do p95 HTTP.

As fases medidas duraram cerca de um segundo cada após aquecimento. Os alvos foram atendidos nesse recorte, com dados que cabem em memória e poucos filtros. Isso não estabelece SLA ou taxa sustentada em produção. Latência e distribuição devem ser medidas novamente para volumes/concorrência/topologias diferentes.

## Reproduzir em ambiente descartável

Copie .env.example para um arquivo local confiável, ajuste credenciais e quatro portas livres; exporte suas variáveis. O nome de projeto separa volume/rede, mas não evita conflito de portas. Exemplo com arquivo /tmp/srm-new-task8-bench.env e portas 55449/18089/14209/18099, mantendo base 0.0100 e mock 5.4321:

```sh
set -a
. /tmp/srm-new-task8-bench.env
set +a
docker compose -p srm-new-task8-bench --env-file /tmp/srm-new-task8-bench.env up --build -d --wait
docker compose -p srm-new-task8-bench --env-file /tmp/srm-new-task8-bench.env exec -T postgres sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d srm' < scripts/benchmark/seed-statements.sql
docker compose -p srm-new-task8-bench --env-file /tmp/srm-new-task8-bench.env exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d srm -c "VACUUM ANALYZE"'
# Entre como ADMIN no painel desse projeto e sincronize a cotação antes de medir.
python3 -u scripts/benchmark/http-load.py --base-url http://127.0.0.1:14209 --clients 10 --requests 1000 --warmup 200 --output /tmp/srm-http-results
docker compose -p srm-new-task8-bench --env-file /tmp/srm-new-task8-bench.env down --volumes
```

O script exige URL HTTP de loopback, 10 clientes, ao menos 1000 medidas/rota e aquecimento. APP_USERNAME/APP_PASSWORD são lidos do ambiente, sem impressão de credenciais. A pasta de saída é explícita; o exemplo usa /tmp para não sobrescrever a evidência versionada. Falha financeira, HTTP ou de alvo reprova o comando, preservando o relatório quando gerado. Nunca carregue o fixture ou use down --volumes em banco com histórico a preservar.

## Evidências

- [Resumo JSON](evidence/http-summary.json), [amostras CSV](evidence/http-latencies.csv) e [stdout da execução](evidence/http-run.txt).
- [Ambiente e hashes](evidence/benchmark-environment.json), [inventário](evidence/benchmark-inventory.txt) e [auditoria do CSV](evidence/performance-audit.txt).
- [Smoke integrado](smoke.md), [qualidade](quality.md) e [rastreabilidade](traceability.md).

O git_head registrado identifica a base de produção dc6c562. A branch de validação ainda não tem commit próprio nesta execução; adiciona driver, teste de extensão e documentos, sem alterar o código de produção medido. Execução remota do CI e defesa ao vivo não foram aferidas.
