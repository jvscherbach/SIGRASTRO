# 12 — Configurações

Tela acessível apenas ao perfil Administrador. Toda alteração gera evento na auditoria de acessos ([11](11-auditoria.md)).

## Perfis e permissões

| Permissão | Operador | Gestor | Administrador |
|---|:---:|:---:|:---:|
| Consultar e visualizar documentos | Só como interessado | ✓ | ✓ |
| Ver e incluir documentos sigilosos | — | ✓ | ✓ |
| Gerar e baixar dossiê | Só como interessado | ✓ | ✓ |
| Auditoria de documentos | Só como interessado | ✓ | ✓ |
| Auditoria de acessos | — | — | ✓ |
| Configurações | — | — | ✓ |

"Só como interessado": processos e requerimentos em que o CPF/CNPJ do usuário é o do interessado (RN07). O Administrador tem acesso irrestrito.

Códigos: `OPERADOR`, `OPERADOR_SIGILO`, `ADMIN`. Na versão definitiva, a identidade vem da autenticação institucional; o perfil é atribuído nesta tela. Itens de menu sem permissão não são exibidos (RN08).

## Abas

### Usuários e perfis

- Lista com nome, login, perfil, situação (ativo/inativo) e último acesso.
- Perfil e situação são alterados direto na lista e gravados na hora (evento `USUARIO`).
- Inclusão por gaveta lateral: nome, login, CPF/CNPJ e perfil. Login é único; CPF/CNPJ é obrigatório para o Operador.
- Usuário inativo não entra no sistema; o histórico de auditoria é mantido.

### Dossiê

| Parâmetro | Padrão | Faixa |
|---|---|---|
| Expiração do dossiê | 30 min | 5–240 min |
| Gerações simultâneas | 2 | 1–8 |
| Sumário | ligado | — |
| Marcadores no PDF | ligado | — |
| Numeração de páginas | ligado | — |
| Origens dos documentos | todas ligadas | uma chave por origem |

Origem desligada some da lista de documentos e não entra no dossiê. A regra de ordem cronológica é fixa (RN03) e só é exibida.

### Conversão

| Parâmetro | Padrão |
|---|---|
| Planilhas | Ajustar à largura (alternativa: uma página por aba) |
| Geometrias (SHP, KML, KMZ) | Fora do dossiê, download à parte |
| Compactados (ZIP, RAR, 7Z) | Desconsiderados |
| Orientação automática | ligado |
| Incluir abas ocultas | desligado |
| Tempo limite de conversão | 120 s (30–600) |

### Conexões

Estado e teste de conexão de: banco do SIGA (somente leitura), arquivos do Licenciamento (com horas até expirar o token), SCORP e taxas (desativada até a forma de obtenção ser confirmada). Tempo limite de download: 60 s (10–300).

Credenciais ficam no arquivo de ambiente do servidor; a tela não as exibe nem edita.

### Auditoria

Retenção por grupo: 1, 2, 5 ou 10 anos. Valores de exemplo no protótipo: documentos 5 anos, acessos 1 ano.

## Salvamento

- Parâmetros alterados ficam pendentes numa barra fixa com a contagem de alterações, "Descartar" e "Salvar alterações".
- O salvamento gera um único evento `CONFIG` com a lista de mudanças (antes e depois).

## Persistência na versão definitiva

No schema próprio: tabela de usuários (login, nome, perfil, situação, último acesso) e tabela de parâmetros (chave, valor, data e usuário da última alteração). As variáveis do arquivo de ambiente continuam valendo como valor inicial e para credenciais.
