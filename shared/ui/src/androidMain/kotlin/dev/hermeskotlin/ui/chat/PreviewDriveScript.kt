package dev.hermeskotlin.ui.chat

/**
 * The page-side half of `preview.act` (the agent's `drive_preview`): an inventory of what can be clicked or typed
 * into, each with a legible ref (`btn-sign-in`), and the actions on those refs. It runs only in the preview's own
 * WebView, which has no bridge to the app, and answers to the app only through the value `evaluateJavascript`
 * hands back. [run] does one action and returns at once; [delta] then says what changed since the last inventory
 * (the first call, or `full`, gives the whole inventory), as Desktop's does, so the agent needn't re-read the page.
 * Kept free of dollar signs: this is a raw Kotlin string.
 */
internal val PREVIEW_DRIVE_SCRIPT = """
(function () {
  if (window.__hd && window.__hd.v === 1) return 'ready';
  var hd = window.__hd = { v: 1, refs: {}, names: new Map(), last: null, lastUrl: null, used: {} };
  var SELECTOR = 'a[href],button,input:not([type=hidden]),select,textarea,summary,[role=button],[role=link],[role=checkbox],' +
    '[role=radio],[role=tab],[role=menuitem],[role=switch],[role=option],[contenteditable=""],[contenteditable=true],[onclick]';

  function shown(el) {
    var r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    var cs = getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none';
  }
  function kind(el) {
    var tag = el.tagName.toLowerCase();
    var role = el.getAttribute('role');
    if (tag === 'a' || role === 'link') return 'link';
    if (tag === 'select') return 'select';
    if (tag === 'textarea' || el.isContentEditable) return 'textbox';
    if (tag === 'input') {
      var t = (el.getAttribute('type') || 'text').toLowerCase();
      if (t === 'checkbox') return 'checkbox';
      if (t === 'radio') return 'radio';
      if (t === 'submit' || t === 'button' || t === 'reset' || t === 'image') return 'button';
      return 'textbox';
    }
    if (role === 'checkbox' || role === 'switch') return 'checkbox';
    if (role === 'radio') return 'radio';
    if (role === 'tab') return 'tab';
    return 'button';
  }
  var PREFIX = { link: 'lnk', select: 'sel', textbox: 'inp', checkbox: 'chk', radio: 'rad', tab: 'tab', button: 'btn' };
  function clip(s, n) { s = (s || '').replace(/\s+/g, ' ').trim(); return s.length > n ? s.slice(0, n - 1) + '…' : s; }
  function label(el) {
    var l = el.getAttribute('aria-label');
    if (!l && el.labels && el.labels.length) {
      // The label's own words: not the options of a select or the value of an input inside it.
      var copy = el.labels[0].cloneNode(true);
      var inner = copy.querySelectorAll('select,input,textarea,option');
      for (var i = 0; i < inner.length; i++) inner[i].remove();
      l = copy.textContent;
    }
    if (!l && el.getAttribute('aria-labelledby')) {
      var by = document.getElementById(el.getAttribute('aria-labelledby'));
      if (by) l = by.innerText;
    }
    if (!l && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) {
      var t = (el.getAttribute('type') || '').toLowerCase();
      l = (t === 'submit' || t === 'button' || t === 'reset') ? el.value : (el.placeholder || el.name);
    }
    if (!l) l = el.innerText || el.getAttribute('title') || el.getAttribute('alt') || el.name || el.id;
    return clip(l, 40);
  }
  function slug(s) {
    s = s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+/, '').slice(0, 28);
    while (s.charAt(s.length - 1) === '-') s = s.slice(0, -1);
    return s;
  }
  function refOf(el, k, lab) {
    var known = hd.names.get(el);
    if (known) return known;
    var base = PREFIX[k] + '-' + (slug(lab) || 'item');
    var ref = base, n = 1;
    while (hd.refs[ref] && hd.refs[ref] !== el && hd.refs[ref].isConnected) { n += 1; ref = base + '-' + n; }
    hd.names.set(el, ref);
    hd.refs[ref] = el;
    return ref;
  }
  function describe(el) {
    var k = kind(el), lab = label(el);
    var o = { ref: refOf(el, k, lab), role: k, label: lab };
    var tag = el.tagName.toLowerCase();
    if (tag === 'input' || tag === 'textarea' || tag === 'select') {
      var t = (el.getAttribute('type') || '').toLowerCase();
      if (k === 'checkbox' || k === 'radio') o.checked = !!el.checked;
      else if (t === 'password') o.value = el.value ? '••••' : '';
      else o.value = clip(el.value, 80);
    } else if (el.isContentEditable) {
      o.value = clip(el.innerText, 80);
    } else if (k === 'checkbox') {
      o.checked = el.getAttribute('aria-checked') === 'true';
    }
    if (el.disabled || el.getAttribute('aria-disabled') === 'true') o.disabled = true;
    if (k === 'link') o.href = clip(el.getAttribute('href'), 80);
    return o;
  }
  function inventory(max) {
    var out = [];
    var all = document.querySelectorAll(SELECTOR);
    for (var i = 0; i < all.length && out.length < max; i++) {
      if (shown(all[i])) out.push(describe(all[i]));
    }
    return out;
  }
  function pick(p) {
    if (p.ref) {
      var el = hd.refs[p.ref];
      if (!el || !el.isConnected) return { error: "The ref '" + p.ref + "' is stale (the page changed). Call elements again." };
      return { el: el };
    }
    if (p.selector) {
      try {
        var found = document.querySelector(p.selector);
        if (!found) return { error: "No element matches '" + p.selector + "'." };
        return { el: found };
      } catch (e) { return { error: "Bad selector '" + p.selector + "'." }; }
    }
    return { error: 'Give a ref from elements, or a selector.' };
  }
  function fire(el, type, init) {
    var ctor = type.indexOf('pointer') === 0 && window.PointerEvent ? PointerEvent : MouseEvent;
    var o = { bubbles: true, cancelable: true, view: window };
    for (var k in init) o[k] = init[k];
    el.dispatchEvent(new ctor(type, o));
  }
  function key(el, type, k) {
    el.dispatchEvent(new KeyboardEvent(type, { key: k, code: k.length === 1 ? 'Key' + k.toUpperCase() : k, bubbles: true, cancelable: true }));
  }
  function setValue(el, text) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value');
    if (setter && setter.set) setter.set.call(el, text); else el.value = text;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  function submitOf(el) {
    var form = el.form || el.closest('form');
    if (!form) return false;
    if (form.requestSubmit) form.requestSubmit(); else form.submit();
    return true;
  }

  hd.run = function (json) {
    var p = JSON.parse(json);
    var a = p.action;
    if (a === 'elements' || a === 'strobe') return JSON.stringify({ ok: true });
    if (a === 'scroll') {
      if (p.to === 'top') window.scrollTo(0, 0);
      else if (p.to === 'bottom') window.scrollTo(0, document.documentElement.scrollHeight);
      else window.scrollBy(0, p.amount != null ? p.amount : Math.round(Math.max(window.innerHeight, 400) * 0.9));
      return JSON.stringify({ ok: true });
    }
    var got = pick(p);
    if (got.error) return JSON.stringify({ error: got.error });
    var el = got.el;
    try {
      el.scrollIntoView({ block: 'center', inline: 'nearest' });
      if (a === 'hover') {
        fire(el, 'pointerover', {}); fire(el, 'mouseover', {}); fire(el, 'mouseenter', {}); fire(el, 'mousemove', {});
      } else if (a === 'click') {
        fire(el, 'pointerdown', {}); fire(el, 'mousedown', {});
        if (el.focus) el.focus();
        fire(el, 'pointerup', {}); fire(el, 'mouseup', {});
        el.click();
      } else if (a === 'type') {
        if (p.text == null) return JSON.stringify({ error: 'type needs text.' });
        var k = kind(el);
        if (el.tagName === 'SELECT') {
          var wanted = null;
          for (var i = 0; i < el.options.length; i++) {
            if (el.options[i].value === p.text || el.options[i].text.trim() === p.text) wanted = el.options[i];
          }
          if (!wanted) return JSON.stringify({ error: "No option '" + p.text + "' in " + p.ref + '.' });
          el.value = wanted.value;
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        } else if (k === 'checkbox' || k === 'radio') {
          return JSON.stringify({ error: 'Use click on a checkbox or radio, not type.' });
        } else if (el.isContentEditable) {
          el.focus(); el.innerText = p.text;
          el.dispatchEvent(new Event('input', { bubbles: true }));
        } else if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
          el.focus(); setValue(el, p.text);
        } else {
          return JSON.stringify({ error: p.ref + ' is not something text can be typed into.' });
        }
        if (p.submit) { key(el, 'keydown', 'Enter'); key(el, 'keyup', 'Enter'); submitOf(el); }
      } else if (a === 'press') {
        var body = el === document.body || el === document.documentElement;
        if (body && p.key && p.key.length === 1 && !p.allow_shortcut) {
          return JSON.stringify({ error: 'A printable key on the page itself is refused unless allow_shortcut is true.' });
        }
        if (el.focus) el.focus();
        key(el, 'keydown', p.key); key(el, 'keyup', p.key);
        if (p.key === 'Enter') {
          var tag = el.tagName;
          if (tag === 'BUTTON' || tag === 'A' || el.getAttribute('role') === 'button') el.click();
          else if (tag === 'INPUT') submitOf(el);
        }
      } else {
        return JSON.stringify({ error: 'Unknown action ' + a + '.' });
      }
    } catch (e) {
      return JSON.stringify({ error: String(e && e.message || e) });
    }
    return JSON.stringify({ ok: true });
  };

  hd.delta = function (json) {
    var p = JSON.parse(json);
    var max = p.max > 0 ? p.max : 120;
    var now = inventory(max);
    var url = location.href;
    var base = { success: true, url: url, title: document.title, scroll_y: Math.round(window.scrollY) };
    var whole = p.action === 'elements' && (p.full || hd.last === null || hd.lastUrl !== url);
    var prior = hd.last || {};
    var next = {};
    now.forEach(function (o) { next[o.ref] = o; });
    hd.last = next;
    hd.lastUrl = url;
    if (whole) { base.elements = now; base.count = now.length; return JSON.stringify(base); }
    var added = [], changed = [], removed = [];
    now.forEach(function (o) {
      var before = prior[o.ref];
      if (!before) { added.push(o); return; }
      var moved = { ref: o.ref };
      var any = false;
      ['label', 'value', 'checked', 'disabled'].forEach(function (f) {
        if (o[f] !== before[f]) { moved[f] = o[f] === undefined ? null : o[f]; any = true; }
      });
      if (any) changed.push(moved);
    });
    Object.keys(prior).forEach(function (r) { if (!next[r]) removed.push(r); });
    if (added.length) base.added = added;
    if (changed.length) base.changed = changed;
    if (removed.length) base.removed = removed;
    return JSON.stringify(base);
  };
  return 'ready';
})()
""".trimIndent()
