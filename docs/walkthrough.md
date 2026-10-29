# Walkthrough de 10 minutos

Roteiro preparado para apresentação; não é registro de defesa realizada. Antes de começar, execute os checks locais e suba um Compose descartável com login externo. Deixe editor, terminal e navegador prontos, sem exibir .env, senhas, cookies ou tokens na tela compartilhada.

| Tempo | Demonstração | Ponto que precisa ser explicado |
|---|---|---|
| 0:00–1:00 | Abrir [C4](c4.md) e [ER](er.md) | SPA/nginx/API/PostgreSQL; monólito, MVC e conta de migração separada da execução |
| 1:00–2:00 | Mostrar [SPEC](../SPEC.md) e golden cases | teto(dias/30), base/spreads como frações, half-even, VP BRL antes de USD |
| 2:00–4:00 | Login, cadastrar título, simular/confirmar BRL ou USD e consultar extrato | Preço indicativo, aceite explícito, moeda e snapshot; CSRF e valores como strings |
| 4:00–6:00 | Executar demonstração de concorrência abaixo | Duas leituras da mesma versão, um commit, um 409, uma liquidação e versão +1 |
| 6:00–7:00 | Apontar replay e rollback nos testes | Hash/ator/rota, reserva transacional, replay antes de recálculo, commit antes do sucesso |
| 7:00–8:00 | Logs/métricas e [resultados HTTP](performance.md) | Contador só pós-commit, sem replay; SQL/ledger versus memória; limites do benchmark |
| 8:00–9:00 | Executar fixture de extensão por strategy | Outro spread usa a mesma fórmula, sem copiar o algoritmo; o que um tipo novo real exigiria |
| 9:00–10:00 | [REVIEW](../REVIEW.md), [DECISIONS](../DECISIONS.md) e [AI_USAGE](../AI_USAGE.md) | Falhas prioritárias, erro real detectado, limites de escopo e pontos ainda não aferidos remotamente |

As janelas reservadas pressupõem Docker ativo, dependências baixadas e terminal no backend. Se uma imagem precisar ser baixada, explique a espera em vez de atribuir duração garantida ao teste.

## Concorrência determinística

No diretório backend, com Java 21 e Docker:

```sh
./mvnw -B -ntp -Dtest=SettlementIntegrationTest#twoTransactionsReadTheSameVersionAndOnlyOneConfirms test
```

O harness intercepta `ReceivableRepository.findById` apenas no contexto de teste. Cada transação lê AVAILABLE/version=0, sinaliza a barreira e só então ambas são liberadas. O teste exige os status 201/409, uma linha em settlements, uma resposta idempotente e version=1. Não usa sleeps para fabricar a disputa e não acrescenta endpoint/hook de teste à aplicação. O login da aplicação é srm_app, com PostgreSQL descartável real.

Abra SettlementTransactionService: REQUIRES_NEW/READ_COMMITTED, reserva da chave, leitura/recálculo, comparação esperada, markSettled/flush, snapshot e conclusão da reserva. O proxy só retorna após commit; a fachada registra métrica/log nesse retorno. Explique UNIQUE(receivable_id) como defesa de banco independente do controle otimista.

Para aprofundar conforme perguntas:

```sh
./mvnw -B -ntp -Dtest=SettlementIntegrationTest#simultaneousRetriesWaitForTheOriginalAndReturnIdenticalSuccess test
./mvnw -B -ntp -Dtest=SettlementIntegrationTest#deferredCommitFailureCannotReturnSuccessAndRollsBackTheCompletedResult test
```

Os exemplos estão cobertos na suíte final; o tempo da defesa não exige rodar todos de novo.

## Extensão sem duplicar fórmula

```sh
./mvnw -B -ntp -Dtest=StrategyExtensionTest test
```

StrategyExtensionTest injeta uma implementação alternativa de SpreadStrategy para DUPLICATA, somente no teste: spread 0.0200 com base 0.0100, face 100000.00 e três meses. O motor original retorna VP 91514.17 e deságio 8485.83. Isso demonstra que a fórmula depende da interface, e não de um if por spread. Não altera a configuração de produção nem introduz um novo tipo suportado.

Para uma alteração real solicitada na defesa:

1. Especificar o tipo/regra, prazo, precisão, moeda e aceite, com novo caso esperado independente do motor.
2. Adicionar o valor ao enum ReceivableType e implementar SpreadStrategy; registrar em BusinessConfig sem copiar a fórmula. Se a regra deixar de ser apenas spread, rever a interface conscientemente.
3. Criar uma migration **nova** para ampliar as CHECKs de receivables e settlements. Não editar migrations já aplicadas. Atualizar o contrato OpenAPI e os tipos/opções/labels do Angular.
4. Verificar snapshots e identificação da política; parâmetros antigos devem continuar reproduzíveis. Rodar golden cases existentes, novo caso, integrações PostgreSQL e checks Angular.

É uma proposta de como executar a mudança, não uma alegação de que um terceiro tipo já foi implementado.

## Evidências para perguntas

- [Smoke integrado](smoke.md) e captura: operações reais da API local, sem edição manual de dados de negócio.
- [Aferição](performance.md): 2000 amostras, 10 clientes, p95 e limites; não prometer saturação/produção.
- [Matriz](traceability.md): 32 requisitos das seis specs, testes e documentos.
- [Qualidade](quality.md): 198 testes backend, 48 frontend, cobertura e testes negativos de gates.
- Histórico Git com branches por funcionalidade e integração pelas PRs #1–#8. CI remota, convites e defesa ao vivo não são resultados já comprovados.

No trecho de desempenho, mostre também o [perfil restrito](performance-constrained.md): 2 CPUs/4 GiB agregados, 100 mil medidas, p95 60,923/94,195 ms e a tentativa interrompida preservada. Explique a diferença entre cotas por serviço e uma VM compartilhada, e a limitação real do coletor. Não execute toda a carga durante o walkthrough de dez minutos.
