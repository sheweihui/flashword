const { studyRecordAPI, vocabAPI } = require('../../utils/api')
const { getGreeting, getTodayString, getUserInfo, getUserId } = require('../../utils/helper')

Page({
  data: {
    greeting: '',
    today: '',
    todayWords: 0,
    totalWords: 0,
    streak: 0,
    progress: 0,
    targetWords: 50,
    isLoading: true,
    showModal: false,
    bookForm: {
      name: '',
      description: ''
    }
  },

  onLoad() {
    this.setToday()
    const userInfo = getUserInfo()
    if (userInfo?.id) {
      this.setGreeting()
      this.loadData()
    } else {
      wx.redirectTo({ url: '/pages/login/login' })
    }
  },

  onShow() {
    const userInfo = getUserInfo()
    if (userInfo?.id) {
      this.loadData()
    }
  },

  setGreeting() {
    const userInfo = getUserInfo()
    const nickname = userInfo?.nickname || '学习者'
    this.setData({ greeting: `${getGreeting()}，${nickname}` })
  },

  setToday() {
    this.setData({ today: getTodayString() })
  },

  loadData() {
    this.setData({ isLoading: true })
    this.loadStudyStats().finally(() => {
      this.setData({ isLoading: false })
    })
  },

  loadStudyStats() {
    return studyRecordAPI.getStudyStats().then(res => {
      // res = { code: 200, message: "...", data: { totalWords, todayLearned, todayReviewed, streakDays, ... } }
      const stats = res.data || {}
      const todayWords = stats.todayLearned || 0
      const targetWords = 50
      const progress = Math.min(Math.round((todayWords / targetWords) * 100), 100)
      this.setData({
        todayWords,
        totalWords: stats.totalWords || 0,
        streak: stats.streakDays || 0,
        targetWords,
        progress
      })
    }).catch(() => {
      this.setData({
        todayWords: 0,
        totalWords: 0,
        streak: 0,
        progress: 0
      })
    })
  },

  createBook() {
    this.setData({
      showModal: true,
      bookForm: { name: '', description: '' }
    })
  },

  closeModal() {
    this.setData({ showModal: false })
  },

  preventBubble() {},

  onNameInput(e) {
    this.setData({ 'bookForm.name': e.detail.value })
  },

  onDescInput(e) {
    this.setData({ 'bookForm.description': e.detail.value })
  },

  async submitCreate() {
    const { name, description } = this.data.bookForm

    if (!name.trim()) {
      wx.showToast({ title: '请输入名称', icon: 'none' })
      return
    }

    try {
      const userId = getUserId()
      if (!userId) return
      await vocabAPI.createBook(userId, name.trim(), description.trim())
      wx.showToast({ title: '创建成功', icon: 'success' })
      this.closeModal()
    } catch (err) {
      wx.showToast({ title: '创建失败', icon: 'none' })
    }
  },

  viewAllWords() {
    wx.switchTab({ url: '/pages/book/book' })
  },

  onPullDownRefresh() {
    const userInfo = getUserInfo()
    if (userInfo?.id) {
      this.loadData().finally(() => {
        wx.stopPullDownRefresh()
      })
    } else {
      wx.stopPullDownRefresh()
    }
  }
})
