# Painel Angular

Use Node 22.16.0 e npm ci. npm start inicia em localhost:4200 com proxy para a API em localhost:8080. Configure as duas contas e execute o backend conforme o [README principal](../README.md). Login, cadastro, simulação, confirmação e extrato estão descritos no [painel](../docs/workspace.md).

Validação: npm run lint, npm test e npm run build. Dinheiro permanece string, estado e apresentação são separados e CSRF/identidade ficam em memória. Após resposta incerta de confirmação, repita o mesmo pedido; após recarga consulte o estado antes de uma nova intenção. [Sessão](../docs/authentication.md).
