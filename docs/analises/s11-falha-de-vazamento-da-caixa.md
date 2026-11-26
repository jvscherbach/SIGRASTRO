# S11a: falha do teste de vazamento da caixa

**Resumo: falso positivo intermitente (flakiness). O padrão de 11 dígitos do teste casa com um trecho aleatório do UUID do `X-Correlation-Id`; não há vazamento real.**

## 1. Reprodução e evidência

- Teste: `CaixaAcessoVazamentoResilienciaHttpTest.nenhumCpfNemChaveEmRespostaErroLogOuAuditoriaDeTodoOFluxoDaCaixa`, asserção em `semDadoPessoalNemChave` (linha 195), chamada pelo `respostas.forEach` (linha 263).
- Saída que disparou: a **lista de cabeçalhos HTTP** de uma das respostas, montada na linha 252 (`h + "=" + r.getHeaders(h)`), não corpo, log nem auditoria. Texto: `[X-Correlation-Id=[<UUID>], Content-Type=[application/json]]`.
- Padrão que casou: `(?<![0-9])\d{11}(?![0-9])` (a 2ª expressão, "CPF só dígitos"). Nem o CPF formatado, nem CNPJ, nem chave opaca.
- Origem: o último grupo do UUID v4 (12 caracteres hexadecimais) daquela requisição tinha 11 dígitos decimais seguidos delimitados por uma letra (formato `...-<letra><11 dígitos>`). O UUID é gerado por `UUID.randomUUID()` em `shared/api/CorrelationIdFilter.java:45`, a cada requisição. Não vem de fixture: o CPF da fixture (`CPF_A`, calculado em tempo de execução, linhas 73 a 92) não aparece no trecho.
- Reprodução: 3 execuções isoladas (`./mvnw -B -q -Dtest=CaixaAcessoVazamentoResilienciaHttpTest test`) passaram. Coerente com falha aleatória e não com dependência de ordem (a classe usa `@DirtiesContext AFTER_EACH_TEST_METHOD`, então não há estado compartilhado entre testes).

## 2. Padrões de `semDadoPessoalNemChave` (linhas 195 a 200)

| Expressão | Pode casar com algo legítimo? |
|---|---|
| `CPF_A`, `CPF_A_DIGITOS` (literais) | Não: valor fictício específico da fixture. |
| `(?<![0-9])\d{11}(?![0-9])` | **Sim.** Trecho de UUID de correlação (grupo final de 12 hex, ~0,4% por UUID); também timestamp em milissegundos não (13 dígitos), mas protocolo ou número de requerimento de 11 dígitos, se existir. |
| `(?<![0-9])\d{14}(?![0-9])` | Possível com id de 14 dígitos (nº de processo/CNPJ-like); no UUID não (grupo de 12). |
| `\d{3}\.\d{3}\.\d{3}-\d{2}` | Improvável; só CPF formatado. |
| `\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}` | Improvável; só CNPJ formatado. |
| `(?i)[0-9a-f]{32,}` | UUID tem hífens, não casa; casaria hash SHA/HMAC completo (o que se quer pegar). O id curto de 16 hex da chave opaca (`/api/painel/analistas/{chave}`) **não** é pego, pois exige 32+. |
| `doesNotContainIgnoringCase("chave")` | Casaria qualquer mensagem que contenha a palavra "chave" em texto legítimo. Hoje não ocorre. |
| `"taskAnalisar"`, `"idDaTarefa"` | Específicos; sem colisão. |

Probabilidade: um UUID tem ~0,43% de chance de conter 11 dígitos isolados no último grupo (2 x 6/16 x (10/16)^11). O teste varre ~11 listas de cabeçalhos mais corpos de erro com o id de correlação; ordem de grandeza de 5% de falha por execução do teste (cerca de 1 em 20), mais nos testes que repetem o mesmo padrão em respostas 403 (linha 223).

## 3. Veredito

Falso positivo. Evidência: o trecho é um cabeçalho de correlação aleatório; o CPF da fixture não consta; as execuções repetidas passam. Para descartar de vez basta ver o valor exato do trecho no relatório surefire (já mostra o X-Correlation-Id acima, com a sequência de dígitos no último grupo).

## 4. Se fosse real

Não se aplica. O painel (S10 e bloco 9) não tem o mesmo ponto, mas o mesmo helper é reutilizável por testes do painel e teria a mesma fragilidade se varresse cabeçalhos.

## 5. Correção mínima proposta (APLICADA: opção B)

**Aplicado.** `semDadoPessoalNemChave` agora checa primeiro o texto original contra `CPF_A`/`CPF_A_DIGITOS` e depois remove os UUIDs (`<uuid>`) antes dos demais padrões, sem afrouxar nenhum deles. O helper ficou package-private (`static`) e ganhou `cpfSinteticoDigitos()`. Novo teste sem Spring `VarreduraDeVazamentoTest`: negativo (UUIDs com 11 dígitos no último grupo não disparam) e positivos (CPF sintético calculado na hora, outro valor de 11 dígitos e hex de 32+ disparam, inclusive com UUID no mesmo texto). Código de produção intocado. Resultado: `CaixaAcessoVazamentoResilienciaHttpTest`, `CaixaHttpTest`, `VarreduraDeVazamentoTest` e `ArchitectureTest` com 50 testes, 0 falhas.

Proposta original:

Arquivo: `api/src/test/java/br/gov/mt/sema/rastro/caixa/api/CaixaAcessoVazamentoResilienciaHttpTest.java`.

Opção A (preferida, mais fiel): na linha 252, não incluir o valor do `X-Correlation-Id` na varredura; mantém os demais cabeçalhos:

```java
respostas.add(r.getHeaderNames().stream()
        .filter(h -> !"X-Correlation-Id".equalsIgnoreCase(h))
        .map(h -> h + "=" + r.getHeaders(h)).toList().toString());
```

Opção B: remover UUIDs antes de varrer, no início de `semDadoPessoalNemChave`:

```java
String limpo = texto.replaceAll("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<uuid>");
```

(e usar `limpo` nas asserções de padrão; manter `doesNotContain(CPF_A...)` sobre o texto original.) A opção B protege também os corpos `ProblemDetail` que carregam o id de correlação.

Provas (testes do próprio helper, sem Spring):
- Negativo: `semDadoPessoalNemChave("{\"correlationId\":\"ce428a6b-3943-472b-92a3-c90247559032\"}")` não dispara (valor sintético com 11 dígitos no último grupo).
- Positivo: o CPF da fixture, puro, formatado, e um hex de 32+ caracteres disparam; `<UUID>` + CPF no mesmo texto ainda dispara.

## 6. Cobertura do teste (lacunas)

Cobre: resposta de `GET /api/caixa` (resumo e lista, com filtros e ordens, estado PRONTO), `/api/caixa/avisos`, `POST /api/caixa/atualizar`, `POST /api/caixa/avisos/visto` (vazio e com id), erros 400 de parâmetro inválido e 403/401 das rotas de outros perfis, lista de usuários e `/api/auth/me`, saída do processo (CapturedOutput, só CPF puro e formatado) e eventos de auditoria (CPF, 11 dígitos, ids 18001 e 18002).

Não cobre:
- **DOM do front**: nenhuma varredura de HTML/DOM (só e2e e specs do Angular, fora deste teste).
- **Log**: só CPF; não procura chave opaca nem id de tarefa (o comentário da linha 265 promete chave, mas a asserção da linha 266 só checa CPF).
- **Erros 404 e 500 reais** da caixa: não há chamada que provoque 404; 500 só no teste de resiliência (Camunda fora), sem passar por `semDadoPessoalNemChave`.
- **Painel** (`/api/painel`, lista por analista) não é varrido aqui.
- **Corpo de resposta de `POST /api/caixa/atualizar`** entra, mas o corpo dos eventos de auditoria é verificado por `toString()` do `NovoEvento` (campos opacos não são inspecionados).
- Chave opaca de 16 hex (HMAC curto) não é detectada pelo padrão `{32,}`.
