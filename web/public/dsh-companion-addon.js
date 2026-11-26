/**
 * 搭子页上的 DeepSeek Harness 入口。不进 Vue 包。
 * 整目录替换 web-dist 时这个文件必须跟着走，否则按钮会消失。
 * 直接打开 dsh 子域会被门禁拒绝，只能从这里拿票再跳转。
 */
(function () {
  var BTN_CLASS = 'dsh-harness-entry-btn'
  var NOTICE_ID = 'dsh-harness-notice'
  var AS_TOKEN = 'agentscope.loginToken'
  var AS_USER = 'agentscope.userId'
  var FALLBACK_TOKENS = ['saas_user_token', 'access_token', 'token']

  function onCompanion() {
    var p = location.pathname || ''
    return (
      p.indexOf('/dashboard/chat') === 0 ||
      p === '/companion' ||
      p.indexOf('/companion') === 0
    )
  }

  function getSession() {
    try {
      var token = localStorage.getItem(AS_TOKEN) || ''
      var userId = localStorage.getItem(AS_USER) || ''
      if (!token) {
        for (var i = 0; i < FALLBACK_TOKENS.length; i++) {
          token = localStorage.getItem(FALLBACK_TOKENS[i]) || ''
          if (token) break
        }
      }
      return { token: token, userId: userId }
    } catch (e) {
      return { token: '', userId: '' }
    }
  }

  function findLogoutBtn() {
    var buttons = document.querySelectorAll('button')
    for (var i = 0; i < buttons.length; i++) {
      var text = (buttons[i].textContent || '').trim()
      if (text === '退出' || text === '登出' || text === 'Logout') return buttons[i]
    }
    return null
  }

  /**
   * 白名单拒绝给对话用户看，不使用浏览器 alert。
   * 不写服务器路径：打开这个页面的人改不了那台机器上的名单。
   */
  function showNotAllowed(userId) {
    var old = document.getElementById(NOTICE_ID)
    if (old) old.remove()
    var box = document.createElement('div')
    box.id = NOTICE_ID
    box.setAttribute('role', 'status')
    box.style.cssText =
      'position:fixed;top:64px;right:16px;z-index:10000;width:min(320px,calc(100vw - 32px));' +
      'background:#fff;border:1px solid #e5e7eb;border-radius:12px;' +
      'box-shadow:0 8px 24px rgba(15,23,42,.12);padding:16px;font-size:14px;' +
      'color:#1f2937;line-height:1.55;font-family:inherit;'
    var title = document.createElement('div')
    title.textContent = '还没有开通'
    title.style.cssText = 'font-weight:600;margin-bottom:6px;'
    var text = document.createElement('p')
    text.style.cssText = 'margin:0 0 10px;'
    text.textContent = userId
      ? '当前账号还不能进入 DeepSeek Harness。把下面的用户编号发给管理员，开通后即可使用。'
      : '当前账号还不能进入 DeepSeek Harness。请重新登录后再试。'
    box.appendChild(title)
    box.appendChild(text)
    if (userId) {
      var idLine = document.createElement('p')
      idLine.style.cssText =
        'margin:0 0 12px;padding:8px 10px;border-radius:8px;background:#f8fafc;' +
        'font-family:ui-monospace,monospace;font-size:12px;word-break:break-all;'
      idLine.textContent = userId
      box.appendChild(idLine)
    }
    var close = document.createElement('button')
    close.type = 'button'
    close.textContent = '知道了'
    close.style.cssText =
      'float:right;border:0;border-radius:8px;padding:6px 12px;cursor:pointer;' +
      'background:#c2410c;color:#fff;font-size:13px;'
    close.addEventListener('click', function () {
      box.remove()
    })
    box.appendChild(close)
    document.body.appendChild(box)
  }

  async function openDsh() {
    var sess = getSession()
    if (!sess.token) {
      alert('未找到登录凭证（agentscope.loginToken）。请重新登录后再试。')
      location.href = '/login?redirect=' + encodeURIComponent(location.pathname || '/dashboard/chat')
      return
    }
    var btn = document.querySelector('.' + BTN_CLASS)
    if (btn) {
      btn.disabled = true
      btn.textContent = '打开中…'
    }
    try {
      var res = await fetch('/api/dsh-gate/ticket', {
        method: 'POST',
        headers: {
          Authorization: 'Bearer ' + sess.token,
          'Content-Type': 'application/json',
          'X-User-Id': sess.userId || '',
        },
      })
      var body = {}
      try {
        body = await res.json()
      } catch (e) {}
      if (res.status === 403) {
        showNotAllowed(sess.userId || '')
        return
      }
      if (!res.ok) {
        alert(
          '打开失败：' +
            ((body && body.message) || res.status) +
            '\nuserId=' +
            (sess.userId || '(空)') +
            '\ntokenParts=' +
            String(sess.token.split('.').length),
        )
        return
      }
      var enterUrl = body && body.data && body.data.enterUrl
      if (!enterUrl) {
        alert('未返回进入地址')
        return
      }
      // 微信和手机上新窗口不可靠，同页跳转才能带上门禁 cookie。
      var ua = navigator.userAgent || ''
      var isMobile = /MicroMessenger|Mobile|Android|iPhone/i.test(ua)
      if (isMobile) location.assign(enterUrl)
      else window.open(enterUrl, '_blank', 'noopener')
    } catch (e) {
      alert((e && e.message) || '打开 DSH 失败')
    } finally {
      if (btn) {
        btn.disabled = false
        btn.textContent = 'DeepSeek Harness'
      }
    }
  }

  function inject() {
    if (!onCompanion()) return
    if (document.querySelector('.' + BTN_CLASS)) return
    var logout = findLogoutBtn()
    var btn = document.createElement('button')
    btn.type = 'button'
    btn.className = BTN_CLASS
    btn.textContent = 'DeepSeek Harness'
    btn.title = '一键进入 DeepSeek Harness'
    btn.style.cssText =
      'margin-right:8px;border-radius:8px;border:1px solid var(--color-border-strong,#d0d5dd);padding:6px 12px;font-size:14px;background:#fff;cursor:pointer;'
    btn.addEventListener('click', openDsh)
    if (logout && logout.parentNode) {
      logout.parentNode.insertBefore(btn, logout)
      return
    }
    btn.style.position = 'fixed'
    btn.style.top = '12px'
    btn.style.right = '96px'
    btn.style.zIndex = '9999'
    document.body.appendChild(btn)
  }

  setInterval(inject, 800)
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', inject)
  else inject()
})()
