# Spec Delta

## Purpose

Definir entradas elegíveis e resultados financeiros reproduzíveis para recebíveis em BRL com pagamento em BRL ou USD, incluindo os casos de aferição do desafio.

## ADDED Requirements

### Requirement: Cadastro de cedentes e recebíveis
O sistema SHALL cadastrar cedentes e recebíveis com identificador, cedente, valor de face em BRL, vencimento, tipo, moeda de pagamento, estado e versão. Tipos aceitos SHALL ser DUPLICATA e CHEQUE; moedas de pagamento SHALL ser BRL e USD. Novos recebíveis SHALL iniciar disponíveis para liquidação.

#### Scenario: Cadastro válido
- **WHEN** o operador cadastra um recebível com cedente existente e campos válidos
- **THEN** a API responde 201 com identificador, estado AVAILABLE e versão inicial

#### Scenario: Dados financeiros inválidos
- **WHEN** o valor não é uma string decimal positiva de até duas casas e no máximo 9999999999999999.99, a moeda ou tipo é desconhecido, ou o cedente não existe
- **THEN** a API rejeita o pedido com erro de validação identificando o campo e não grava o recebível

### Requirement: Prazo mensal explícito
O sistema SHALL usar a data da operação em America/Sao_Paulo e calcular prazo como teto(dias corridos até vencimento / 30), entre 1 e 36 meses. Vencimentos no dia da operação ou no passado SHALL ser rejeitados. O cálculo isolado SHALL aceitar prazo inteiro zero para aferição de borda, sem tornar títulos vencidos elegíveis.

#### Scenario: Período parcial
- **WHEN** o vencimento está 31 dias após a data da operação
- **THEN** a simulação usa 2 meses e informa essa convenção

#### Scenario: Título não elegível
- **WHEN** o vencimento está no passado, no dia da operação ou a mais de 1080 dias
- **THEN** a simulação e a liquidação são rejeitadas sem alterar o estado do recebível

### Requirement: Precificação decimal por tipo
O sistema SHALL calcular VP = face / (1 + base + spread)^meses com base mensal 0.0100, spread DUPLICATA 0.0150 e CHEQUE 0.0250. Valores, taxas e potências SHALL usar aritmética decimal, sem float/double, com precisão interna mínima de 34 dígitos significativos. O deságio SHALL ser face menos VP arredondado em BRL.

#### Scenario: Golden case C1
- **WHEN** face é 100000.00, tipo DUPLICATA, prazo 3 e pagamento BRL
- **THEN** VP é 92859.94 BRL e deságio é 7140.06 BRL

#### Scenario: Golden case C2
- **WHEN** face é 25000.00, tipo CHEQUE, prazo 2 e pagamento BRL
- **THEN** VP é 23337.77 BRL e deságio é 1662.23 BRL

#### Scenario: Prazo zero no motor isolado
- **WHEN** o cálculo isolado recebe face 100.00 e prazo zero
- **THEN** o VP é 100.00 e o deságio é 0.00

### Requirement: Arredondamento e conversão ao final
O sistema SHALL arredondar VP em BRL para duas casas por half-even apenas após o desconto composto. Para USD SHALL dividir esse VP arredondado pela cotação BRL por USD e arredondar o pagamento para duas casas por half-even. A API SHALL transmitir valores e taxas como strings decimais, identificando a moeda de cada valor.

#### Scenario: Golden case C3
- **WHEN** face é 100000.00, tipo DUPLICATA, prazo 3, pagamento USD e câmbio 5.4321 BRL por USD
- **THEN** VP é 92859.94 BRL, pagamento é 17094.67 USD e deságio permanece 7140.06 BRL

#### Scenario: Empates de arredondamento
- **WHEN** a política monetária arredonda os valores decimais exatos 1.005 e 1.015 para duas casas
- **THEN** os resultados são respectivamente 1.00 e 1.02

### Requirement: Simulação indicativa sem efeitos de liquidação
O sistema SHALL simular entradas válidas sem exigir recebível persistido e sem criar liquidação. A resposta SHALL informar VP em BRL, deságio em BRL, pagamento, moeda, prazo, base, spread, data da operação, versão da política e cotação efetiva quando houver conversão. A simulação SHALL ser identificada como indicativa.

#### Scenario: Simulação de pagamento em BRL
- **WHEN** uma simulação válida em BRL é solicitada
- **THEN** o resultado independe de cotações e não altera recebíveis nem cria registros de liquidação

#### Scenario: Explicabilidade da simulação
- **WHEN** o operador recebe uma simulação em USD
- **THEN** consegue identificar cotação, vigência e todos os parâmetros necessários para reproduzir o resultado
