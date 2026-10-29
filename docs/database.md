# Credenciais e histórico financeiro

A aplicação usa `DATABASE_USER`/`DATABASE_PASSWORD` (padrão de nome srm_app). O Flyway usa `MIGRATION_USER`/`MIGRATION_PASSWORD`, uma credencial distinta que é dona do esquema. O papel NOLOGIN srm_runtime recebe somente os privilégios necessários. Nenhum segredo real deve ser versionado.

O serviço Compose db-bootstrap executa infra/postgres/bootstrap.sql usando a credencial de migração; execute-o antes de iniciar o backend no host. Ele cria/atualiza o login de execução e concede o papel srm_runtime; as migrations aplicam os privilégios nas tabelas. Identificadores e senhas são passados ao psql por variáveis de ambiente e escapados com format %I/%L. O script recusa usar o dono do banco, um superusuário ou dono de tabelas como login de execução.

Em instalação nova, copie .env.example para .env e configure senhas distintas. Execute na raiz: docker compose up -d --wait postgres e depois docker compose run --rm -T db-bootstrap. Exporte as variáveis de .env e inicie o backend com ./mvnw spring-boot:run no diretório backend. O POSTGRES_USER do Compose é a identidade de migração, enquanto a conexão usada pelas funcionalidades é a de execução. A separação é de conexões e privilégios; nesta demonstração o processo backend ainda recebe a credencial do Flyway para migrar ao iniciar.

## Volume já existente

Não apague o volume para atualizar. Preserve como MIGRATION_USER o usuário que criou o banco existente e como MIGRATION_PASSWORD a senha que esse banco já possui. Defina DATABASE_USER=srm_app e uma nova DATABASE_PASSWORD para a execução. Depois execute docker compose up -d --wait postgres e docker compose run --rm -T db-bootstrap. Reinicie a API com as quatro variáveis exportadas; o Flyway aplica V3/V4. O bootstrap cria o novo login no banco existente. Mudar POSTGRES_PASSWORD no .env não muda a senha de um banco já inicializado. As credenciais e os dados reais do ambiente não são alterados automaticamente pelo agente.

Sem o serviço db-bootstrap, exporte as quatro variáveis e execute o bootstrap com uma conexão administrativa ao banco desejado antes de iniciar o backend:

```sh
PGHOST=localhost PGDATABASE=srm PGUSER="$MIGRATION_USER" PGPASSWORD="$MIGRATION_PASSWORD" psql -X -f infra/postgres/bootstrap.sql
```

O usuário de migração precisa criar papéis e conceder privilégios. Em produção, o bootstrap e as migrations devem ser executados por um job separado, sem entregar essa credencial ao processo de atendimento.

## Restrições aplicadas

- Execução: SELECT/INSERT em cedentes, recebíveis, cotações, liquidações e idempotência.
- Recebíveis: UPDATE somente nas colunas status/version, para a transição com controle otimista.
- Liquidações e cotações: nenhum UPDATE, DELETE ou TRUNCATE; a execução não é dona das tabelas.
- Idempotência: pode completar as colunas do resultado de uma reserva; não pode apagar, alterar a identidade/hash ou reescrever um resultado confirmado. Um trigger protege a transição mesmo contra uma concessão acidental de UPDATE adicional.
- Um constraint trigger diferido exige conclusão da reserva antes do commit. Rollback remove a reserva automaticamente, permitindo nova tentativa com a mesma chave.

Administradores do banco continuam uma fronteira de confiança. Os testes usam login real srm_app, sem SET ROLE sobre conexão de superusuário, e validam tanto a gravação permitida quanto a recusa de alteração/exclusão do histórico.

Na orquestração completa, docker compose up --build -d --wait executa postgres, bootstrap e API nessa ordem automaticamente. Os comandos separados acima permanecem úteis no desenvolvimento no host. [Operação e portas](operations.md).
