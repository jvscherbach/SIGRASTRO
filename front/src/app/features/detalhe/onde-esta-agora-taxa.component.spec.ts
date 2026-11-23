import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { DetalheApiService } from './detalhe-api.service';
import { OndeEstaAgoraComponent } from './onde-esta-agora.component';
import { PosicaoDoRequerimento } from './posicao.models';

/** S9, bloco 9: o aviso "Taxa vencida" aparece com a situação do Licenciamento, sem trocar o texto dela. */
function posicao(sobre: Partial<PosicaoDoRequerimento> = {}): PosicaoDoRequerimento {
  return {
    idRequerimento: 9015,
    numeroRequerimento: '9015/2026',
    numeroProcesso: '9900/2026',
    situacaoLicenciamento: { codigo: 'AGUARDANDO_PAGAMENTO_DE_TAXAS', descricao: 'Aguardando pagamento de taxas' },
    modelo: 'Outorga de recursos hídricos',
    estado: 'ENCERRADO',
    etapaAtual: null,
    emParalelo: [],
    complementacao: null,
    multiInstancia: null,
    avisos: [
      {
        codigo: 'FLUXO_ENCERRADO_SEM_STATUS',
        texto: 'O fluxo foi encerrado por taxa não paga no prazo; o SIGA ainda mostra "aguardando pagamento".',
        gravidade: 'ATENCAO',
      },
      {
        codigo: 'TAXA_VENCIDA',
        texto: 'Taxa vencida em 19/03/2026. O SIGA ainda não marca a taxa como vencida; o Rastro compara a data de vencimento com hoje.',
        gravidade: 'ATENCAO',
      },
    ],
    final: { rotulo: 'Encerrado por taxa não paga no prazo', encerradoEm: '2026-03-19T08:00:00' },
    ...sobre,
  };
}

describe('OndeEstaAgoraComponent: taxa vencida (S9, bloco 9)', () => {
  function criar(p: PosicaoDoRequerimento): HTMLElement {
    const api = { posicao: vi.fn(() => of(p)), parametrosDeExibicao: vi.fn(() => of({ posicoesSimultaneas: 4 })) };
    TestBed.configureTestingModule({
      imports: [OndeEstaAgoraComponent],
      providers: [{ provide: DetalheApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(OndeEstaAgoraComponent);
    fixture.componentRef.setInput('idRequerimento', 9015);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const texto = (raiz: HTMLElement) => raiz.textContent!.replace(/\s+/g, ' ');

  it('FIN15: mostra a situação do Licenciamento como está e os dois avisos, sem trocar o final', () => {
    const raiz = criar(posicao());

    expect(texto(raiz.querySelector('.onde-situacao')!)).toContain('Situação no Licenciamento: Aguardando pagamento de taxas');
    const avisos = Array.from(raiz.querySelectorAll('.onde-avisos li')).map((li) => li.textContent!.replace(/\s+/g, ' '));
    expect(avisos).toHaveLength(2);
    expect(avisos[1]).toContain('Taxa vencida em 19/03/2026');
    expect(avisos[1]).toContain('O SIGA ainda não marca a taxa como vencida');
    expect(texto(raiz)).toContain('Encerrado por taxa não paga no prazo');
  });

  it('sem o aviso de taxa vencida a linha da situação não aparece (a tela segue como antes)', () => {
    const raiz = criar(posicao({ avisos: [] }));

    expect(raiz.querySelector('.onde-situacao')).toBeNull();
    expect(texto(raiz)).not.toContain('Situação no Licenciamento');
  });
});
