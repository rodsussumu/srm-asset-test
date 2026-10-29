# Migrations

`V1__create_assignors_and_receivables.sql` cria cedentes e recebíveis, suas constraints e índices.
O Flyway aplica e valida as migrations ao iniciar. Não alterar uma migration já aplicada; mudanças futuras exigem nova versão.
O modelo e os testes estão documentados em `docs/er.md` na raiz do repositório.
