package br.gov.mt.sema.rastro.configuracao.application;

import java.util.function.BooleanSupplier;

import br.gov.mt.sema.rastro.acesso.application.AdministrarUsuarios;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Estado do serviço para quem já está autenticado (S6, bloco 7): no ar e em que modo. O servidor MCP do Rastro só opera
 * com o serviço em <b>demonstração</b>, que exige as duas coisas: {@code RASTRO_MODO=demonstracao} (ligado pelo
 * {@code compose.verificacao.yaml}) <b>e</b> nenhum sistema externo configurado (SIGA, API de arquivos e SCORP vazios).
 * Só a variável não basta: com um sistema real configurado o modo é {@code padrao}. Nada de URL, host ou credencial sai
 * daqui: só se cada sistema está configurado (sim/não).
 */
@Service
public class ConsultarEstadoDoServico {

    public static final String DEMONSTRACAO = "demonstracao";
    public static final String PADRAO = "padrao";

    private final String modoPedido;
    private final boolean sigaConfigurado;
    private final boolean arquivosConfigurados;
    private final boolean scorpConfigurado;

    private final BooleanSupplier haAdministrador;

    @Autowired
    public ConsultarEstadoDoServico(@Value("${rastro.modo:padrao}") String modoPedido,
            @Value("${rastro.datasource.siga.url:}") String sigaUrl,
            @Value("${rastro.arquivos.url:}") String arquivosUrl,
            @Value("${rastro.scorp.url:}") String scorpUrl, AdministrarUsuarios usuarios) {
        this(modoPedido, sigaUrl, arquivosUrl, scorpUrl, usuarios::haAdministradorAtivo);
    }

    /** Sem a conferência de administrador (assume que existe). */
    public ConsultarEstadoDoServico(String modoPedido, String sigaUrl, String arquivosUrl, String scorpUrl) {
        this(modoPedido, sigaUrl, arquivosUrl, scorpUrl, () -> true);
    }

    private ConsultarEstadoDoServico(String modoPedido, String sigaUrl, String arquivosUrl, String scorpUrl,
            BooleanSupplier haAdministrador) {
        this.modoPedido = modoPedido == null ? "" : modoPedido.trim().toLowerCase(java.util.Locale.ROOT);
        this.sigaConfigurado = preenchido(sigaUrl);
        this.arquivosConfigurados = preenchido(arquivosUrl);
        this.scorpConfigurado = preenchido(scorpUrl);
        this.haAdministrador = haAdministrador;
    }

    /**
     * @param sistemasExternos algum sistema externo (SIGA, arquivos, SCORP) está configurado
     * @param semAdministrador "sem administrador cadastrado": nenhum Administrador ativo existe (L1, bloco 2)
     */
    public record EstadoDoServico(String status, String modo, boolean sistemasExternos, boolean semAdministrador) {
    }

    public EstadoDoServico consultar() {
        boolean externos = sigaConfigurado || arquivosConfigurados || scorpConfigurado;
        String modo = DEMONSTRACAO.equals(modoPedido) && !externos ? DEMONSTRACAO : PADRAO;
        return new EstadoDoServico("UP", modo, externos, !haAdministrador.getAsBoolean());
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
