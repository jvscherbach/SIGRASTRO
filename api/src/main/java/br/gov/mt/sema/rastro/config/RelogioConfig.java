package br.gov.mt.sema.rastro.config;

import java.time.Clock;

import br.gov.mt.sema.rastro.shared.api.error.ProblemDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelogioConfig {

    @Bean
    Clock relogio() {
        return Clock.system(ProblemDetails.FUSO);
    }
}
