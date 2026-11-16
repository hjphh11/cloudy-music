/* Cloudy 官网脚本：滚动动效、玻璃演示、从版本清单同步版本号 */
(function () {
  'use strict';

  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const fine = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
  const MANIFEST = 'https://cn-nb1.rains3.com/qingwu/cloudy/version.json';

  /* 顶栏：滚过首屏后加深 */
  const nav = document.getElementById('nav');
  const onScroll = () => nav.classList.toggle('scrolled', window.scrollY > 12);
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  /* 入场动画 */
  const reveals = document.querySelectorAll('.reveal');
  if (reduce || !('IntersectionObserver' in window)) {
    reveals.forEach((el) => el.classList.add('in'));
  } else {
    document.querySelectorAll('.grid').forEach((grid) => {
      [...grid.children].forEach((child, i) => {
        child.style.transitionDelay = (i * 70) + 'ms';
      });
    });
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (!e.isIntersecting) return;
          e.target.classList.add('in');
          io.unobserve(e.target);
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -8% 0px' }
    );
    reveals.forEach((el) => io.observe(el));
  }

  /* 导航高亮：视口中间那一节亮起 */
  const links = [...document.querySelectorAll('.nav-links a')];
  const targets = links
    .map((a) => document.querySelector(a.getAttribute('href')))
    .filter(Boolean);
  if (targets.length && 'IntersectionObserver' in window) {
    const spy = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (!e.isIntersecting) return;
          links.forEach((a) =>
            a.classList.toggle('active', a.getAttribute('href') === '#' + e.target.id)
          );
        });
      },
      { rootMargin: '-45% 0px -50% 0px' }
    );
    targets.forEach((t) => spy.observe(t));
  }

  /* 液态玻璃：开关 + 高光跟随指针 */
  const stage = document.getElementById('stage');
  const plate = document.getElementById('plate');
  const toggle = document.getElementById('glassToggle');
  if (toggle && stage) {
    toggle.addEventListener('click', () => {
      const off = stage.classList.toggle('off');
      toggle.setAttribute('aria-pressed', String(!off));
    });
  }

  if (fine) {
    const track = (el) => {
      el.addEventListener('pointermove', (ev) => {
        const r = el.getBoundingClientRect();
        const x = ((ev.clientX - r.left) / r.width) * 100;
        const y = ((ev.clientY - r.top) / r.height) * 100;
        el.style.setProperty('--mx', x.toFixed(1) + '%');
        el.style.setProperty('--my', y.toFixed(1) + '%');
      });
      el.addEventListener('pointerleave', () => {
        el.style.setProperty('--mx', '50%');
        el.style.setProperty('--my', '22%');
      });
    };
    if (plate) track(plate);
    document.querySelectorAll('.feat').forEach(track);
  }

  /* 从对象存储上的版本清单同步版本号与下载地址 */
  const setText = (sel, text) => {
    document.querySelectorAll(sel).forEach((el) => (el.textContent = text));
  };

  fetch(MANIFEST, { cache: 'no-store' })
    .then((r) => (r.ok ? r.json() : null))
    .then((v) => {
      if (!v || !v.versionName) return;
      setText('[data-ver]', v.versionName);
      if (v.versionCode) setText('[data-build]', String(v.versionCode));
      if (v.publishedAt) setText('[data-date]', v.publishedAt);
      const apk = document.getElementById('dlApk');
      if (apk && v.apkUrl) apk.href = v.apkUrl;
    })
    .catch(() => {
      /* 清单取不到就用 HTML 里写死的版本，不影响下载 */
    });
})();
