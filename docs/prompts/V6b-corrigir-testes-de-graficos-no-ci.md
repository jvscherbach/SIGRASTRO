# V6b: corrigir 4 testes de `ConversaoDeGraficosTest` que falham no CI

**Tipo:** correção de teste, 1 bloco. **Modelo: Sonnet.** Não use Opus.

## Blocos e sessões

| Bloco | Título | Sessão |
|---|---|---|
| 1 | Asserções de ConversaoDeGraficosTest independentes de largura de coluna | SONNET |

## O que aconteceu

O CI falhou só neste ponto: `Tests run: 1937, Failures: 4`, todos em `ConversaoDeGraficosTest` (linhas 86, 94, 103 e 114). O resto da suíte passou.

Todos falham do mesmo jeito: o teste procura o texto de célula `CelulaDeTexto` no PDF, mas o texto extraído é `CelulaDeTeValor P1 10 P2 20 ...`. O texto da célula foi cortado na largura da coluna (a coluna vizinha tem conteúdo, então o Calc não deixa o texto invadir): sai `CelulaDeTe` colado em `Valor`. Isso é efeito da largura da coluna e das métricas da fonte do ambiente (o CI tem fontes e largura diferentes da máquina do responsável), não defeito do produto. No teste `pacoteSemConteudoAtivoDesenhaOGrafico` o texto do gráfico apareceu (`Serie Sintetica Alfa`), ou seja, o gráfico foi desenhado no CI, que é o que o bloco 3 do V6 queria provar.

## Regras

1. Não rode comandos git, exceto `git diff`. Quem commita é o responsável.
2. Nenhum dado real; só arquivos sintéticos.
3. Não altere o código de produção (`PerfilSeguro`, conversor, filtros). Só o teste e a fixture. Se achar que o produto precisa mudar, pare e descreva.
4. Não suba containers e não abra `.env.local`.

---

## Bloco 1 (SONNET): asserções independentes de largura de coluna

**O que fazer**

1. Leia `ConversaoDeGraficosTest` e a fixture que monta a planilha sintética. Confirme a causa acima (coluna estreita com texto cortado).
2. Torne as asserções independentes de largura de coluna e de fonte:
   - Nas planilhas com macro, objeto OLE e vínculo externo (perfil estrito), a prova de que "o texto das células continua saindo" deve usar valores que cabem na coluna: por exemplo `P1`, `P2`, `10`, `20` (já aparecem no texto extraído), ou um texto de célula curto (até 6 caracteres) numa coluna alargada na fixture. Ao mesmo tempo, prove que o gráfico não foi desenhado: o texto `Serie Sintetica Alfa` (título/série do gráfico) não aparece.
   - Em `pacoteSemConteudoAtivoDesenhaOGrafico`, a prova é o texto do gráfico presente (`Serie Sintetica Alfa`) e, se quiser, os rótulos de eixo. Não dependa de `CelulaDeTexto`.
   - Compare textos normalizando espaços e ignore quebra de linha.
3. Se alguma asserção seguinte (por exemplo o aviso "os gráficos desta planilha não foram incluídos") também depender da largura, ajuste do mesmo modo. Rode a classe inteira, não só a linha que falhou.
4. Garanta que o teste passe também no CI: o ambiente dele tem LibreOffice 24.2 e fontes Liberation/Carlito/DejaVu. Não use `Thread.sleep` nem tempo fixo; siga o padrão de pulo (`Assumptions`) que os outros testes de conversão já usam quando o LibreOffice não existe.
5. Rode `ConversaoDeGraficosTest` e os demais testes de `integration.libreoffice`. Não precisa rodar a suíte completa.

**Ao final** responda com: o que estava errado, o que mudou nos testes, o resultado dos testes rodados e `git diff --stat`.

**Ponto de parada.** Commit sugerido: `test(conversao): independe da largura de coluna nas provas de gráficos`
