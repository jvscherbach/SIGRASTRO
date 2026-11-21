import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';

import { ConsultaApiService } from '../consulta/consulta-api.service';
import { ResultadoBusca } from '../consulta/consulta.models';
import { DetalheApiService } from './detalhe-api.service';
import { PosicaoDoRequerimento } from './posicao.models';
import { RequerimentosDoProcessoComponent } from './requerimentos-do-processo.component';

function resultado(id: number): ResultadoBusca {
  return {
    id,
    identificacao: { rotulo: 'Processo', numero: '7900/2022', protocolado: true },
    numeroRequerimento: `${id}/2026`,
    numeroProcesso: '7900/2022',
    interessado: { nome: 'Interessado Fictício S7', cpfCnpj: '12345678000190' },
    tipoProcesso: 'Outorga de recursos hídricos (FICTÍCIO)',
    situacao: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    abertura: '2026-03-01T09:00:00',
  };
}

function posicao(id: number, sobre: Partial<PosicaoDoRequerimento>): PosicaoDoRequerimento {
  return {
    idRequerimento: id,
    numeroRequerimento: `${id}/2026`,
    numeroProcesso: '7900/2022',
    situacaoLicenciamento: null,
    modelo: 'Outorga de recursos hídricos',
    estado: 'ATIVO',
    etapaAtual: null,
    emParalelo: [],
    complementacao: null,
    multiInstancia: null,
    avisos: [],
    final: null,
    ...sobre,
  };
}

describe('RequerimentosDoProcessoComponent', () => {
  let consulta: { buscar: ReturnType<typeof vi.fn> };
  let detalhe: { posicao: ReturnType<typeof vi.fn>; parametrosDeExibicao: ReturnType<typeof vi.fn> };

  function criar(): { fixture: ComponentFixture<RequerimentosDoProcessoComponent>; raiz: HTMLElement } {
    TestBed.configureTestingModule({
      imports: [RequerimentosDoProcessoComponent],
      providers: [
        provideRouter([]),
        { provide: ConsultaApiService, useValue: consulta },
        { provide: DetalheApiService, useValue: detalhe },
      ],
    });
    const fixture = TestBed.createComponent(RequerimentosDoProcessoComponent);
    fixture.componentRef.setInput('numeroProcesso', '7900/2022');
    fixture.componentRef.setInput('idAtual', 7007);
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    consulta = { buscar: vi.fn() };
    detalhe = { posicao: vi.fn(), parametrosDeExibicao: vi.fn(() => NEVER) };
  });

  it('processo com 3 outros requerimentos (um com falha, um sem instância, um encerrado): cada um carrega e falha sozinho', () => {
    consulta.buscar.mockReturnValue(
      of({ conteudo: [resultado(7007), resultado(7015), resultado(7021), resultado(7030)], pagina: 0, tamanho: 100, totalElementos: 4, totalPaginas: 1 }),
    );
    detalhe.posicao.mockImplementation((id: number) => {
      if (id === 7015) return of(posicao(id, { estado: 'ENCERRADO', final: { rotulo: 'Concluído', encerradoEm: '2026-03-16T09:30:00' } }));
      if (id === 7021) return of(posicao(id, { estado: 'SEM_INSTANCIA' }));
      return throwError(() => new HttpErrorResponse({ status: 500 }));
    });
    const { raiz } = criar();

    const itens = [...raiz.querySelectorAll('.rdp-lista > li')];
    expect(itens).toHaveLength(3); // o atual (7007) não se repete
    expect(raiz.querySelector('h2')!.textContent).toContain('Outros requerimentos deste processo');

    const [encerrado, semInstancia, falhou] = itens;
    expect(encerrado.querySelector('a')!.textContent).toContain('Requerimento 7015/2026');
    expect(encerrado.textContent).toContain('Concluído');
    expect(encerrado.querySelector('.ponto')!.getAttribute('aria-label')).toBe('Estado: Encerrado');
    expect(semInstancia.textContent).toContain('Histórico detalhado indisponível');
    expect(semInstancia.querySelector('.ponto')!.getAttribute('aria-label')).toBe('Estado: Sem histórico detalhado');
    expect(falhou.textContent).toContain('Não foi possível consultar');
    expect(falhou.querySelector('.ponto')!.getAttribute('aria-label')).toBe('Estado: Indisponível');
    expect(detalhe.posicao).toHaveBeenCalledTimes(3);
  });

  it('sem outros requerimentos, a seção não aparece', () => {
    consulta.buscar.mockReturnValue(of({ conteudo: [resultado(7007)], pagina: 0, tamanho: 100, totalElementos: 1, totalPaginas: 1 }));
    const { raiz } = criar();

    expect(raiz.querySelector('section')).toBeNull();
    expect(detalhe.posicao).not.toHaveBeenCalled();
  });

  it('403 ou 404 na lista: nada a mostrar; outro erro: aviso curto, sem derrubar o detalhe', () => {
    consulta.buscar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    expect(criar().raiz.querySelector('section')).toBeNull();

    TestBed.resetTestingModule();
    consulta.buscar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const { raiz } = criar();
    expect(raiz.textContent).toContain('Não foi possível listar os outros requerimentos deste processo.');
  });
});
