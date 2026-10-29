# SRM Credit Engine — especificação

**Estado:** premissas definidas antes do código; blocos 2 a 6 implementados, incluindo liquidação, auditoria, extrato, sessão e painel Angular; operação completa e validação integrada final pendentes. Stack: Java/Spring Boot, Angular e PostgreSQL. Cenários detalhados nas [seis specs](openspec/changes/build-srm-credit-engine/specs/).

## Escopo e premissas

Cadastrar cedentes e recebíveis em BRL, simular o valor líquido e registrar uma aquisição integral por título, com pagamento em BRL ou USD. Lotes serão sequências de operações individuais; importação de arquivos e atomicidade do lote ficam fora do escopo. Liquidação é registro interno, sem transferência bancária.

Backend MVC: controller → service → repository; model concentra regras e strategies; DTOs representam o contrato HTTP. Pacotes serão criados junto das funcionalidades. Angular separará apresentação, API e estado local. PostgreSQL será a autoridade financeira.

| Tema | Premissa |
|---|---|
| Taxas | Base mensal configurada em 1%; DUPLICATA: spread 1,5%; CHEQUE: 2,5%. Tipo desconhecido será rejeitado. |
| Prazo | Teto(dias corridos até vencimento / 30), entre 1 e 36 meses. Vencimento hoje, passado ou acima de 1080 dias será rejeitado; 31 dias equivalem a 2 meses. |
| Relógio | Clock injetável; data da operação em America/Sao_Paulo e timestamps em UTC. |
| Fórmula | VP = face / (1 + base + spread)^meses, com taxas em fração decimal. |
| Câmbio | BRL por USD, positivo, registrado e vigente até a operação; escolher a vigência mais recente, válida por até 24 horas. Ignorar vigências futuras. |
| Conversão | Arredondar VP BRL, dividir pela cotação e arredondar pagamento USD; deságio = face − VP BRL. |
| Confirmação | Simulação indicativa; recalcular na confirmação. Mudança do pagamento ou da cotação exigirá nova aceitação. |

## Precisão e aferição

BigDecimal construído de strings, precisão interna de 50 dígitos e potência mensal inteira. Não usar float/double para dinheiro ou taxas. Arredondar por half-even a duas casas apenas no VP BRL e no pagamento após conversão. O motor puro aceitará prazo zero para testes; a operação exigirá elegibilidade.

Banco: face/VP/deságio NUMERIC(18,2), pagamento NUMERIC(30,2), câmbio NUMERIC(16,8), taxas NUMERIC(10,8). Rejeitar excesso de escala antes de gravar. Face máxima 9999999999999999.99; cotação entre 0.00000001 e 99999999.99999999. JSON e Angular preservarão decimais como strings.

Golden cases obrigatórios, com base de 1% a.m. e prazo inteiro:

| Caso | Tipo / face BRL / meses | Cotação | VP BRL | Pagamento | Deságio BRL |
|---|---|---:|---:|---|---:|
| C1 | DUPLICATA / 100000.00 / 3 | — | 92859.94 | BRL 92859.94 | 7140.06 |
| C2 | CHEQUE / 25000.00 / 2 | — | 23337.77 | BRL 23337.77 | 1662.23 |
| C3 | DUPLICATA / 100000.00 / 3 | 5.4321 | 92859.94 | USD 17094.67 | 7140.06 |

## Integridade e critérios de aceite

- ACID: liquidação, estado/versão e resultado idempotente na mesma transação; responder sucesso somente após commit. Falhas desfazem todas as gravações.
- Idempotência: UUID por ator/rota e hash normalizado; replay devolve status/corpo/Location originais. Outro conteúdo com a mesma chave gera 409. Sucessos não expiram nesta entrega.
- Concorrência: optimistic locking e unicidade por recebível; teste determinístico com duas leituras da mesma versão comprovará um sucesso, um conflito e uma liquidação.
- Auditoria: snapshots de valores, taxas, cotação, prazo, política, ator e timestamps; credencial de execução sem UPDATE/DELETE do histórico, separada da migração.
- Resiliência: sincronização separada da liquidação, duas tentativas totais de 1 segundo e intervalo de 100 ms, somente para falhas transitórias. Cotação local válida permite USD com provedor fora; sem ela, USD retorna 503 e BRL continua.
- Segurança/API: sessão HttpOnly, CSRF, OPERATOR/ADMIN, credenciais externas, SQL parametrizado, erros seguros e OpenAPI. Câmbio alterável apenas por ADMIN.
- Interface: português, teclado e validação por campo; simulação após 300 ms, cancelamento de respostas antigas e proteção contra duplo clique/retry.
- Extrato: filtros período/cedente/moeda, intervalo UTC [início, fim) de até 366 dias; paginação padrão 20/máximo 100; contagem e totais por moeda no mesmo snapshot da página.
- Operação: Compose, ER/C4 1 e 2, logs JSON correlacionados e métricas de confirmações pós-commit/latência. CI com testes, lint e build, incluindo PostgreSQL real e Angular.
- Cobertura: backend 80% linhas/70% branches; cada classe financeira e strategy 95%/90%, bloqueados por JaCoCo.
- Desempenho: aferir p95 ≤300 ms na simulação e ≤500 ms no extrato com 10000 títulos/5000 liquidações, 10 clientes e ≥1000 medidas por rota após aquecimento; ambiente com ≥4 vCPUs/8 GB. Registrar resultados reais, sem garantia de produção.

## Perguntas e cortes

Confirmar com o negócio: prazo/feriados/vencidos, responsável pela base, validade cambial em fins de semana, garantia do preço, atomicidade do lote, estornos, caixa e autorização por cedente. Essas premissas ainda não têm aprovação comercial.

Fora do escopo: pagamentos bancários, gestão de saldo, liquidação parcial, importação, microserviços, estudo de um milhão de transações/minuto, EDA e post-mortem do Anexo B. [Decisões e limites](DECISIONS.md).
