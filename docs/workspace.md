# Painel de operações

O Angular oferece login/logout, cadastro de cedentes e recebíveis, simulação, confirmação e extrato em português. Rode `npm ci`, `npm start` no frontend e o backend em 8080; o proxy mantém as chamadas na mesma origem. No Compose, nginx serve o bundle e encaminha a API pela mesma origem. Configure as duas contas conforme [sessão e acesso](authentication.md).

## Operação

1. Entre com a conta de operador; ADMIN também opera e pode sincronizar o câmbio.
2. Selecione ou cadastre cedente. Informe face em formato brasileiro (`100.000,00`), vencimento de 1 a 1080 dias, tipo e moeda de pagamento.
3. Após 300 ms sem alterações válidas, o painel solicita preço indicativo. Mostra VP/deságio em BRL, pagamento com moeda, meses, base/spread, política, arredondamento, data da operação e cotação/vigência/origem em USD. A base e spread são frações mensais, sem conversão em percentuais no navegador.
4. Cadastre o recebível. O formulário fica bloqueado porque não há edição de recebíveis neste escopo. O painel simula novamente os dados persistidos e exige o botão **Confirmar liquidação**.
5. Após sucesso, mostra UUID/valor/data da liquidação, atualiza a listagem e o extrato. **Novo recebível** inicia outra operação.

Os controles são HTML nativos, com labels, mensagens associadas por aria-describedby, foco visível e navegação por teclado. O formulário usa submit nativo, inclusive Enter. Os valores são strings do input à API: normalização e agrupamento brasileiros não usam Number/parseFloat. A face aceita até 16 dígitos inteiros e dois centavos; apresentação de pagamentos/totais comporta strings maiores sem perda.

## Respostas antigas e falhas

`CreditState` separa regras/estado da apresentação. Uma mudança limpa o resultado imediatamente e `switchMap` cancela tanto o debounce quanto a requisição anterior. Entradas inválidas cancelam simulação; erros 400 são apresentados junto aos campos quando a API os identifica. Falta de câmbio apresenta erro e mantém confirmação indisponível. O backend permanece responsável pela elegibilidade e cálculo.

A confirmação guarda em memória um UUID e corpo imutáveis por intenção. Duplo clique durante envio é ignorado. Timeout local de 15 segundos, perda de conexão, 5xx ou REQUEST_IN_PROGRESS mantêm a intenção para retry manual com a mesma chave/corpo; os dados e a escolha de outro título ficam bloqueados. Não há retry automático financeiro. PRICE_CHANGED apaga preço/intenção, exige nova simulação e outro clique explícito, gerando nova chave. RECEIVABLE_ALREADY_SETTLED orienta consultar o extrato.

Após recarregar ou autenticar novamente, a intenção anterior não é restaurada nem reenviada: consulte **Recebíveis cadastrados**, por paginação ou UUID, e o extrato. O estado SETTLED vem do servidor e não permite nova confirmação. Datas do extrato podem ser ampliadas para localizar operações de dias anteriores. Cadastro de recebível não possui o protocolo idempotente da liquidação: se a resposta desse cadastro se perder, consulte a listagem antes de cadastrá-lo novamente.

## Extrato

Filtros de período/cedente/moeda são enviados ao servidor. Toda mudança volta à página zero e cancela a resposta anterior. Datas são civis em America/Sao_Paulo, independentemente do fuso do computador; o fim inclui o dia selecionado e vira limite exclusivo no dia seguinte. A conversão encontra o primeiro instante do dia, inclusive em transições históricas de horário de verão, sem presumir UTC−3 para toda data.

Cada página tem 20 itens. Contagem/totais vêm do backend e abrangem o filtro inteiro, preservando BRL e USD separadamente; não há soma entre moedas. O painel apresenta carregando, vazio e erro, limpa valores antigos durante nova consulta e permite tentar novamente. Páginas distintas podem observar novos commits, conforme [contrato do extrato](statements.md).

## Testes

No frontend, `npm test` executa Vitest pelo builder Angular com HttpTestingController; `npm run lint` e `npm run build` validam os arquivos e templates. Os testes cobrem debounce/cancelamento, entradas inválidas, precisão de centavos, label/foco nativos, exibição de parâmetros USD, cadastro, duplo clique, timeout real do relógio de teste, chaves estáveis, PRICE_CHANGED, recuperação por consulta, filtros/paginação/totais e transições históricas de São Paulo. São testes automatizados do cliente e do contrato HTTP; não substituem a validação integrada em ambiente limpo do bloco 8.


## Conferência local

Em 29/10/2026, o Chrome percorreu login OPERATOR, cadastro por Enter, simulação C1, cadastro do título, confirmação explícita, estado Liquidado e atualização do extrato. Após recarga, a sessão foi preservada e a consulta por UUID confirmou o estado; logout retornou ao login. Foram usados Angular 4206/API 18086 e PostgreSQL descartável 55446, sem editar o banco habitual. [Resultados e limites](quality.md#validação-atual--bloco-6-29102026). O smoke completo BRL/USD permanece no bloco 8.
