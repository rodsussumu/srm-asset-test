# Spec Delta

## Purpose

Garantir que cada recebível gere no máximo uma liquidação confirmada e que toda confirmação seja transacional, reproduzível e segura diante de retries e concorrência.

## ADDED Requirements

### Requirement: Confirmação com recálculo
O sistema SHALL recalcular a liquidação no servidor usando o recebível persistido e o instante da operação. O pedido SHALL incluir moeda cadastrada, valor esperado e, para USD, identificador esperado da cotação. Divergência de moeda SHALL ser rejeitada com 400; mudança de valor ou cotação SHALL exigir nova simulação por resposta 409 PRICE_CHANGED.

#### Scenario: Valor confirmado corresponde à simulação
- **WHEN** o recebível está disponível e o valor esperado e a cotação correspondem ao recálculo
- **THEN** o sistema pode confirmar a liquidação com os valores calculados no servidor

#### Scenario: Cotação mudou após simulação
- **WHEN** a última cotação válida tem identificador diferente daquele confirmado pelo operador
- **THEN** o sistema responde 409 PRICE_CHANGED e não liquida o recebível

### Requirement: Persistência atômica
O sistema SHALL confirmar a liquidação, a transição AVAILABLE para SETTLED, o incremento de versão e o resultado idempotente em uma única transação. Qualquer falha antes do commit SHALL desfazer todas essas mudanças. O sucesso SHALL ser informado somente depois do commit.

#### Scenario: Falha entre gravações
- **WHEN** ocorre uma falha após inserir a liquidação e antes do commit da transação
- **THEN** nenhuma liquidação ou resultado idempotente confirmado permanece e o recebível continua AVAILABLE

#### Scenario: Resposta perdida após commit
- **WHEN** a transação conclui e a conexão cai antes de o cliente receber a resposta
- **THEN** o retry com a mesma chave recupera a confirmação original

### Requirement: Idempotência por intenção de operação
O endpoint SHALL exigir Idempotency-Key UUID, escopada por operador e rota, e vincular a chave ao hash do pedido normalizado. Repetição de sucesso SHALL retornar o mesmo status 201, corpo e Location. Mesma chave com conteúdo diferente SHALL retornar 409 IDEMPOTENCY_KEY_REUSED. Registros confirmados SHALL ser mantidos sem expiração nesta entrega.

#### Scenario: Retry sequencial
- **WHEN** o mesmo operador repete a mesma requisição confirmada com a mesma chave
- **THEN** recebe a mesma liquidação sem recálculo ou novas gravações, mesmo que o câmbio tenha mudado

#### Scenario: Chave reutilizada para outro pedido
- **WHEN** o operador usa uma chave confirmada com outro recebível, moeda, valor esperado ou cotação
- **THEN** recebe 409 IDEMPOTENCY_KEY_REUSED sem efeitos financeiros

#### Scenario: Dois retries simultâneos com a mesma chave
- **WHEN** duas requisições equivalentes com a mesma chave chegam simultaneamente
- **THEN** somente uma liquidação é criada e ambas recuperam o mesmo sucesso; se a espera ultrapassar 3 segundos, a segunda recebe 409 REQUEST_IN_PROGRESS com Retry-After e pode repetir

### Requirement: Uma liquidação por recebível sob concorrência
O sistema SHALL impedir mais de uma liquidação por recebível, inclusive com chaves ou operadores diferentes. A disputa SHALL ser detectada por controle otimista de versão e protegida por unicidade no banco. A operação perdedora SHALL retornar 409 RECEIVABLE_ALREADY_SETTLED sem liquidação parcial.

#### Scenario: Duas intenções concorrentes
- **WHEN** duas transações leem a mesma versão AVAILABLE e tentam liquidar com chaves diferentes
- **THEN** exatamente uma confirma, a outra recebe conflito, e há uma única liquidação e um único incremento de versão

#### Scenario: Nova chave para título já liquidado
- **WHEN** chega uma nova intenção de liquidar um recebível SETTLED
- **THEN** o sistema responde 409 RECEIVABLE_ALREADY_SETTLED sem criar outra liquidação

### Requirement: Auditoria financeira imutável
O registro SHALL preservar recebível, cedente, operador, face, vencimento, tipo, prazo, data da operação, base, spread, VP e deságio em BRL, moeda e valor pago, cotação/identificador/vigência quando USD, versão da política, chave de correlação e instante de liquidação UTC. O sistema SHALL impedir alteração ou exclusão de liquidações pelo usuário de execução do banco.

#### Scenario: Reprodução histórica
- **WHEN** uma liquidação é consultada após nova cotação ou alteração de parâmetros para operações futuras
- **THEN** seus valores e parâmetros originais continuam disponíveis e inalterados

#### Scenario: Tentativa de alterar auditoria
- **WHEN** a aplicação ou seu usuário de banco tenta atualizar ou excluir uma liquidação
- **THEN** a operação é recusada e o registro original permanece intacto

### Requirement: Erros REST explícitos
A API SHALL usar 201 para criação confirmada, 400 para entrada inválida, 401/403 para acesso inválido, 404 para recurso inexistente, 409 para conflitos, 503 para dependência indisponível e 500 para falha inesperada. Erros SHALL conter código estável, detalhe seguro e correlationId, sem SQL, credenciais ou stack trace.

#### Scenario: Falha inesperada de persistência
- **WHEN** uma falha inesperada impede o commit
- **THEN** a resposta é 500 com identificador de correlação, sem corpo de sucesso ou exposição de detalhes internos
