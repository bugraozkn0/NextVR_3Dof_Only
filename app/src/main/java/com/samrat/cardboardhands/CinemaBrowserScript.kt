package com.samrat.cardboardhands

/** Native-to-page queries only. No privileged JavascriptInterface is exposed to websites.
 * Cross-origin/DRM players are deliberately reported unavailable.
 */
internal object CinemaBrowserScript {
    val SETUP = """
(() => {
  if (window.__nxCinema) return;
  let seq = 0; const ids = new WeakMap();
  const video = () => Array.from(document.querySelectorAll('video')).sort((a,b) =>
    (b.clientWidth*b.clientHeight) - (a.clientWidth*a.clientHeight))[0];
  const state = () => {
    const v = video();
    return { controllable: !!v, fullscreen: !!document.fullscreenElement,
      playback: !v ? 'unknown' : v.ended ? 'ended' : v.paused ? 'paused' : v.readyState < 3 ? 'buffering' : 'playing' };
  };
  const hit = (u,v) => {
    let el = document.elementFromPoint(u*innerWidth,v*innerHeight);
    el = el && el.closest('a[href],button,input,textarea,select,summary,[role=button],[role=link],[role=menuitem],[role=tab],[contenteditable=true],video');
    if (!el) return null;
    if (!ids.has(el)) ids.set(el, ++seq);
    const r = el.getBoundingClientRect();
    return {id:String(ids.get(el)), label:(el.getAttribute('aria-label') || el.innerText || el.tagName).slice(0,70),
      enabled:!el.disabled && el.getAttribute('aria-disabled') !== 'true',
      l:r.left/innerWidth,t:r.top/innerHeight,r:r.right/innerWidth,b:r.bottom/innerHeight};
  };
  const scroll = (direction, pixels=0, u=.55, v=.55) => {
    // Select the scrollable ancestor under the hand or gaze rather than guessing
    // the document body; mobile YouTube and streaming sites use nested containers.
    const x=Math.min(.98,Math.max(.02,u))*innerWidth;
    const y=Math.min(.98,Math.max(.02,v))*innerHeight;
    let el = document.elementFromPoint(x,y);
    while (el && el !== document.body && el !== document.documentElement) {
      if (el.scrollHeight > el.clientHeight+20 && /auto|scroll|overlay/.test(getComputedStyle(el).overflowY)) break;
      el=el.parentElement;
    }
    const target = el && el !== document.body && el !== document.documentElement
      ? el : (document.scrollingElement || document.documentElement);
    if (!target) return;
    if (pixels) target.scrollBy({top:Math.max(-650,Math.min(650,pixels)),behavior:'instant'});
    else target.scrollBy({top:innerHeight*.65*direction,behavior:'smooth'});
  };
  const action = command => {
    const v=video();
    if (command==='scrollUp' || command==='scrollDown') {scroll(command==='scrollUp'?-1:1);return true;}
    if (command==='fullscreen') {
      if (document.fullscreenElement) {document.exitFullscreen().catch(()=>{});return true;}
      if (!v || !v.requestFullscreen) return false;
      v.requestFullscreen().catch(()=>{});return true;
    }
    if (!v) return false;
    if (command==='play') {if(v.paused) v.play().catch(()=>{});else v.pause();}
    else if(command==='seekBack'||command==='seekForward') {
      if(!Number.isFinite(v.duration)||!v.seekable.length) return false;
      v.currentTime=Math.max(v.seekable.start(0),Math.min(v.seekable.end(v.seekable.length-1),v.currentTime+(command==='seekBack'?-10:10)));
    } else return false;
    return true;
  };
  const fullscreenControl = () => {
    const candidates=Array.from(document.querySelectorAll('button,[role=button]'));
    const el=candidates.find(b => /full.?screen|tam ekran|plein .cran/i.test(b.getAttribute('aria-label')||b.title||'') && b.getBoundingClientRect().width>5 && !b.disabled);
    if(!el) return null;
    const r=el.getBoundingClientRect();return {u:(r.left+r.width/2)/innerWidth,v:(r.top+r.height/2)/innerHeight};
  };
  window.__nxCinema={state,hit,action,fullscreenControl,
    scrollPixels:(pixels,u,v)=>scroll(Math.sign(pixels),pixels,u,v)};
})();
""".trimIndent()
    fun query(u: Float, v: Float) = SETUP + "\nJSON.stringify({input:!!document.activeElement && document.activeElement.matches(\'input,textarea,[contenteditable=true]\'),media:window.__nxCinema.state(),hit:window.__nxCinema.hit($u,$v)})"
    fun action(action: String) = "window.__nxCinema && window.__nxCinema.action(" + org.json.JSONObject.quote(action) + ")"
}
