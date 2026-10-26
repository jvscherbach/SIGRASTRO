import { agoraEmCuiaba, minutosAte } from './formato';

describe('relógio de Cuiabá', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-21T15:00:00Z')); // 11:00 em Cuiabá (UTC-4)
  });

  afterEach(() => vi.useRealTimers());

  it('minutosAte conta pelo relógio de parede de Cuiabá, qualquer que seja o fuso do navegador', () => {
    expect(agoraEmCuiaba().getHours()).toBe(11);
    expect(minutosAte('2026-10-21T11:30:00')).toBe(30);
    expect(minutosAte('2026-10-21T11:00:01')).toBe(1);
  });

  it('nunca negativo e nulo quando não há data válida', () => {
    expect(minutosAte('2026-10-21T10:00:00')).toBe(0);
    expect(minutosAte(null)).toBeNull();
    expect(minutosAte('lixo')).toBeNull();
  });
});
