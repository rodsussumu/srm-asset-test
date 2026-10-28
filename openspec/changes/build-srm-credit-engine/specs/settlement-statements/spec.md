# Spec Delta

## Purpose

Permitir que a mesa consulte liquidações históricas com filtros, paginação consistente por resposta e agregações que preservem a distinção entre moedas.

## ADDED Requirements

### Requirement: Extrato filtrado por instante de liquidação
O sistema SHALL consultar liquidações por período UTC com início inclusivo e fim exclusivo, cedente opcional e moeda de pagamento opcional. O período SHALL ser obrigatório, ordenado e limitado a 366 dias. Sem filtros de cedente ou moeda, SHALL incluir todos os cedentes ou moedas autorizados.

#### Scenario: Combinação dos filtros
- **WHEN** o operador informa período, cedente e moeda USD
- **THEN** o extrato contém somente liquidações daquele cedente pagas em USD no intervalo indicado

#### Scenario: Limites temporais
- **WHEN** uma liquidação ocorre exatamente no início e outra exatamente no fim do período
- **THEN** a primeira é incluída e a segunda é excluída

#### Scenario: Intervalo inválido
- **WHEN** falta uma extremidade, o fim não sucede o início ou o período supera 366 dias
- **THEN** a API responde 400 com os campos inválidos

### Requirement: Paginação no servidor
O sistema SHALL paginar o resultado com página inicial zero, tamanho padrão 20 e máximo 100. SHALL ordenar por instante de liquidação decrescente e identificador decrescente como desempate, retornando itens, número da página, tamanho e total de registros. Conteúdo e contagem de uma resposta SHALL representar o mesmo recorte do banco.

#### Scenario: Segunda página e desempate
- **WHEN** existem 45 liquidações correspondentes e é solicitada a página 1 com tamanho 20
- **THEN** a resposta contém os registros de posições 21 a 40 na ordenação definida e total 45

#### Scenario: Página sem resultados
- **WHEN** o filtro não encontra liquidações ou a página está além da última
- **THEN** a API responde 200 com lista vazia e contagem correta, sem tratar ausência como erro

### Requirement: Histórico e totais com semântica de moeda
O extrato SHALL expor identificador, cedente, recebível, instante, face/VP/deságio em BRL e pagamento com sua moeda. SHALL retornar contagem e totais de pagamento por moeda para todo o filtro, independentes da página. Não SHALL somar pagamentos BRL e USD em um total monetário único.

#### Scenario: Resultado multimoeda
- **WHEN** o filtro abrange pagamentos BRL e USD
- **THEN** a resposta apresenta totais BRL e USD separados e preserva valores auditados sem usar a cotação atual

#### Scenario: Totais independentes da página
- **WHEN** o operador muda de página mantendo os mesmos filtros e sem novas gravações
- **THEN** os totais do filtro permanecem iguais e incluem também registros fora da página
