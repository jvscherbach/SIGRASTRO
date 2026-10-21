/** Ilustração de chapadas com curvas de nível do login, portada do protótipo. */
export function cenaLogin(): string {
  const cx = 800, cy = 260, linhas: string[] = [];
  for(let k=1;k<=15;k++){const r0 = 24 + k * 30, pts: string[] = [];
    for(let a=0;a<=96;a++){const t=a/96*Math.PI*2,r=r0*(1+.13*Math.sin(3*t+k*.35)+.07*Math.sin(5*t-k*.5)+.04*Math.cos(7*t+k));pts.push(`${(cx+Math.cos(t)*r*1.35).toFixed(1)},${(cy+Math.sin(t)*r*.9).toFixed(1)}`);}
    linhas.push(`<path d="M${pts.join('L')}Z" fill="none" stroke="${k%4===0?'#79d900':'#d3f8d3'}" stroke-opacity="${k%4===0?.28:.11}" stroke-width="${k%4===0?1.3:1}"/>`);}
  const mata = (y: number, amp: number, cor: string, seed: number) =>{let d=`M0 ${y}`;for(let x=0;x<=1000;x+=40){const h=amp*(.55+.45*Math.sin(x*.05+seed));d+=` q20 ${-h} 40 0`;}return `<path d="${d} V900 H0 Z" fill="${cor}"/>`;};
  const estrato = (x1: number, x2: number, y: number, n: number) => Array.from({ length: n }, (_, i) =>`<path d="M${x1} ${y+i*11} L${x2} ${y+i*11-2}" stroke="#a6c77a" stroke-opacity="${.14-i*.025}" stroke-width="1.5"/>`).join('');
  return `<svg viewBox="0 0 1000 900" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg"><defs>
<linearGradient id="lgS" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1d4010"/><stop offset=".6" stop-color="#132b0a"/><stop offset="1" stop-color="#0b1c06"/></linearGradient>
<radialGradient id="lgL" cx=".78" cy=".28" r=".42"><stop offset="0" stop-color="#9be22e" stop-opacity=".32"/><stop offset=".45" stop-color="#548b0a" stop-opacity=".1"/><stop offset="1" stop-color="#548b0a" stop-opacity="0"/></radialGradient>
<radialGradient id="lgSol" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#e6ffc2"/><stop offset=".55" stop-color="#9be22e"/><stop offset="1" stop-color="#79d900"/></radialGradient>
<linearGradient id="lgM1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a6619"/><stop offset="1" stop-color="#244611"/></linearGradient>
<linearGradient id="lgM2" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a4f14"/><stop offset="1" stop-color="#173209"/></linearGradient>
<linearGradient id="lgF" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff" stop-opacity=".9"/><stop offset="1" stop-color="#d3f8d3" stop-opacity=".05"/></linearGradient></defs>
<rect width="1000" height="900" fill="url(#lgS)"/><rect width="1000" height="900" fill="url(#lgL)"/>
<g>${linhas.join('')}</g>
<circle cx="790" cy="236" r="58" fill="#79d900" opacity=".08"/><circle cx="790" cy="236" r="30" fill="url(#lgSol)" opacity=".9"/>
<path d="M0 546 L96 542 L118 522 L300 514 L322 540 L470 546 L494 510 L718 500 L740 532 L862 538 L884 514 L1000 510 V900 H0 Z" fill="url(#lgM1)" opacity=".8"/>
<path d="M118 522 L300 514 M494 510 L718 500 M884 514 L1000 510" stroke="#79d900" stroke-opacity=".5" stroke-width="2"/>
${estrato(494,718,522,3)}${estrato(118,300,532,2)}
<path d="M0 646 L236 632 L262 594 L572 584 L598 622 L790 630 L816 600 L1000 594 V900 H0 Z" fill="url(#lgM2)"/>
<path d="M236 632 L262 594 L270 632 Z" fill="#355e1a" opacity=".7"/><path d="M572 584 L598 622 L590 590 Z" fill="#0f2407" opacity=".5"/>
<path d="M262 594 L572 584 M816 600 L1000 594" stroke="#79d900" stroke-opacity=".38" stroke-width="2"/>
${estrato(270,566,606,3)}${estrato(822,1000,612,2)}
<path d="M412 589 L420 589 L426 700 L406 700 Z" fill="url(#lgF)"/><ellipse cx="416" cy="700" rx="34" ry="9" fill="#d3f8d3" opacity=".12"/>
${mata(700,26,'#14300a',1)}${mata(760,22,'#0f2407',2.6)}
</svg>`;}
