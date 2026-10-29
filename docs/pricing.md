# Motor financeiro

O motor em `model.PricingEngine` é independente de Spring, HTTP e banco. Recebe taxa base e strategies; cada strategy seleciona o spread, e a fórmula permanece centralizada. A política inicial é `monthly-ceil-30d-v1`, com base de 0.0100, spread DUPLICATA de 0.0150 e CHEQUE de 0.0250.

`VP = face / (1 + base + spread)^meses`. A potência inteira é calculada exatamente; a divisão utiliza 50 dígitos significativos. O VP é arredondado para duas casas por half-even; deságio é face menos esse VP. Para USD, dividir o VP BRL já arredondado pela cotação BRL por USD e aplicar half-even novamente. Nenhum valor financeiro usa float/double.

| Caso | VP BRL | Pagamento | Deságio BRL |
|---|---:|---|---:|
| C1 | 92859.94 | BRL 92859.94 | 7140.06 |
| C2 | 23337.77 | BRL 23337.77 | 1662.23 |
| C3 | 92859.94 | USD 17094.67 | 7140.06 |

Executar no backend: `./mvnw -Dtest=PricingEngineTest test` com Java 21. Os testes também verificam empates de arredondamento, prazo zero no motor, limite de 36 meses, valores máximos, cotação inválida e ausência de fallback de strategy. A cotação em C3 é entrada explícita do teste; o motor não busca câmbio externo.

O endpoint de simulação será implementado no bloco 3. A liquidação permanece fora do bloco 2.

## Prazo e elegibilidade

`MonthlyTermPolicy` recebe um Clock injetável. A data da operação é capturada em America/Sao_Paulo e o prazo é teto(dias corridos/30), de 1 a 36 meses. Vencimento hoje, passado ou além de 1080 dias é inválido. Prazo zero existe somente no motor isolado para testes e não torna o título elegível. O retorno inclui a data de referência usada e o número de meses.

`MonthlyTermPolicyTest` cobre dias 0, 1, 30, 31, 1080 e 1081, passado e a virada da meia-noite em São Paulo com Clock fixo. Executar: `./mvnw -Dtest=PricingEngineTest,MonthlyTermPolicyTest test`.
