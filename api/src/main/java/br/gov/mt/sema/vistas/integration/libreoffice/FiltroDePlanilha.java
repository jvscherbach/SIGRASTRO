package br.gov.mt.sema.vistas.integration.libreoffice;

import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao.PlanilhaModo;
import com.sun.star.awt.Size;
import com.sun.star.beans.Property;
import com.sun.star.beans.PropertyAttribute;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XIndexAccess;
import com.sun.star.container.XNameContainer;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.sheet.CellFlags;
import com.sun.star.sheet.XCellRangeAddressable;
import com.sun.star.sheet.XCellRangesQuery;
import com.sun.star.sheet.XPrintAreas;
import com.sun.star.sheet.XSheetCellCursor;
import com.sun.star.sheet.XSpreadsheet;
import com.sun.star.sheet.XSpreadsheetDocument;
import com.sun.star.sheet.XUsedAreaCursor;
import com.sun.star.style.XStyleFamiliesSupplier;
import com.sun.star.table.CellRangeAddress;
import com.sun.star.table.XCellRange;
import com.sun.star.uno.UnoRuntime;
import org.jodconverter.core.office.OfficeContext;
import org.jodconverter.local.filter.Filter;
import org.jodconverter.local.filter.FilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prepara a planilha carregada (cópia em memória, só leitura: o arquivo original nunca é alterado) para que nenhuma
 * coluna seja cortada no PDF (docs/06, "Planilhas"). Documentos que não são planilha passam sem mudança.
 *
 * <p>Para cada aba que entra no PDF, um estilo de página próprio (cópia do estilo da aba, para não afetar outras abas
 * que o compartilham):
 *
 * <ul>
 *   <li><b>LARGURA:</b> 1 página de largura e altura livre ({@code ScaleToPagesX = 1}, {@code ScaleToPagesY = 0}),
 *       paisagem quando a área impressa é mais larga que alta. Se a escala estimada ficar abaixo de
 *       {@code CONVERSAO_PLANILHA_ESCALA_MINIMA}, aquela aba vai para uma página única;</li>
 *   <li><b>PAGINA_POR_ABA:</b> uma página do tamanho da área impressa (mais margens), com a aba inteira.</li>
 * </ul>
 *
 * <p><b>Escala estimada</b> = largura útil da página (largura menos margens, já na orientação escolhida) / largura da
 * área impressa (soma das larguras das colunas, propriedade {@code Size} do intervalo, que não conta as ocultas),
 * limitada a 100%. A área impressa é a área de impressão definida no arquivo ou, sem ela, de A1 até o fim da área usada
 * (o Calc imprime a partir de A1).
 *
 * <p>Abas ocultas ficam fora (o Calc não as imprime); com {@code CONVERSAO_ABAS_OCULTAS=true}, tornam-se visíveis e
 * entram. Linhas e colunas ocultas continuam fora, como o usuário as vê. Cada aba começa em página nova (padrão do
 * Calc). Planilha sem nenhuma célula com conteúdo nas abas que entram: {@link PlanilhaVaziaException}.
 */
final class FiltroDePlanilha implements Filter {

    private static final Logger log = LoggerFactory.getLogger(FiltroDePlanilha.class);

    /** Maior lado de página aceito (1/100 mm), abaixo do limite de 200 polegadas do PDF; acima, a aba é reduzida. */
    static final int LADO_MAXIMO = 500_000;
    private static final short CONTEUDO = (short) (CellFlags.VALUE | CellFlags.DATETIME | CellFlags.STRING
            | CellFlags.FORMULA);

    private final PlanilhaModo modo;
    private final boolean abasOcultas;
    private final int escalaMinima;
    private int abasEmPaginaUnica;

    FiltroDePlanilha(PlanilhaModo modo, boolean abasOcultas, int escalaMinima) {
        this.modo = modo;
        this.abasOcultas = abasOcultas;
        this.escalaMinima = escalaMinima;
    }

    /** Abas que caíram para página única pela escala mínima, nesta conversão. */
    int abasEmPaginaUnica() {
        return abasEmPaginaUnica;
    }

    @Override
    public void doFilter(OfficeContext contexto, XComponent documento, FilterChain cadeia) throws Exception {
        XSpreadsheetDocument planilha = UnoRuntime.queryInterface(XSpreadsheetDocument.class, documento);
        if (planilha != null) {
            preparar(documento, planilha);
        }
        cadeia.doFilter(contexto, documento);
    }

    private void preparar(XComponent documento, XSpreadsheetDocument planilha) throws Exception {
        XIndexAccess abas = UnoRuntime.queryInterface(XIndexAccess.class, planilha.getSheets());
        XNameContainer estilos = UnoRuntime.queryInterface(XNameContainer.class,
                UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, documento).getStyleFamilies()
                        .getByName("PageStyles"));
        XMultiServiceFactory fabrica = UnoRuntime.queryInterface(XMultiServiceFactory.class, documento);
        int comConteudo = 0;
        for (int i = 0; i < abas.getCount(); i++) {
            XSpreadsheet aba = UnoRuntime.queryInterface(XSpreadsheet.class, abas.getByIndex(i));
            XPropertySet propriedades = UnoRuntime.queryInterface(XPropertySet.class, aba);
            if (!Boolean.TRUE.equals(propriedades.getPropertyValue("IsVisible"))) {
                if (!abasOcultas) {
                    continue;
                }
                propriedades.setPropertyValue("IsVisible", Boolean.TRUE);
                if (!Boolean.TRUE.equals(propriedades.getPropertyValue("IsVisible"))) {
                    // Estrutura da pasta protegida no arquivo: a aba continua oculta e fora do PDF.
                    log.info("Planilha: aba {} oculta não pôde ser exibida (estrutura protegida).", i + 1);
                    continue;
                }
            }
            if (!temConteudo(aba)) {
                continue; // o Calc não gera página para aba vazia
            }
            comConteudo++;
            Size area = areaImpressa(aba);
            XPropertySet estilo = estiloProprio(estilos, fabrica, propriedades, i);
            if (modo == PlanilhaModo.PAGINA_POR_ABA) {
                paginaUnica(estilo, area);
            } else if (!ajustarALargura(estilo, area)) {
                paginaUnica(estilo, area);
                abasEmPaginaUnica++;
                log.info("Planilha: aba {} em página única (escala abaixo de {}%).", i + 1, escalaMinima);
            }
        }
        if (comConteudo == 0) {
            throw new PlanilhaVaziaException();
        }
    }

    private static boolean temConteudo(XSpreadsheet aba) {
        XCellRangesQuery consulta = UnoRuntime.queryInterface(XCellRangesQuery.class, aba);
        return consulta.queryContentCells(CONTEUDO).getCount() > 0;
    }

    /** Tamanho (1/100 mm) da área impressa: maior largura e maior altura entre as áreas de impressão, ou A1 até o fim da área usada. */
    private static Size areaImpressa(XSpreadsheet aba) throws Exception {
        CellRangeAddress[] areas = UnoRuntime.queryInterface(XPrintAreas.class, aba).getPrintAreas();
        if (areas == null || areas.length == 0) {
            XSheetCellCursor cursor = aba.createCursor();
            UnoRuntime.queryInterface(XUsedAreaCursor.class, cursor).gotoEndOfUsedArea(false);
            CellRangeAddress fim = UnoRuntime.queryInterface(XCellRangeAddressable.class, cursor).getRangeAddress();
            areas = new CellRangeAddress[] {new CellRangeAddress(fim.Sheet, 0, 0, fim.EndColumn, fim.EndRow)};
        }
        Size maior = new Size(1, 1);
        for (CellRangeAddress area : areas) {
            XCellRange intervalo = aba.getCellRangeByPosition(area.StartColumn, area.StartRow, area.EndColumn,
                    area.EndRow);
            Size tamanho = (Size) UnoRuntime.queryInterface(XPropertySet.class, intervalo).getPropertyValue("Size");
            maior = new Size(Math.max(maior.Width, tamanho.Width), Math.max(maior.Height, tamanho.Height));
        }
        return maior;
    }

    /** Novo estilo de página só desta aba, com as propriedades do estilo atual dela (margens, cabeçalho, rodapé...). */
    private static XPropertySet estiloProprio(XNameContainer estilos, XMultiServiceFactory fabrica,
            XPropertySet aba, int indice) throws Exception {
        XPropertySet atual = UnoRuntime.queryInterface(XPropertySet.class,
                estilos.getByName((String) aba.getPropertyValue("PageStyle")));
        String nome = "VistasAba" + (indice + 1);
        for (int sufixo = 2; estilos.hasByName(nome); sufixo++) {
            nome = "VistasAba" + (indice + 1) + "_" + sufixo;
        }
        Object novo = fabrica.createInstance("com.sun.star.style.PageStyle");
        estilos.insertByName(nome, novo);
        XPropertySet estilo = UnoRuntime.queryInterface(XPropertySet.class, novo);
        for (Property propriedade : atual.getPropertySetInfo().getProperties()) {
            if ((propriedade.Attributes & PropertyAttribute.READONLY) != 0) {
                continue;
            }
            try {
                estilo.setPropertyValue(propriedade.Name, atual.getPropertyValue(propriedade.Name));
            } catch (Exception e) {
                // Propriedade que não se copia entre estilos: fica o padrão.
            }
        }
        aba.setPropertyValue("PageStyle", nome);
        return estilo;
    }

    /** Modo LARGURA. Devolve falso quando a escala estimada fica abaixo da mínima (a aba vai para página única). */
    private boolean ajustarALargura(XPropertySet estilo, Size area) throws Exception {
        int largura = (Integer) estilo.getPropertyValue("Width");
        int altura = (Integer) estilo.getPropertyValue("Height");
        boolean paisagem = area.Width > area.Height;
        int ladoMaior = Math.max(largura, altura);
        int ladoMenor = Math.min(largura, altura);
        int larguraDaPagina = paisagem ? ladoMaior : ladoMenor;
        int util = larguraDaPagina - margem(estilo, "LeftMargin") - margem(estilo, "RightMargin");
        double escala = Math.min(1.0, util / (double) Math.max(1, area.Width));
        if (escala * 100 < escalaMinima) {
            return false;
        }
        estilo.setPropertyValue("IsLandscape", paisagem);
        estilo.setPropertyValue("Width", larguraDaPagina);
        estilo.setPropertyValue("Height", paisagem ? ladoMenor : ladoMaior);
        estilo.setPropertyValue("ScaleToPages", (short) 0);
        estilo.setPropertyValue("ScaleToPagesX", (short) 1);
        estilo.setPropertyValue("ScaleToPagesY", (short) 0);
        return true;
    }

    /** Página do tamanho da área impressa mais margens e cabeçalho/rodapé, com a aba inteira nela. */
    private static void paginaUnica(XPropertySet estilo, Size area) throws Exception {
        int largura = area.Width + margem(estilo, "LeftMargin") + margem(estilo, "RightMargin");
        int altura = area.Height + margem(estilo, "TopMargin") + margem(estilo, "BottomMargin")
                + faixa(estilo, "Header") + faixa(estilo, "Footer");
        estilo.setPropertyValue("IsLandscape", largura > altura);
        estilo.setPropertyValue("Width", Math.min(largura, LADO_MAXIMO));
        estilo.setPropertyValue("Height", Math.min(altura, LADO_MAXIMO));
        estilo.setPropertyValue("ScaleToPages", (short) 0);
        estilo.setPropertyValue("ScaleToPagesX", (short) 1);
        estilo.setPropertyValue("ScaleToPagesY", (short) 1);
    }

    private static int margem(XPropertySet estilo, String nome) throws Exception {
        return (Integer) estilo.getPropertyValue(nome);
    }

    /** Altura ocupada pelo cabeçalho ou rodapé (com o espaço até o corpo), 0 quando desligado. */
    private static int faixa(XPropertySet estilo, String qual) throws Exception {
        if (!Boolean.TRUE.equals(estilo.getPropertyValue(qual + "IsOn"))) {
            return 0;
        }
        return (Integer) estilo.getPropertyValue(qual + "Height");
    }

    /** Nenhuma aba que entra no PDF tem célula com conteúdo. */
    static final class PlanilhaVaziaException extends RuntimeException {

        PlanilhaVaziaException() {
            super("planilha sem conteúdo imprimível");
        }
    }
}
