# Spec Delta

## Purpose

Disponibilizar cotações reproduzíveis de BRL por USD com vigência explícita, histórico preservado e atualização que não comprometa a liquidação durante falhas externas.

## ADDED Requirements

### Requirement: Registro histórico de cotação
O sistema SHALL registrar cotação BRL por USD positiva, com até oito casas decimais e valor máximo 99999999.99999999, identificador, origem, instante de vigência e instante de registro em UTC. Uma correção SHALL criar nova cotação, preservando a anterior. O par e a vigência SHALL ser únicos.

#### Scenario: Atualização manual
- **WHEN** um administrador registra 5.4321 BRL por USD com vigência válida
- **THEN** a API responde 201 e a cotação fica consultável pelo identificador sem substituir o histórico

#### Scenario: Cotação inválida ou duplicada
- **WHEN** a cotação é zero, negativa, fora dos limites ou possui vigência já registrada para o par
- **THEN** a API rejeita dados inválidos com 400 ou duplicidade com 409 e preserva as cotações anteriores

### Requirement: Seleção temporal e validade
O sistema SHALL selecionar a cotação registrada e vigente mais recente até o instante de referência da operação. A cotação SHALL ser válida por no máximo 24 horas desde sua vigência. Cotações futuras SHALL ficar fora da seleção. A resposta SHALL expor valor, direção BRL por USD, origem, identificador e vigência.

#### Scenario: Cotação futura não altera operação presente
- **WHEN** existe uma cotação vigente e outra com vigência futura
- **THEN** a consulta para o instante atual retorna somente a vigente

#### Scenario: Cotação ausente ou antiga
- **WHEN** uma operação USD não encontra cotação vigente ou a última tem mais de 24 horas
- **THEN** a operação responde 503 com código FX_RATE_UNAVAILABLE e não cria liquidação

### Requirement: Sincronização externa limitada
O sistema SHALL permitir sincronizar cotação de um provedor mockado com até duas tentativas totais, timeout de 1 segundo por tentativa e intervalo de 100 ms. SHALL repetir somente timeout, falha de conexão ou resposta 5xx; respostas 4xx e payload inválido SHALL falhar sem retry. A sincronização SHALL retornar em até 3 segundos no ambiente local de referência.

#### Scenario: Recuperação de falha transitória
- **WHEN** o provedor falha na primeira tentativa e responde validamente na segunda
- **THEN** uma única cotação é registrada e a resposta informa sucesso

#### Scenario: Provedor persistentemente indisponível
- **WHEN** as duas tentativas falham
- **THEN** a sincronização responde 503, registra a falha de forma observável e preserva o histórico existente

### Requirement: Liquidação independente do provedor externo
O sistema SHALL usar uma cotação persistida e válida na liquidação, sem depender de chamada ao provedor durante a transação. A cotação efetivamente selecionada SHALL ser preservada no registro da liquidação, mesmo após atualizações futuras.

#### Scenario: Provedor cai com cotação local válida
- **WHEN** o provedor está indisponível e existe cotação local vigente dentro da validade
- **THEN** uma liquidação USD elegível pode concluir usando essa cotação, com valor e identificador auditáveis

#### Scenario: Pagamento em BRL durante falha de câmbio
- **WHEN** não há cotação válida e o recebível será pago em BRL
- **THEN** a indisponibilidade cambial não impede sua simulação ou liquidação
