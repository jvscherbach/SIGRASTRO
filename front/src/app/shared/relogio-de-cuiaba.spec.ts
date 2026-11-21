import { agoraEmCuiaba, minutosAte, tempoDesde } from './formato';

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

  it('tempoDesde (S7): "há N" pelo relógio de Cuiabá, data sem fuso ou com fuso', () => {
    expect(tempoDesde('2026-10-21T10:59:40')).toBe('agora há pouco');
    expect(tempoDesde('2026-10-21T10:48:00')).toBe('há 12 min');
    expect(tempoDesde('2026-10-21T06:00:00')).toBe('há 5 h');
    expect(tempoDesde('2026-10-20T11:00:00')).toBe('há 1 dia');
    expect(tempoDesde('2026-10-18T09:00:00')).toBe('há 3 dias');
    expect(tempoDesde('2026-10-21T14:00:00Z')).toBe('há 1 h'); // instante: 10:00 em Cuiabá
    expect(tempoDesde('2026-10-21T12:00:00')).toBe('agora há pouco'); // futuro não vira negativo
    expect(tempoDesde(null)).toBe('');
    expect(tempoDesde('lixo')).toBe('');
  });
});
