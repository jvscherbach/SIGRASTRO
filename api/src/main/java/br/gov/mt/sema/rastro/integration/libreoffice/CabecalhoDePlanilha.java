package br.gov.mt.sema.rastro.integration.libreoffice;

import com.sun.star.beans.XPropertySet;
import com.sun.star.beans.XPropertySetInfo;
import com.sun.star.sheet.XHeaderFooterContent;
import com.sun.star.text.XText;
import com.sun.star.text.XTextCursor;
import com.sun.star.uno.UnoRuntime;

/**
 * Cabeçalho e rodapé das páginas de planilha convertida (S3.1): no topo, o nome do documento como o dossiê o conhece
 * (com o nome da aba quando mais de uma aba entra no PDF); sem o rodapé do Calc ("Página 1"), porque a numeração oficial
 * é a do dossiê; tabela centralizada na largura.
 *
 * <p>O título e o nome da aba são dados não confiáveis: vão ao LibreOffice só como texto puro ({@code setString}, que
 * não interpreta campo, fórmula nem marcação), sem caractere de controle e com tamanho limitado.
 */
final class CabecalhoDePlanilha {

    /** Tamanho máximo do título depois da limpeza; o corte final depende da largura da página. */
    static final int TITULO_MAXIMO = 200;
    static final int ABA_MAXIMA = 40;
    static final String SEPARADOR = " — ";
    static final String RETICENCIAS = "…";

    /** Fonte do cabeçalho, em pontos. */
    static final float FONTE = 9f;
    /** Altura do cabeçalho com o espaço até o corpo (1/100 mm): uma linha de 9 pt e folga. */
    static final int ALTURA = 700;
    static final int DISTANCIA_DO_CORPO = 250;
    /** Largura média conservadora de um caractere de 9 pt (1/100 mm), para cortar antes de quebrar a linha. */
    static final int LARGURA_DO_CARACTERE = 190;

    private static final String[] CONTEUDOS = {"RightPageHeaderContent", "LeftPageHeaderContent",
            "FirstPageHeaderContent"};

    private CabecalhoDePlanilha() {
    }

    /**
     * Sem caracteres de controle nem de formatação invisível, espaços em sequência viram um, sem espaço nas pontas e
     * com até {@code maximo} caracteres (cortado com reticências). Nulo vira vazio.
     */
    static String limpar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        StringBuilder limpo = new StringBuilder();
        texto.codePoints()
                .map(c -> Character.isISOControl(c) || Character.isWhitespace(c) ? ' ' : c)
                .filter(c -> Character.getType(c) != Character.FORMAT && Character.getType(c) != Character.SURROGATE
                        && Character.getType(c) != Character.PRIVATE_USE && Character.getType(c) != Character.UNASSIGNED)
                .forEach(limpo::appendCodePoint);
        return cortar(limpo.toString().replaceAll(" {2,}", " ").strip(), maximo);
    }

    /** "documento" ou "documento — aba" com até {@code maximo} caracteres; o nome da aba é preservado, o título é cortado. */
    static String texto(String titulo, String nomeDaAba, int maximo) {
        String aba = limpar(nomeDaAba, ABA_MAXIMA);
        if (aba.isEmpty()) {
            return cortar(titulo, maximo);
        }
        String sufixo = SEPARADOR + aba;
        if (sufixo.length() >= maximo) {
            return cortar(titulo + sufixo, maximo);
        }
        return cortar(titulo, maximo - sufixo.length()) + sufixo;
    }

    /** Corta em {@code maximo} caracteres (contando por code point), terminando em reticências. */
    static String cortar(String texto, int maximo) {
        if (texto.codePointCount(0, texto.length()) <= maximo) {
            return texto;
        }
        if (maximo <= 1) {
            return RETICENCIAS;
        }
        int fim = texto.offsetByCodePoints(0, maximo - 1);
        return texto.substring(0, fim).stripTrailing() + RETICENCIAS;
    }

    /**
     * Antes do layout da aba: rodapé desligado, tabela centralizada na horizontal (não na vertical) e cabeçalho ligado
     * com altura fixa (entra na área útil e na página única) ou desligado, sem título.
     */
    static void preparar(XPropertySet estilo, boolean comCabecalho) throws Exception {
        estilo.setPropertyValue("FooterIsOn", Boolean.FALSE);
        estilo.setPropertyValue("CenterHorizontally", Boolean.TRUE);
        estilo.setPropertyValue("CenterVertically", Boolean.FALSE);
        estilo.setPropertyValue("HeaderIsOn", comCabecalho);
        if (!comCabecalho) {
            return;
        }
        definirSeExistir(estilo, "HeaderIsShared", Boolean.TRUE);
        definirSeExistir(estilo, "HeaderIsDynamicHeight", Boolean.FALSE);
        estilo.setPropertyValue("HeaderBodyDistance", DISTANCIA_DO_CORPO);
        estilo.setPropertyValue("HeaderHeight", ALTURA);
    }

    /** Caracteres que cabem numa linha do cabeçalho na largura útil da página já decidida. */
    static int caracteresQueCabem(XPropertySet estilo) throws Exception {
        int util = (Integer) estilo.getPropertyValue("Width") - (Integer) estilo.getPropertyValue("LeftMargin")
                - (Integer) estilo.getPropertyValue("RightMargin") - inteiroSeExistir(estilo, "HeaderLeftMargin")
                - inteiroSeExistir(estilo, "HeaderRightMargin");
        return Math.max(20, Math.min(TITULO_MAXIMO + ABA_MAXIMA, util / LARGURA_DO_CARACTERE));
    }

    /** Só o texto, no centro, em fonte pequena; esquerda e direita vazias (nada do cabeçalho padrão do Calc). */
    static void escrever(XPropertySet estilo, String texto) throws Exception {
        XPropertySetInfo info = estilo.getPropertySetInfo();
        for (String nome : CONTEUDOS) {
            if (!info.hasPropertyByName(nome)) {
                continue;
            }
            XHeaderFooterContent conteudo = UnoRuntime.queryInterface(XHeaderFooterContent.class,
                    estilo.getPropertyValue(nome));
            if (conteudo == null) {
                continue;
            }
            conteudo.getLeftText().setString("");
            conteudo.getRightText().setString("");
            XText centro = conteudo.getCenterText();
            centro.setString(texto);
            XTextCursor cursor = centro.createTextCursor();
            cursor.gotoStart(false);
            cursor.gotoEnd(true);
            UnoRuntime.queryInterface(XPropertySet.class, cursor).setPropertyValue("CharHeight", FONTE);
            estilo.setPropertyValue(nome, conteudo);
        }
    }

    private static void definirSeExistir(XPropertySet estilo, String nome, Object valor) throws Exception {
        if (estilo.getPropertySetInfo().hasPropertyByName(nome)) {
            estilo.setPropertyValue(nome, valor);
        }
    }

    private static int inteiroSeExistir(XPropertySet estilo, String nome) throws Exception {
        return estilo.getPropertySetInfo().hasPropertyByName(nome) ? (Integer) estilo.getPropertyValue(nome) : 0;
    }
}
