# Carga com orçamento de 2 CPUs/4 GiB — 29/10/2026

Cenário adicional solicitado pelo autor: recursos para todo o conjunto e 100000 requisições medidas no total, 50000 por rota. Preserva a [referência anterior](performance.md), com mais recursos e 1000 medidas/rota. Não é uma medição de capacidade de produção ou escalabilidade horizontal.

## Orçamento aplicado

O perfil [compose.benchmark.yaml](../compose.benchmark.yaml) limita individualmente os quatro serviços; as cotas somam 2 CPUs e 4 GiB. Docker inspect confirmou os valores antes/depois da carga, com MemorySwap igual a Memory. Não foi alterada a configuração global do Docker nem o ambiente habitual.

| Serviço | CPU máxima | Memória máxima |
|---|---:|---:|
| backend | 1,25 | 2048 MiB |
| postgres | 0,50 | 1536 MiB |
| frontend/nginx | 0,15 | 256 MiB |
| fx-mock | 0,10 | 256 MiB |
| Total em execução | 2,00 | 4096 MiB |

O db-bootstrap tem 0,25 CPU/128 MiB e termina antes de iniciar backend/nginx; não participa da medição. Esse particionamento é mais rígido que uma VM de 2 CPUs/4 GiB: serviços não podem aproveitar livremente a cota ociosa de outro. Driver, daemon Docker e sistema operacional ficam fora do orçamento. [Ambiente/hashes](evidence/constrained-100k/environment.json).

## Duas tentativas preservadas

**Tentativa 1:** executada de 05:14:45Z a 17:10:55Z e reprovada (exit 1). Simulação completou 50000 sem erros; extrato teve 33335 falhas: 33195 de transporte/JSON e 140 HTTP 401. A coleta mostrou lacunas de aproximadamente 15 minutos, e pmset registrou Sleep/DarkWake no intervalo. Não houve OOM ou reinício de containers. A duração prolongada é compatível com expiração de sessão; não se atribui toda falha exclusivamente à suspensão nem às cotas. Essa tentativa não é comparação válida de desempenho contínuo. [Diagnóstico](evidence/constrained-100k/attempt-01-interrupted/diagnosis.json), [resumo](evidence/constrained-100k/attempt-01-interrupted/http-summary.json) e dados comprimidos preservados na mesma pasta.

**Tentativa 2:** banco recriado, mesmo fixture/cotas, estatísticas atualizadas e cotação sincronizada por API. Carga executada sob caffeinate -i no macOS, com inibição temporária de suspensão por inatividade; o processo encerra essa proteção ao terminar. O observador impõe 900 segundos ao driver e recusa sobrescrever uma pasta de resultados existente. Uma suspensão forçada, como fechar a tampa, não é impedida por essa opção.

A segunda tentativa ocorreu entre 17:20:54Z e 17:24:33Z (14:20–14:24 em São Paulo), com exit 0. Não houve ajuste de SQL, aplicação, limites ou alvos entre tentativas para escolher um resultado favorável; a repetição resolveu a interferência operacional identificada e ambas foram mantidas.

## Resultado da tentativa 2

Cada rota recebeu 1000 requisições de aquecimento não incluídas e 50000 medidas com 10 sessões/CSRF/conexões independentes. Mesma massa 10000/5000, mesmos quatro filtros, página zero/tamanho 20, BRL/USD e validação de payload da referência. Sem retry automático ou renovação automática de sessão durante a carga. A taxa observada usa o tempo da fase incluindo seu setup/login; a latência individual exclui login e inclui HTTP/proxy, processamento e validação JSON.

| Rota | Medidas | Erros | p95 ms | p99 ms | Fase s | Requisições/s observadas |
|---|---:|---:|---:|---:|---:|---:|
| Simulação | 50000 | 0 | 60.923 | 75.144 | 38.650 | 1293.66 |
| Extrato | 50000 | 0 | 94.195 | 99.515 | 164.719 | 303.55 |

Foram 100000 HTTP 200 sem erro de payload; 5000 medidas por cliente/rota. Percentis nearest-rank foram recalculados do CSV e conferidos contra o resumo. p95 ficou abaixo dos alvos 300/500 ms. Os máximos foram 175,739 ms na simulação e 392,514 ms no extrato. Banco permaneceu 10000 títulos/5000 liquidações. [Auditoria](evidence/constrained-100k/attempt-02/audit.json), [resumo](evidence/constrained-100k/attempt-02/http-summary.json), [inventário](evidence/constrained-100k/attempt-02/inventory.txt) e [amostras gzip](evidence/constrained-100k/attempt-02/http-latencies.csv.gz).

Comparação descritiva: p95 da referência foi 16,628/17,311 ms e o cenário restrito resultou em 60,923/94,195 ms. Recursos, duração e aquecimento mudaram juntos; não se isola o efeito causal de cada mudança. A carga é closed-loop com dez clientes, não uma taxa de chegada aberta nem teste de saturação. Não há carga de confirmação/escrita ou de transferências financeiras.

## CPU, memória e limites da observação

Os contadores cgroup v2 foram coletados para todos os quatro serviços antes/depois. A tabela usa deltas de CPU no intervalo da coleta (incluindo aquecimento/overhead) e memory.peak de toda a vida do container, incluindo startup. Picos individuais não são um pico agregado simultâneo.

| Serviço | Pico individual MiB | CPU em segundos-core | Períodos com throttling | OOM kills | Reinícios |
|---|---:|---:|---:|---:|---:|
| backend | 504.69 | 101.506 | 18.59% | 0 | 0 |
| postgres | 181.73 | 86.694 | 78.57% | 0 | 0 |
| frontend | 16.32 | 14.883 | 10.66% | 0 | 0 |
| fx-mock | 32.60 | 7.766 | 88.84% | 0 | 0 |

O percentual é a fração dos períodos reportados pelo controlador que sofreram throttling; não é percentual do tempo total nem uso médio de CPU. A amostra pontual mostrou PostgreSQL em torno de sua cota de 0,5 CPU. Seus 78,57% de períodos com throttling indicam pressão dessa cota, mas não substituem um ensaio isolando a distribuição das CPUs. O mock também sofre throttling nos healthchecks; a precificação não o chama durante o cálculo. Não houve eventos OOM/OOM kill, reinícios ou pressão de memória até os limites individuais neste fixture.

**Limitação real do coletor:** o parser original descartava a primeira linha do docker stats por conter controles ANSI; as amostras contínuas omitem backend. As cotas e os contadores cgroup antes/depois continuam cobrindo os quatro serviços, e houve uma conferência pontual de todos. Não se apresenta uma curva completa de CPU/memória do backend. O parser foi corrigido, passou a registrar erros/cobertura e foi verificado por replay offline de quatro payloads reais com prefixos ANSI. Não foi repetida a carga para ocultar essa limitação. [Proveniência](evidence/constrained-100k/attempt-02/observer-provenance.json), [check do parser](evidence/constrained-100k/attempt-02/parser-check.json), [contadores/cotas](evidence/constrained-100k/attempt-02/resources.json) e [amostras disponíveis](evidence/constrained-100k/attempt-02/resource-samples.ndjson.gz).

## Reproduzir

Use um projeto descartável, arquivo .env local confiável e portas livres. O exemplo utilizado foi /tmp/srm-constrained.env com PostgreSQL 55450, API 18090, nginx 14210 e mock 18100, base 0.0100/mock 5.4321. Não apague volumes de ambientes com histórico a preservar.

```sh
set -a
. /tmp/srm-constrained.env
set +a
docker compose -p srm-constrained -f compose.yaml -f compose.benchmark.yaml --env-file /tmp/srm-constrained.env up --build -d --wait
docker compose -p srm-constrained -f compose.yaml -f compose.benchmark.yaml --env-file /tmp/srm-constrained.env exec -T postgres sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d srm' < scripts/benchmark/seed-statements.sql
docker compose -p srm-constrained -f compose.yaml -f compose.benchmark.yaml --env-file /tmp/srm-constrained.env exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d srm -c "VACUUM ANALYZE"'
# Entre como ADMIN e sincronize o mock pelo painel antes da medição.
# No macOS, prefixe o comando abaixo com caffeinate -i enquanto a máquina estiver aberta.
python3 -u scripts/benchmark/observe-load.py --project srm-constrained --env-file /tmp/srm-constrained.env --base-url http://127.0.0.1:14210 --output /tmp/srm-constrained-results --timeout 900
docker compose -p srm-constrained -f compose.yaml -f compose.benchmark.yaml --env-file /tmp/srm-constrained.env down --volumes
```

O observador apenas mede serviços já iniciados, não cria/resetta banco nem sincroniza câmbio. Exige soma das cotas 2 CPUs/4 GiB e contêineres em execução; mantém segredos herdados somente no ambiente do driver. Em timeout global, retorna erro e preserva metadados; o driver pode não ter finalizado o CSV. Arquivos brutos grandes ficam locais/ignorados, e os gzip selecionados são verificáveis por SHA-256. O ambiente descartável foi removido após as tentativas; o ambiente habitual foi preservado.
