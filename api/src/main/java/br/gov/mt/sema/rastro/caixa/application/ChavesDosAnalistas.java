package br.gov.mt.sema.rastro.caixa.application;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.application.ConsultarAnalistas;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Quem são os analistas da caixa e a chave opaca de cada um (S11, bloco 5). O sal é sorteado a cada subida do processo: a
 * chave não serve fora desta execução e não se reverte ao CPF. O CPF cadastrado só passa por aqui para virar o
 * identificador da leitura; não é guardado.
 */
@Component
public class ChavesDosAnalistas {

    private static final int TAMANHO_DO_SAL = 32;

    private final ConsultarAnalistas analistas;
    private final byte[] sal;

    @Autowired
    public ChavesDosAnalistas(ConsultarAnalistas analistas) {
        this(analistas, sorteio());
    }

    /** Para teste: sal conhecido. */
    ChavesDosAnalistas(ConsultarAnalistas analistas, byte[] sal) {
        this.analistas = analistas;
        this.sal = sal.clone();
    }

    private static byte[] sorteio() {
        byte[] sal = new byte[TAMANHO_DO_SAL];
        new SecureRandom().nextBytes(sal);
        return sal;
    }

    /** Os analistas ativos com vínculo, prontos para a leitura da posse. */
    public List<AnalistaParaLeitura> analistas() {
        return analistas.vinculados().stream().map(this::paraLeitura).flatMap(Optional::stream).toList();
    }

    /** A chave do analista do login; vazia se o usuário não é Analista ativo vinculado a um servidor (sem CPF). */
    public Optional<ChaveDoResponsavel> chaveDoLogin(String login) {
        return analistas.doLogin(login).flatMap(this::paraLeitura).map(AnalistaParaLeitura::chave);
    }

    private Optional<AnalistaParaLeitura> paraLeitura(ConsultarAnalistas.Vinculo vinculo) {
        return IdentificadorDoResponsavel.de(vinculo.identificadorProtegido())
                .map(id -> new AnalistaParaLeitura(ChaveDoResponsavel.de(id, sal), id));
    }
}
