/** Ilustração da Cachoeira Véu de Noiva (Chapada dos Guimarães), portada do protótipo. */
export function cachoeira(id: string): string {
  return `<svg viewBox="0 0 1200 420" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg"><defs>
<linearGradient id="vsky${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#b9ccd1"/><stop offset=".55" stop-color="#ecdcbc"/><stop offset="1" stop-color="#f4e6c8"/></linearGradient>
<radialGradient id="vsun${id}" cx=".7" cy=".12" r=".45"><stop offset="0" stop-color="#fff6dc" stop-opacity=".9"/><stop offset="1" stop-color="#fff6dc" stop-opacity="0"/></radialGradient>
<linearGradient id="vcl${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f3d9b0"/><stop offset=".45" stop-color="#e2ae7c"/><stop offset="1" stop-color="#b9774c"/></linearGradient>
<linearGradient id="vcr${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d89a66"/><stop offset=".5" stop-color="#ad653f"/><stop offset="1" stop-color="#6f3b25"/></linearGradient>
<radialGradient id="vcave${id}" cx=".5" cy=".75" r=".6"><stop offset="0" stop-color="#22170f" stop-opacity=".95"/><stop offset=".7" stop-color="#3a2618" stop-opacity=".7"/><stop offset="1" stop-color="#4a2f1e" stop-opacity="0"/></radialGradient>
<linearGradient id="vfall${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff" stop-opacity=".98"/><stop offset=".7" stop-color="#f4f8f6" stop-opacity=".9"/><stop offset="1" stop-color="#ffffff" stop-opacity=".35"/></linearGradient>
<linearGradient id="vfor${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4d7535"/><stop offset="1" stop-color="#1f3519"/></linearGradient>
<radialGradient id="vmist${id}" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#ffffff" stop-opacity=".75"/><stop offset="1" stop-color="#ffffff" stop-opacity="0"/></radialGradient></defs>
<rect width="1200" height="420" fill="url(#vsky${id})"/><rect width="1200" height="420" fill="url(#vsun${id})"/>
<g fill="#fff6e6" opacity=".55"><ellipse cx="820" cy="30" rx="150" ry="10"/><ellipse cx="1060" cy="46" rx="120" ry="8"/><ellipse cx="620" cy="52" rx="110" ry="7"/></g>
<path d="M0 250 C160 236 360 246 700 230 L700 420 L0 420 Z" fill="#9db277"/>
<g transform="translate(160 0)"><path d="M-170 112 C40 104 240 100 380 96 C500 84 640 88 760 80 S1000 70 1200 74 L1200 122 L380 132 C240 136 40 140 -170 146 Z" fill="#7f9c4c"/>
<path d="M640 112 C760 102 900 98 1000 100 S1150 94 1200 96 L1200 340 L640 340 Z" fill="url(#vcr${id})"/>
<g stroke="#f2bd8a" stroke-opacity=".35" stroke-width="3" fill="none"><path d="M700 150 C820 144 980 142 1200 136"/><path d="M700 196 C840 188 1000 186 1200 180"/></g>
<g stroke="#6a361f" stroke-opacity=".3" stroke-width="2" fill="none"><path d="M690 170 C840 164 1000 160 1200 156"/><path d="M690 222 C840 216 1000 212 1200 206"/><path d="M880 112V214M960 108V196M1040 106V222M1120 102V200"/></g>
<ellipse cx="900" cy="318" rx="230" ry="112" fill="url(#vcave${id})"/>
<path d="M700 246 C790 214 990 206 1110 238 L1110 340 L700 340 Z" fill="#3a2316" opacity=".42"/>
<path d="M-170 142 C60 136 280 132 455 128 C540 114 630 110 712 106 L730 160 L722 248 L706 360 L-170 360 Z" fill="url(#vcl${id})"/>
<g stroke="#b8774c" stroke-opacity=".38" stroke-width="2" fill="none"><path d="M-170 180 C120 174 330 170 462 168 C560 160 650 158 724 154"/><path d="M-170 224 C120 218 330 214 462 212 C560 204 650 202 724 198"/><path d="M-170 274 C120 268 330 264 462 262 C560 254 650 252 716 250"/></g>
<g stroke="#fff3df" stroke-opacity=".45" stroke-width="3" fill="none"><path d="M-170 158 C120 152 330 148 470 146 C560 138 640 134 718 130"/><path d="M-170 248 C120 242 330 238 470 236 C560 228 640 226 720 224"/></g>
<g fill="#6f9440" opacity=".85"><ellipse cx="80" cy="200" rx="20" ry="8"/><ellipse cx="260" cy="250" rx="24" ry="9"/><ellipse cx="380" cy="300" rx="22" ry="9"/><ellipse cx="520" cy="190" rx="18" ry="8"/><ellipse cx="600" cy="232" rx="22" ry="9"/><ellipse cx="560" cy="282" rx="26" ry="10"/><ellipse cx="1150" cy="168" rx="20" ry="8"/><ellipse cx="1060" cy="250" rx="16" ry="7"/></g>
<g fill="#5d8236"><circle cx="-120" cy="138" r="15"/><circle cx="-40" cy="134" r="13"/><circle cx="60" cy="132" r="16"/><circle cx="160" cy="128" r="13"/><circle cx="250" cy="126" r="16"/><circle cx="340" cy="122" r="13"/><circle cx="410" cy="122" r="15"/><circle cx="470" cy="122" r="14"/><circle cx="500" cy="116" r="17"/><circle cx="540" cy="112" r="13"/><circle cx="600" cy="108" r="16"/><circle cx="650" cy="104" r="12"/><circle cx="700" cy="100" r="15"/><circle cx="790" cy="98" r="14"/><circle cx="850" cy="96" r="17"/><circle cx="930" cy="94" r="13"/><circle cx="990" cy="94" r="16"/><circle cx="1060" cy="90" r="14"/><circle cx="1130" cy="90" r="17"/><circle cx="1185" cy="88" r="14"/></g>
<g fill="#86ad4c"><circle cx="512" cy="110" r="8"/><circle cx="608" cy="100" r="9"/><circle cx="858" cy="88" r="9"/><circle cx="1000" cy="86" r="8"/><circle cx="1140" cy="82" r="9"/></g>
<path d="M736 100 L752 100 L756 180 L762 262 L768 336 L738 336 L736 262 L734 180 Z" fill="url(#vfall${id})"/>
<path d="M742 104 L744 336" stroke="#ffffff" stroke-width="2" opacity=".9"/><path d="M749 110 C752 200 756 270 760 334" stroke="#d9e8e4" stroke-width="1.5" fill="none" opacity=".8"/>
<ellipse cx="752" cy="336" rx="70" ry="24" fill="url(#vmist${id})"/>
<path d="M-170 420 L-170 344 C100 332 300 340 440 330 C520 312 600 322 680 304 S820 300 900 312 S1080 300 1200 296 L1200 420 Z" fill="url(#vfor${id})"/>
<g fill="#2e4a25"><circle cx="-100" cy="340" r="18"/><circle cx="0" cy="336" r="20"/><circle cx="110" cy="334" r="18"/><circle cx="220" cy="332" r="20"/><circle cx="330" cy="330" r="18"/><circle cx="410" cy="328" r="16"/><circle cx="470" cy="326" r="16"/><circle cx="520" cy="318" r="20"/><circle cx="590" cy="316" r="18"/><circle cx="660" cy="306" r="20"/><circle cx="820" cy="304" r="22"/><circle cx="890" cy="310" r="18"/><circle cx="960" cy="302" r="21"/><circle cx="1040" cy="300" r="19"/><circle cx="1110" cy="296" r="22"/><circle cx="1180" cy="294" r="19"/></g>
<g fill="#4f7a35"><circle cx="500" cy="346" r="22"/><circle cx="570" cy="342" r="26"/><circle cx="640" cy="336" r="22"/><circle cx="710" cy="350" r="26"/><circle cx="790" cy="340" r="24"/><circle cx="870" cy="336" r="26"/><circle cx="950" cy="344" r="22"/><circle cx="1020" cy="334" r="27"/><circle cx="1100" cy="340" r="24"/><circle cx="1170" cy="332" r="26"/></g>
<g fill="#79d900" opacity=".28"><circle cx="560" cy="330" r="10"/><circle cx="700" cy="338" r="11"/><circle cx="860" cy="324" r="10"/><circle cx="1010" cy="322" r="12"/><circle cx="1160" cy="320" r="10"/></g>
</g>
<path d="M0 360 C160 344 330 362 500 352 S760 364 900 356 S1080 350 1200 346 L1200 420 L0 420 Z" fill="#3d6a2a"/>
<g fill="#2b4b20"><circle cx="60" cy="372" r="24"/><circle cx="160" cy="366" r="28"/><circle cx="270" cy="374" r="24"/><circle cx="390" cy="366" r="28"/><circle cx="520" cy="372" r="26"/><circle cx="660" cy="378" r="28"/><circle cx="800" cy="370" r="26"/><circle cx="940" cy="374" r="28"/><circle cx="1080" cy="366" r="26"/><circle cx="1190" cy="372" r="24"/></g>
<path d="M0 392 C220 382 460 396 700 388 S1000 394 1200 386 L1200 420 L0 420 Z" fill="#1d3317"/>
</svg>`;
}
