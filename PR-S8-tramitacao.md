# PR: S8, tramitação (histórico, com quem esteve e fluxograma simplificado)

**Base:** `main`
**Branch:** `Sprint-8`
**Título sugerido:** `S8: tramitação com histórico, com quem esteve e fluxograma simplificado`

> Arquivo de apoio para abrir o PR à mão. Não commitar: fica fora do Git.

## Resumo

Fecha a S8 (12 blocos). O detalhe do requerimento passa a mostrar o **caminho percorrido**: a linha do tempo com todas as etapas por onde o requerimento passou, com entrada, saída, duração, **com quem esteve** em cada uma e as voltas que de fato aconteceram, com o motivo quando é determinável. Ao lado, um **fluxograma simplificado** em linha reta, só com o que já passou e a etapa atual. Para requerimento sem instância no Camunda (2021/2022), um **plano B** mostra o histórico aproximado, sempre rotulado como aproximado.

Também entram as correções herdadas da S7: a verificação da revisão do processo passa a aparecer como **"Concluído"** na posição (P38), e o termo "Camunda" sai de todas as telas de consulta, para todos os perfis (P39).

## O que muda, por bloco

| Bloco | Entrega |
|---|---|
| 1 | Fixtures históricas do Camunda fictício: caminhos com voltas, ruído, filhas e cenários HC01 a HC15, LC01 a LC03, finais e plano B (PB01 a PB03) |
| 2 | Leitura do caminho percorrido (`ACT_HI_*`), sempre por `PROC_INST_ID_`, com limite de linhas e sem remover ruído |
| 3 | Reconstrução das etapas, ciclos e voltas (função pura), com motivo da volta por regra explícita; sem motivo determinável, o motivo fica vazio |
| 4 | Finais pelo evento de fim (nunca pela situação), avisos de divergência e "Concluído" na verificação da revisão |
| 5 | Plano B para requerimento sem instância: histórico aproximado, sem responsável, sem ciclos |
| 6 | API: `GET /api/requerimentos/{id}/tramitacao` e `GET /api/processos/{numero}/tramitacao`, com acesso, auditoria e perfis |
| 7 | Desempenho: cache curto com *single flight*, tempo limite por pedido, teto de leituras simultâneas ao Camunda, colapso de repetições no SQL |
| 8 | Front: seção **Tramitação** com linha do tempo e "com quem esteve"; P38 e P39 |
| 9 | Front: fluxograma simplificado (HTML, CSS e SVG, sem biblioteca), com impressão em preto e branco |
| 10 | Front: plano B na mesma seção e estados de erro (`INDISPONIVEL`, `NAO_SUPORTADO`, `HISTORICO_TRUNCADO`) |
| 11 | Matriz de acesso, varredura de vazamento (CPF, ids técnicos, chave, token) nos cenários HC, LC e PB e negação do nome |
| 12 | Roteiro de homologação `docs/roteiros/s8-homologacao.md` e fechamento da documentação |

## Regras de segurança mantidas

- Nenhuma consulta de escrita no SIGA; todo SQL novo é `SELECT` com parâmetros nomeados e concentrado no componente de acesso do Camunda.
- CPF nunca sai em resposta, tela, log, erro ou auditoria; ids técnicos, chave e token só para o Administrador, em campo de diagnóstico.
- O plano B nunca consulta `SERVIDOR` nem lê o CPF da tabela de histórico.
- Quando o Camunda falha ou demora, a resposta é `INDISPONIVEL`; o plano B **não** entra no lugar.
- Nenhum sistema externo real é acessado em teste; os cenários são fictícios e gravados no Oracle de teste.

## Pontos para a revisão

- **Hipóteses ainda NÃO CONFIRMADAS (P40, P41):** valores das variáveis da lista branca (`RA`, `RP`, `isRevisaoAnalise`, `isRevisaoParecer`, `REVISAR`), gravação da correção expirada, ids de "Report Kafka" e de repetição por erro, e o mapa de eventos do plano B. Validar com o BPMN ou com um requerimento real antes de tratar como fato.
- **Mudança de contrato:** a posição (S7) passa a dar o estado `CONCLUIDO` na verificação da revisão, e um final com situação fora da tabela de compatibilidade passa de `SITUACAO_NAO_CONFERE` para `FINAL_E_SITUACAO_DIVERGEM`.
- **Bundle do front:** a tela de detalhe passou a carregar sob demanda, o que mantém o bundle inicial abaixo do limite de aviso do build.
- **Riscos abertos:** R73 (consulta do plano B sem índice conhecido em `ID_REQUERIMENTO`) e R74 (cache e limite por instância do Rastro). Recomendações de índice ao SIGA em P42, só como documento.
- **R66 aceito (D84):** o servidor MCP continua sem dados no ambiente de verificação.

## Testes

Os testes de cada bloco foram rodados na sessão de cada bloco (Vitest, Playwright com API simulada, JUnit com Oracle 23 Free por Testcontainers quando há Docker, e ArchUnit). Neste último bloco (12) só houve documentação: nenhum código foi alterado e nenhum teste foi rodado de novo.

**Ainda não feito:** a homologação pelo responsável (roteiro `docs/roteiros/s8-homologacao.md`). A coluna **Tela** fica `Não executado` até existir ambiente com SIGA fictício (R66, R69), e a conferência com dados reais é fora do repositório.

## Como revisar

1. `git diff main...Sprint-8 --stat` mostra o escopo da sprint.
2. Começar por `docs/15-tramitacao.md` (regras de reconstrução, motivos da volta, finais e plano B) e por `docs/roteiros/s8-homologacao.md`.
3. Em código, `api/src/main/java/br/gov/mt/sema/rastro/tramitacao/` e `front/src/app/features/detalhe/` (seção Tramitação, fluxograma e plano B).

## Mensagem de commit sugerida (bloco 12)

```
docs(tramitacao): roteiro de homologação e fechamento da S8

Roteiro com uma linha por cenário (HC01 a HC15, LC01 a LC03, HF01 a HF04,
LF01 a LF05 e PB01 a PB03), sequência esperada da linha do tempo, finais e
conferência com dados reais feita pelo responsável, fora do repositório.
Atualiza docs/14, docs/15, docs/10 e docs/progresso/s8.md.

Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>
```

🤖 Generated with [Claude Code](https://claude.com/claude-code)
