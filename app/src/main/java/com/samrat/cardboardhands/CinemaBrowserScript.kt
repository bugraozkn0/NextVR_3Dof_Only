package com.samrat.cardboardhands

/** Lightweight, no-network controls added inside regular WebView pages.
 *  The bar lives in page space, but its clicks are still delivered by the VR gaze cursor.
 *  The script runs on every navigation and is idempotent per document.
 */
internal object CinemaBrowserScript {
    val SETUP = """
(() => {
  if (window.__nxCinemaInstalled) return;
  window.__nxCinemaInstalled = true;
  const scrollByPage = (direction) => {
    let el = document.elementFromPoint(window.innerWidth * .55, window.innerHeight * .55);
    while (el && el !== document.body && el !== document.documentElement) {
      const st = window.getComputedStyle(el);
      if (el.scrollHeight > el.clientHeight + 20 && /auto|scroll|overlay/.test(st.overflowY)) break;
      el = el.parentElement;
    }
    const delta = Math.round(window.innerHeight * .7) * (direction > 0 ? 1 : -1);
    const target = el && el !== document.body && el !== document.documentElement
        ? el : (document.scrollingElement || document.documentElement);
    try { target.scrollBy({top: delta, behavior: 'smooth'}); }
    catch (e) { target.scrollTop += delta; }
  };
  window.__nxCinemaScroll = scrollByPage;
  const isPlaying = () => Array.from(document.querySelectorAll('video')).some(v =>
    !v.paused && !v.ended);
  const report = () => {
    try { window.PhoneXR && window.PhoneXR.cinemaMedia(isPlaying()); } catch (e) {}
    const bar = document.getElementById('__nxCinemaScrollBar');
    if (bar) bar.style.display = isPlaying() || !!document.fullscreenElement ? 'none' : 'flex';
  };
  const attachBar = () => {
    if (!document.body || document.getElementById('__nxCinemaScrollBar')) return;
    const bar = document.createElement('div');
    bar.id = '__nxCinemaScrollBar';
    bar.style.cssText = 'position:fixed!important;top:39%!important;right:1%!important;'+
       'display:flex;flex-direction:column;gap:1vh;z-index:2147483647!important;'+
       'pointer-events:auto;user-select:none;touch-action:manipulation;';
    const make = (glyph, direction) => {
      const b = document.createElement('button');
      b.type = 'button'; b.textContent = glyph; b.setAttribute('aria-label', direction < 0 ? 'Scroll up' : 'Scroll down');
      b.style.cssText = 'all:initial;display:flex!important;align-items:center;'+
        'justify-content:center;width:8vw;height:10vh;border:1px solid #94a3b8;'+
        'border-radius:16px;background:rgba(24,32,48,.85);color:white;'+
        'font:700 30px/1 sans-serif;box-shadow:0 3px 18px #0009;cursor:pointer;';
      b.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); scrollByPage(direction); });
      return b;
    };
    bar.appendChild(make('▲', -1)); bar.appendChild(make('▼', 1));
    document.body.appendChild(bar);
    report();
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', attachBar, {once:true});
  else attachBar();
  document.addEventListener('play', report, true);
  document.addEventListener('pause', report, true);
  document.addEventListener('ended', report, true);
  document.addEventListener('fullscreenchange', report, true);
  report();
})();
""".trimIndent()
}
