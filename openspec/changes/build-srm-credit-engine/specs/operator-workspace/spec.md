# Spec Delta

## Purpose

Oferecer uma experiência de operação em português que permita cadastrar, simular, confirmar e consultar recebíveis, com feedback claro e proteção contra confirmação desatualizada.

## ADDED Requirements

### Requirement: Acesso autenticado por papel
O sistema SHALL exigir autenticação para dados de negócio. O papel OPERATOR SHALL cadastrar, simular, liquidar e consultar; ADMIN SHALL também registrar ou sincronizar câmbio. Credenciais SHALL vir de configuração externa. A sessão SHALL usar cookie HttpOnly, proteção CSRF nas mutações e encerramento explícito. Credenciais não SHALL ser persistidas no armazenamento do navegador.

#### Scenario: Acesso anônimo
- **WHEN** um cliente sem sessão solicita dados de recebíveis, câmbio ou liquidações
- **THEN** a API responde 401 sem expor dados e a interface solicita autenticação

#### Scenario: Operador tenta atualizar câmbio
- **WHEN** um usuário OPERATOR tenta registrar ou sincronizar uma cotação
- **THEN** a API responde 403 e nenhuma cotação é criada

#### Scenario: Mutação sem proteção CSRF
- **WHEN** uma sessão autenticada envia mutação sem token CSRF válido
- **THEN** a API responde 403 sem executar a alteração

### Requirement: Formulário acessível em português
O painel SHALL fornecer cedente, valor de face, vencimento, tipo e moeda de pagamento com rótulos associados, navegação por teclado e erros junto aos campos. Valores monetários SHALL ser mantidos como strings decimais; a apresentação em pt-BR SHALL preservar centavos sem calcular dinheiro em ponto flutuante binário.

#### Scenario: Entrada inválida
- **WHEN** o operador preenche valor zero, formato inválido ou vencimento inelegível
- **THEN** o campo apresenta orientação específica e a confirmação fica indisponível

### Requirement: Simulação reativa sem resposta obsoleta
O painel SHALL solicitar simulação após 300 ms sem alterações em entradas válidas e mostrar carregamento, erro ou resultado. Somente a resposta correspondente às entradas atuais SHALL ser exibida. SHALL mostrar VP/deságio em BRL, pagamento e moeda, prazo, cotação e vigência quando USD, e aviso de preço indicativo.

#### Scenario: Respostas fora de ordem
- **WHEN** o operador altera o valor enquanto uma simulação anterior ainda está em andamento
- **THEN** uma resposta da entrada anterior não substitui o resultado da entrada atual

### Requirement: Confirmação e recuperação de retry
O painel SHALL criar uma chave de idempotência por intenção de liquidação e reutilizá-la em retries de pedido inalterado. SHALL desabilitar confirmação durante o envio. Em preço alterado SHALL exigir nova simulação e confirmação explícita. Após sucesso SHALL exibir identificador e valores da liquidação e atualizar o histórico.

#### Scenario: Duplo clique ou timeout
- **WHEN** há duplo clique ou a resposta se perde após confirmação
- **THEN** o painel não cria nova intenção e o retry usa a mesma chave e o mesmo corpo

#### Scenario: Preço desatualizado
- **WHEN** a API responde 409 PRICE_CHANGED
- **THEN** o painel mostra a necessidade de recalcular e não confirma automaticamente com um novo valor

#### Scenario: Atualização da página após resposta incerta
- **WHEN** a página é recarregada após perda de resposta
- **THEN** o histórico e a listagem do recebível permitem verificar o estado confirmado antes de uma nova tentativa

### Requirement: Histórico com filtros remotos
O painel SHALL oferecer filtros de período, cedente e moeda, navegação de páginas e estados de carregamento, vazio e falha. Alterar filtro SHALL reiniciar a página em zero. Totais SHALL ser identificados por moeda e refletir todo o filtro informado.

#### Scenario: Alteração do cedente
- **WHEN** o operador seleciona outro cedente estando em página posterior
- **THEN** o painel solicita a página zero ao servidor com os novos filtros e apresenta o resultado correspondente
