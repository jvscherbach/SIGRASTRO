# Tipos de dados

Cada coluna com o tipo mais restrito que represente o domínio. Tipos Oracle compatíveis com 19c (ambiente final) e 23 Free (desenvolvimento). Não usar tipos exclusivos do 23, como `BOOLEAN` em coluna.

| Natureza | Tipo | Exemplo no SIGRASTRO |
|---|---|---|
| Identificador técnico | `NUMBER(12) GENERATED ALWAYS AS IDENTITY` | `ID_AUDITORIA_EVENTO` (volume alto de eventos) |
| Identificador de pedido exposto na API | `VARCHAR2(36)` com UUID | `CODIGO` de `DOSSIE_PEDIDO` (evita IDs sequenciais adivinháveis) |
| Referência lógica ao SIGA | `NUMBER(19)` | `ID_REQUERIMENTO`, `ID_DOCUMENTO` |
| Número de processo/requerimento | `VARCHAR2(12)` | `NUMERO_PROCESSO` (`N/AAAA`) |
| Data e hora de evento | `TIMESTAMP WITH TIME ZONE` | `DATA_HORA` da auditoria, `DATA_CRIACAO` do pedido |
| Expiração | `TIMESTAMP WITH TIME ZONE` | `DATA_EXPIRACAO` do pedido |
| Booleano | `NUMBER(1)` + `CHECK (X IN (0,1))` | `SIGILOSO`, `ATIVO` |
| Enumeração fixa de código | `VARCHAR2(20)` + `CHECK` | `PERFIL` (`OPERADOR`, `OPERADOR_SIGILO`, `ADMIN`), `GRUPO` (`DOCUMENTOS`, `ACESSOS`), `TIPO_EVENTO`, `STATUS` do pedido |
| Login | `VARCHAR2(60)` | `USUARIO.LOGIN` |
| CPF/CNPJ | `VARCHAR2(14)` só dígitos | `USUARIO.CPF_CNPJ` (vínculo do interessado, RN07) |
| Nome de pessoa | `VARCHAR2(150)` | `USUARIO.NOME` |
| Título/nome de arquivo (cópia) | `VARCHAR2(255)` | `AUDITORIA_EVENTO_DOCUMENTO.TITULO`, `NOME_ARQUIVO` |
| Origem do documento | `VARCHAR2(30)` + `CHECK` | `ORIGEM` (`DOCUMENTO_PROCESSO`, `SCORP`, ...) |
| Endereço IP | `VARCHAR2(45)` | `ORIGEM_IP` (cobre IPv6) |
| Correlação | `VARCHAR2(64)` | `CORRELATION_ID` |
| Texto curto livre | `VARCHAR2(500)` | `DETALHE` do evento, `MOTIVO` da falha |
| Valor de parâmetro | `VARCHAR2(200)` | `PARAMETRO.VALOR` (tipado pela aplicação) |
| Arquivo | **não guardar** | PDF do dossiê fica em disco temporário (D08) |

Regras:

- Nunca datas como texto; nunca `FLOAT` para valores exatos.
- Não usar `VARCHAR2(4000)` "por segurança".
- Enums que a interface lista (perfis, tipos de evento) são fixos e pequenos: `CHECK` basta. Se passarem a ser administráveis, virar tabela de domínio.
- Tablespace do schema próprio: alinhar com quem administra a instância institucional.
