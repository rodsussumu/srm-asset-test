# Sessão e acesso local

O backend usa Spring Security com sessão, autenticação por formulário e senhas verificadas por BCrypt. Configure no ambiente `APP_USERNAME`/`APP_PASSWORD` para OPERATOR e `ADMIN_USERNAME`/`ADMIN_PASSWORD` para ADMIN. ADMIN também possui OPERATOR. Os nomes devem ser distintos e as senhas ter pelo menos 12 caracteres; não use os exemplos fora do desenvolvimento. Para um `.env` anterior ao bloco 6, acrescente ADMIN_USERNAME e ADMIN_PASSWORD; APP_ROLES deixou de ser utilizado.

As senhas vêm do ambiente, são transformadas em hashes no início da aplicação e não são devolvidas pela API. São duas contas em memória, sem cadastro de usuários ou federação. Alterar a configuração exige reinício. Nomes são identidades de auditoria e do escopo idempotente: mantenha-os estáveis. O limite desse modelo demonstrativo inclui ausência de MFA, governança de usuários, sessões distribuídas e limitação de tentativas de login.

Use o Angular em localhost:4200 com o proxy de desenvolvimento (nginx no Compose previsto para o bloco 7). O navegador acessa a API pela mesma origem; não há CORS irrestrito. O cookie JSESSIONID tem HttpOnly e SameSite=Lax, com timeout de sessão de 30 minutos. `COOKIE_SECURE=false` serve apenas HTTP local; em HTTPS configure `COOKIE_SECURE=true`. A sessão reside na memória do backend e não sobrevive ao reinício.

## Protocolo HTTP

1. GET `/api/v1/auth/csrf`, público: `{headerName:"X-CSRF-TOKEN",token:"..."}` e cookie de sessão. Preserve o cookie e envie o token no cabeçalho indicado.
2. POST `/api/v1/auth/login`, `application/x-www-form-urlencoded`, campos `username` e `password`, mais o CSRF. Sucesso 204; credenciais inválidas 401 INVALID_CREDENTIALS; CSRF ausente/inválido 403.
3. Após autenticar, GET `/api/v1/auth/csrf` novamente. O framework altera o identificador da sessão e invalida o token anterior para proteger contra fixação. Os tokens expostos usam mascaramento XOR padrão do Spring; cada leitura pode ter representação diferente.
4. GET `/api/v1/auth/me`: `{username,roles}`; anônimo recebe 401. OPERATOR cadastra/consulta/simula/confirma; ADMIN também registra/sincroniza câmbio. Papéis desconhecidos recebem 403. OpenAPI e métricas exigem acesso autenticado; somente health não revela dados de negócio.
5. Envie CSRF em toda mutação, inclusive logout. POST `/api/v1/auth/logout` retorna 204, invalida a sessão e expira o cookie. Obtenha novo CSRF antes do próximo login.

Não há HTTP Basic, formulário `/login` ou redirecionamento HTML no protocolo da SPA. A API devolve Problem Details com código e correlação para falhas de autenticação/autorização.

## Navegador

O Angular mantém identidade, CSRF e intenção de confirmação apenas em memória. Senha é limpa do formulário ao iniciar login; cookies são gerenciados pelo navegador. Não usa localStorage/sessionStorage para credenciais, tokens ou intenções. O interceptor só acrescenta CSRF a mutações relativas de `/api/`; 401 elimina identidade/token e volta ao login. Não repete mutações automaticamente após 401/403: faça login e consulte o estado antes de confirmar novamente.

O token é renovado depois do login e logout, conforme a [documentação oficial de CSRF do Spring Security](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).

## Verificação

`AuthenticationTest` executa o filtro real, login com senhas externas de teste, mudança de identificador da sessão, rejeição do CSRF anterior, logout, papéis e falhas 401/403. `session.spec.ts` verifica a sequência de renovação, não envio de CSRF para outras origens, expiração da sessão e ausência de gravações no armazenamento do navegador. As integrações PostgreSQL continuam usando os papéis OPERATOR/ADMIN no harness; o usuário genérico USER não representa um operador.
