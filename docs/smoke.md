# Smoke final — 29/10/2026

Execução em Compose limpo srm-new-task8, volume próprio e portas PostgreSQL 55448/API 18088/nginx 14208/mock 18098. Banco novo, bootstrap encerrado em zero e quatro serviços saudáveis. O .env e os volumes habituais foram preservados. A massa SQL de benchmark não foi usada neste projeto.

## Fluxo HTTP pelo proxy

scripts/verify-compose.py retornou exit 0 e criou cedente/títulos/liquidações pela API. Verificou 401 anônimo, login OPERATOR/ADMIN com CSRF, cookie HttpOnly/SameSite=Lax, correlação e conexões SQL reais srm_app sem UPDATE/DELETE no histórico. A sincronização inicial criou cotação local 5.4321.

Com o mock parado, sincronização retornou 503 em menos de três segundos; C1 e C3 continuaram válidos usando cotação persistida. Face 100000.00 e prazo 90 dias produziram VP 92859.94/deságio 7140.06 e pagamentos BRL 92859.94/USD 17094.67. Cada confirmação foi repetida três vezes com a mesma chave/corpo, preservando status 201, bytes e Location. Contador cresceu somente uma vez por moeda e o extrato teve dois registros/totais separados.

Ao parar PostgreSQL, readiness devolveu 503 apenas com status DOWN, enquanto liveness permaneceu 200. Start recuperou readiness. Logs reais apresentaram timestamp UTC, evento/resultado, correlação e IDs de auditoria; não continham os canários/senhas externos testados e houve exatamente dois eventos novos de confirmação nesse intervalo. [Saída original desta execução](evidence/compose-smoke.txt).

## Angular no Chrome

Após o verificador HTTP, o Chrome acessou a SPA construída/servida pelo nginx. Login OPERATOR exibiu os títulos liquidados e totais separados de BRL 92.859,94 e USD 17.094,67. O formulário foi preenchido com o cedente sintético, face 100.000,00, vencimento 27/01/2027, DUPLICATA e USD. Exibiu VP/deságio BRL, pagamento US$ 17.094,67, prazo, taxas, política e cotação/vigência.

Cadastrar o título não confirmou automaticamente. Depois da nova simulação, o clique explícito confirmou a liquidação 4dd2bff9-c113-42c7-ad7e-beb7a76b4e2e do recebível 30fa17c6-1136-47c2-8d24-2a74d2faefd8. A listagem mostrou Liquidado e o extrato passou a três operações, com BRL 92.859,94 e USD 34.189,34 (duas operações USD).

Recarga preservou a sessão e recuperou histórico; filtrar USD mostrou somente duas liquidações e seu total. Logout retornou à tela de acesso. A [captura real](evidence/smoke-workspace.png) registra a confirmação e o extrato antes da recarga. Trata-se de dados sintéticos, sem transferência financeira ou edição manual de tabelas.

## Limites e reprodução

O teste de interface não injeta perda real de resposta no browser; timeout, duplo clique e PRICE_CHANGED estão na suíte Angular, e a perda após commit é coberta pela integração backend. O verificador HTTP comprova replay real pelo proxy. A concorrência determinística usa duas conexões PostgreSQL no harness, conforme [roteiro](walkthrough.md).

Para repetir o smoke de API, siga [operação](operations.md) em projeto descartável e execute scripts/verify-compose.py com --project, --env-file e --base-url. Configure base/mock padrão, banco novo e nenhuma confirmação externa enquanto o verificador conta eventos. O script para/restaura dependências e cria dados; não o execute no ambiente habitual. Ao finalizar a conferência de navegador, encerre esse projeto com down --volumes.

O benchmark usa outro projeto e dados próprios; [aferição HTTP](performance.md). Este smoke é evidência local, sem comprovar publicação remota, CI no GitHub ou defesa humana já realizada.
