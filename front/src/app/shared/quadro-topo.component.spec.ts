import { TestBed } from '@angular/core/testing';

import { QuadroTopoComponent } from './quadro-topo.component';

/** R2, bloco 7: faixa do topo com a paisagem do Cerrado e o cartão de título. */
describe('QuadroTopoComponent', () => {
  function criar() {
    const fixture = TestBed.createComponent(QuadroTopoComponent);
    fixture.componentRef.setInput('titulo', 'Consulta');
    fixture.componentRef.setInput('descricao', 'Busque por processo ou requerimento.');
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renderiza a paisagem do Cerrado como imagem decorativa', () => {
    const img = criar().querySelector('.dash-hero img.dash-scene');
    expect(img?.getAttribute('src')).toBe('assets/img/cerrado.svg');
    expect(img?.getAttribute('alt')).toBe('');
  });

  it('renderiza o cartão com o título e o subtítulo informados', () => {
    const cartao = criar().querySelector('.dash-headline')!;
    expect(cartao.querySelector('h1')?.textContent).toBe('Consulta');
    expect(cartao.querySelector('p')?.textContent).toBe('Busque por processo ou requerimento.');
  });

  it('não traz etiqueta de local nem a ilustração antiga', () => {
    const el = criar();
    expect(el.querySelector('.dash-place')).toBeNull();
    expect(el.querySelector('svg')).toBeNull();
    expect(el.textContent).not.toContain('Cachoeira');
  });
});
