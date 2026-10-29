# Review do endpoint de liquidação — Anexo A

**Parecer: bloquear a integração.** O trecho permite adulteração de comandos SQL, pagamento calculado com taxas erradas e confirmação duplicada ou parcial com resposta de sucesso. Essas falhas afetam diretamente o valor adquirido e a confiabilidade do histórico. A análise abaixo se refere ao código fornecido no Anexo A, não a uma alegação de incidente ocorrido neste repositório.

Prioridades: P0 impede uso do endpoint com dinheiro real; P1 compromete a operação ou a investigação de falhas. Middleware, schema e configuração não mostrados no anexo precisam ser inspecionados; sua ausência não pode ser inferida apenas desse arquivo.

## 1. P0 — Interpolação de entrada em SQL

**Trechos:** SELECT/UPDATE com `receivableId` e INSERT com `currency` interpolados.

**Impacto:** uma entrada pode mudar o sentido da consulta, atingir outros recebíveis ou alterar/expor dados com as permissões da conexão. Interpolar um UUID aparentemente válido ou escapar apenas a moeda não resolve todas as entradas.

**Correção:** parâmetros tipados em todas as consultas; UUID e enum validados na fronteira; nomes de tabelas/colunas/ordenação devem vir de fragmentos fixos, nunca do cliente. A conexão de negócio deve ter privilégios mínimos e não ser dona do histórico.

**Prevenção:** review de toda concatenação SQL, testes com entradas malformadas e integração executada com a credencial restrita. No projeto: [repositórios JDBC](backend/src/main/java/com/srm/credit/repository/) e [permissões reais](backend/src/test/java/com/srm/credit/integration/SettlementSchemaTest.java).

## 2. P0 — Taxas percentuais usadas como multiplicadores

**Trechos:** `BASE_RATE = 1.0`, spreads `1.5` e `2.5` na potência.

**Impacto:** 1% deveria ser `0.0100`, não `1.0`; 1,5% deveria ser `0.0150`. Em C1, o código usa `(1 + 1.0 + 1.5)^3 = 42.875`, resultando em aproximadamente BRL 2332.36 em vez de 92859.94. O cedente receberia 90527.58 a menos, antes de considerar os demais defeitos.

**Correção:** representar taxas como frações decimais, separar seleção de spread do motor e definir a unidade do prazo. Meses inteiros dos golden cases não autorizam interpretar um campo `term` desconhecido como dias ou fração mensal.

**Prevenção:** C1/C2/C3 obrigatórios ao centavo, teste de base/spread e política versionada. [Motor e strategies](backend/src/main/java/com/srm/credit/model/PricingEngine.java), [casos de aferição](backend/src/test/java/com/srm/credit/model/PricingEngineTest.java).

## 3. P0 — Falta de atomicidade entre liquidação e recebível

**Trechos:** INSERT e UPDATE independentes, sem transação explícita.

**Impacto:** INSERT confirmado seguido de UPDATE falho deixa uma liquidação registrada com título ainda disponível. Um retry ou outro operador pode adquirir o mesmo título novamente. A ordem inversa apenas muda o tipo de inconsistência.

**Correção:** uma transação engloba reserva idempotente, controle de estado/versão, auditoria e resultado da resposta. Qualquer falha, inclusive no commit, deve desfazer todas as gravações. Só responder sucesso depois do commit.

**Prevenção:** injetar falhas antes/depois do INSERT e em constraint diferida no commit. [Transação](backend/src/main/java/com/srm/credit/service/SettlementTransactionService.java) e [integrações de rollback](backend/src/test/java/com/srm/credit/integration/SettlementIntegrationTest.java).

## 4. P0 — Duplicidade por retry e por concorrência

**Trechos:** nenhuma chave idempotente, transição condicionada à versão ou garantia de unicidade aparece no endpoint.

**Impacto:** perda da resposta ou duplo clique produz outra liquidação; dois operadores podem ler o mesmo estado disponível e confirmar simultaneamente. Um `if` no estado lido antes do INSERT não elimina a corrida.

**Correção:** chave por ator estável/rota/intenção, hash do pedido normalizado e replay de status/corpo/Location; mesmo UUID com outro corpo deve conflitar. Manter UNIQUE(receivable_id) como defesa independente e UPDATE otimista de status/version. Estado, reserva e resultado devem compartilhar a transação. A reserva não pode ser confirmada sozinha.

**Prevenção:** duas conexões reais leem a mesma versão sob barreira; exigir um sucesso, um 409, um registro e um incremento. Testar mesma chave concorrente, rollback da primeira tentativa e resposta perdida após commit. Desabilitar um botão é proteção de interface, não garantia de banco. [Protocolo implementado](docs/settlements.md).

## 5. P0 — Exceção engolida e falso sucesso

**Trechos:** catch vazio e `res.status(200).json({ok:true,...})` incondicional.

**Impacto:** a mesa acredita que adquiriu o ativo mesmo quando não houve gravação ou apenas uma das gravações concluiu. Reconciliação, retry e investigação ficam comprometidos.

**Correção:** propagar a falha para tratamento global fora da transação abortada; separar conflitos 409, entrada 400, indisponibilidade 503 e falha inesperada 500. Criação confirmada deve retornar 201/Location. Reproduzir 201 no replay é parte do contrato, não uma nova criação.

**Prevenção:** testes de falha no banco e no commit verificam ausência de sucesso e registros parciais. Logs e contador de confirmações só após commit; replay não incrementa produção. Métricas em memória não substituem reconciliação SQL.

## 6. P0 — Autoridade da operação não demonstrada

**Trechos:** uso direto do body, sem autenticação/autorização ou ator auditável visíveis.

**Impacto:** se não houver proteção em middleware, qualquer cliente pode liquidar e escolher a moeda. Mesmo com autenticação, é necessário definir quais recebíveis o ator pode operar. O trecho isolado não comprova ausência desses controles em toda a aplicação.

**Correção:** verificar a cadeia de segurança; obter ator da sessão, autorizar papéis e escopo e proteger mutações contra CSRF quando houver cookie. Nunca aceitar ator do body. Conferir moeda com o cadastro e política adotada antes de calcular. Nesta demonstração, todos os operadores autorizados compartilham o mesmo fundo; não se presume isolamento por cedente.

**Prevenção:** testes 401/403, CSRF, identidade estável e divergência de moeda; credenciais fora do Git/navegador. [Sessão e limites](docs/authentication.md).

## 7. P0 — Ponto flutuante e arredondamento financeiro indefinido

**Trechos:** aritmética `Number`, `Math.pow` e `toFixed(2)`.

**Impacto:** `toFixed` formata uma aproximação binária; não garante half-even decimal, nem recupera centavos perdidos antes da formatação. Em USD, o código converte o VP não arredondado, divergindo da ordem exigida nos golden cases. NaN/Infinity e valores fora do limite podem chegar à persistência.

**Correção:** decimal construído de strings, precisão interna explícita e potência mensal inteira. Arredondar VP BRL por half-even; deságio = face − VP BRL; então dividir pela cotação e arredondar o pagamento USD. Persistir NUMERIC com escala/limites coerentes e validar antes da gravação. Transmitir strings decimais até o Angular.

**Prevenção:** empates 1.005/1.015, faces máximas, menor cotação positiva admitida, prazo 0 no motor e 1–36 na operação, além de C1–C3. [Precisão e limites](docs/pricing.md).

## 8. P1 — Cotação “latest” sem instante, direção ou validade

**Trechos:** `getLatestRate("USD")` sem identificação do registro utilizado.

**Impacto:** cotação futura ou expirada pode mudar o valor pago; interpretar USD por BRL como BRL por USD inverte a conversão. Uma nova consulta não reproduz o preço confirmado. O operador pode aceitar um valor e receber outro sem novo aceite.

**Correção:** escolher taxa persistida, vigente/registrada até o instante da operação, com validade explícita. Preservar UUID, direção, valor, origem e instantes. No protocolo adotado, o pedido inclui pagamento e cotação esperados; divergência retorna PRICE_CHANGED sem efeitos e exige novo aceite.

**Prevenção:** limites exatamente 24h/expirado, taxa futura, ausente, zero e duplicada; nova cotação após confirmação não altera histórico. [Seleção e sincronização](docs/currency.md).

## 9. P1 — Dependência remota dentro do caminho de confirmação

**Trechos:** chamada ao provedor em cada liquidação, sem timeout/retry demonstrados.

**Impacto:** lentidão ou queda externa paralisa USD e pode prolongar uma transação/uso de conexão quando o endpoint for parcialmente corrigido. Retry ilimitado amplia a falha; aceitar taxa antiga silenciosamente muda a semântica financeira.

**Correção:** sincronizar fora da transação, com prazo total limitado e retry apenas transitório. A liquidação usa cotação local válida; sem ela, USD retorna 503 e BRL continua. Retry do provedor e replay idempotente financeiro são mecanismos distintos.

**Prevenção:** provedor HTTP mockado com timeout, 5xx, 4xx e payload inválido; teste operacional com provedor parado e taxa local válida. Limites: duas tentativas totais de 1 segundo e intervalo de 100 ms.

## 10. P1 — Elegibilidade, inexistência e tipo desconhecido

**Trechos:** acesso direto a `receivable.type`, fallback de qualquer tipo para cheque e prazo não validado.

**Impacto:** título inexistente causa exceção; vencido, já liquidado ou com valor inválido pode ser precificado; um novo tipo recebe spread de cheque silenciosamente.

**Correção:** DTOs estritos, 404 para consulta inexistente, estado AVAILABLE, valor positivo e datas/prazo validados com Clock e fuso de negócio. Registrar strategies por tipo e rejeitar não suportados; constraints defendem os mesmos limites estáticos no banco.

**Prevenção:** enum desconhecido, face zero/negativa, prazo 0/1/30/31/1080/1081 dias, virada da data em São Paulo e optimistic locking. O relógio de negócio não deve depender do fuso da máquina.

## 11. P1 — Histórico insuficiente e imutabilidade não demonstrada

**Trechos:** INSERT registra apenas recebível, amount e currency.

**Impacto:** não permite explicar face, spread, prazo, deságio e câmbio da operação depois de uma mudança. Uma tabela chamada settlements não é imutável por convenção: privilégios de UPDATE/DELETE ou ORM podem reescrever a evidência.

**Correção:** snapshot completo, timestamps UTC, política, ator e correlação, com unicidade por título e restrições de precisão. Login de execução separado da migração; proibir reescrita/exclusão de auditoria e de resultado idempotente confirmado. O administrador do banco permanece uma fronteira de confiança.

**Prevenção:** testar com credencial real da aplicação, tentar alteração/exclusão e consultar após nova cotação/nome do cedente. [ER](docs/er.md), [papéis e limites](docs/database.md).

## 12. P1 — Falha sem diagnóstico ou contrato seguro

**Trechos:** nenhum erro correlacionado ou resultado operacional distinguível aparece no catch.

**Impacto:** suporte não distingue confirmação, replay, rollback ou falha de provedor; um contador por resposta 200 indicaria produção inexistente. Exceções anteriores ao try também precisam chegar ao handler global; o comportamento depende da integração async/framework usada, que o anexo não informa.

**Correção:** logs JSON com evento/resultado/correlação e identificadores auditáveis quando disponíveis, sem body, senhas, cookies ou SQL. Contador por moeda após commit e tempo do cálculo com labels limitados; status semântico e Problem Details seguro. Documentar o replay em OpenAPI.

**Prevenção:** testar falhas e replays, verificar logs com canários de segredo, manter testes/lint/build obrigatórios e publicar evidências de execução. [Observabilidade](docs/observability.md), [qualidade e CI](docs/quality.md).

## Ordem de correção e aceite

Antes de liberar o endpoint: parametrizar SQL e validar autoridade; corrigir unidades/precisão com C1–C3; fechar a transação e remover falso sucesso; implementar idempotência, controle otimista e unicidade; preservar snapshots e cotação temporal. Instrumentação e testes de falha acompanham cada correção, não uma promessa para depois da publicação.

O aceite exige PostgreSQL real, prova de rollback no commit, disputa determinística da mesma versão, replay exato após perda de resposta, credencial restrita e verificação operacional. Estas são as garantias do registro interno. Uma transferência bancária futura exigirá protocolo próprio de idempotência, conciliação e tratamento de resultados incertos; a unicidade local não garante execução única fora deste banco.
