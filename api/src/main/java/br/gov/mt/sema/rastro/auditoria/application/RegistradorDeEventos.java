package br.gov.mt.sema.rastro.auditoria.application;

import br.gov.mt.sema.rastro.auditoria.application.port.RepositorioDeEventos;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class RegistradorDeEventos implements RegistrarEvento {

    private static final Logger log = LoggerFactory.getLogger(RegistradorDeEventos.class);

    private final RepositorioDeEventos repositorio;

    RegistradorDeEventos(RepositorioDeEventos repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void registrar(NovoEvento evento) {
        try {
            repositorio.incluir(evento);
        } catch (RuntimeException e) {
            // Só a classe da exceção: a mensagem pode carregar dado do evento.
            log.error("Falha ao registrar evento de auditoria: {}", e.getClass().getSimpleName());
        }
    }
}
