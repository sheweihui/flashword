/**
 * 共享工具函数
 */

/** 日期格式化: M/d */
function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** 日期格式化: yyyy-MM-dd HH:mm */
function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 秒数 → HH:MM:SS */
function formatCountdown(seconds) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/** 根据时间返回问候语 */
function getGreeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

/** 获取今日日期字符串: M月D日 周X */
function getTodayString() {
  const now = new Date()
  const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${now.getMonth() + 1}月${now.getDate()}日 ${weekDays[now.getDay()]}`
}

/** 从 storage 获取用户信息，带默认值 */
function getUserInfo() {
  return wx.getStorageSync('userInfo') || {}
}

/** 获取用户 ID，未登录则跳转登录页并返回 null */
function getUserId() {
  const userInfo = getUserInfo()
  if (!userInfo?.id) {
    wx.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => wx.reLaunch({ url: '/pages/login/login' }), 500)
    return null
  }
  return userInfo.id
}

/** 从 storage 获取 token */
function getToken() {
  return wx.getStorageSync('token') || ''
}

/** 检查登录状态，未登录则跳转 */
function checkLogin() {
  const token = getToken()
  const userInfo = getUserInfo()
  if (!token || !userInfo?.id) {
    wx.redirectTo({ url: '/pages/login/login' })
    return false
  }
  return true
}

/** 跳转登录页 */
function navigateLogin() {
  wx.removeStorageSync('token')
  wx.removeStorageSync('userInfo')
  wx.reLaunch({ url: '/pages/login/login' })
}

/** 成功提示 */
function showSuccess(msg) {
  wx.showToast({ title: msg || '成功', icon: 'success' })
}

/** 错误提示 */
function showError(msg) {
  wx.showToast({ title: msg || '操作失败', icon: 'none' })
}

/** 加载提示 */
function showLoading(msg) {
  wx.showLoading({ title: msg || '加载中...' })
}

/** 隐藏加载提示 */
function hideLoading() {
  wx.hideLoading()
}

/** 通用 API 异常处理 */
function handleApiError(err, defaultMsg) {
  console.error(err)
  const msg = err?.message || err?.errMsg || defaultMsg || '操作失败'
  showError(msg)
}

/** 根据单词总数计算等级 */
function calculateLevel(totalWords) {
  if (totalWords >= 1000) return 'Lv.5 词汇大师'
  if (totalWords >= 500) return 'Lv.4 单词达人'
  if (totalWords >= 200) return 'Lv.3 学习能手'
  if (totalWords >= 50) return 'Lv.2 进阶学习'
  return 'Lv.1 新手'
}

/** 防抖 */
function debounce(fn, delay) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

module.exports = {
  formatDate,
  formatDateTime,
  formatCountdown,
  getGreeting,
  getTodayString,
  getUserInfo,
  getUserId,
  getToken,
  checkLogin,
  navigateLogin,
  showSuccess,
  showError,
  showLoading,
  hideLoading,
  handleApiError,
  calculateLevel,
  debounce
}
