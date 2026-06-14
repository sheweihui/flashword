const { userAPI, vocabAPI, studyRecordAPI, storeAPI } = require('../../utils/api')
const { getUserInfo, calculateLevel, getUserId } = require('../../utils/helper')

Page({
  data: {
    userInfo: {
      id: null,
      username: '',
      nickname: '未登录',
      avatar: '',
      level: 'Lv.1 新手',
      createdDays: 0
    },
    stats: [
      { label: '今日学习', value: '0', unit: '词' },
      { label: '本周学习', value: '0', unit: '词' },
      { label: '累计学习', value: '0', unit: '词' },
      { label: '学习天数', value: '0', unit: '天' }
    ],
    progress: {
      mastered: 0,
      total: 0,
      percent: 0
    },
    pointsBalance: {
      balance: 0,
      totalEarned: 0,
      totalSpent: 0
    },
    checkinInfo: {
      checkedIn: false,
      continuousDays: 0
    },
    menuItems: [
      { icon: '📚', label: '学习计划', badge: '' },
      { icon: '🏆', label: '成就中心', badge: '' },
      { icon: '⚙️', label: '设置', badge: '' },
      { icon: '💬', label: '意见反馈', badge: '' }
    ],
    isLoggedIn: false,
    isLoading: true
  },

  onLoad() {
    const userInfo = getUserInfo()
    if (userInfo?.id) {
      this.setData({
        isLoggedIn: true,
        userInfo: { ...this.data.userInfo, ...userInfo }
      })
      this.loadUserData()
    } else {
      wx.redirectTo({ url: '/pages/login/login' })
    }
  },

  onShow() {
    const userInfo = getUserInfo()
    if (userInfo?.id) {
      this.loadUserData()
    }
  },

  loadUserData() {
    this.setData({ isLoading: true })
    Promise.all([
      this.loadStudyStats(),
      this.loadVocabularyProgress(),
      this.loadPointsBalance()
    ]).finally(() => {
      this.setData({ isLoading: false })
    })
  },

  loadPointsBalance() {
    return storeAPI.getPointsBalance().then(res => {
      if (res && res.code === 200) {
        this.setData({ pointsBalance: res.data })
      }
    }).catch(() => {})
  },

  checkin() {
    wx.showLoading({ title: '签到中...' })
    storeAPI.checkin().then(res => {
      wx.hideLoading()

      if (res && res.code === 200) {
        const data = res.data || {}
        const { checkedIn, pointsEarned, continuousDays, bonusPoints } = data

        if (checkedIn) {
          let message = `签到成功！获得${pointsEarned}积分`
          if (bonusPoints > 0) {
            message += `，连续签到${continuousDays}天额外奖励${bonusPoints}积分`
          }
          wx.showToast({ title: message, icon: 'success', duration: 2000 })
          this.loadPointsBalance()
          this.setData({ checkinInfo: { checkedIn: true, continuousDays: continuousDays } })
        } else {
          wx.showToast({ title: res.message || '今日已签到', icon: 'none', duration: 2000 })
        }
      } else {
        wx.showToast({ title: res?.message || '签到失败', icon: 'none' })
      }
    }).catch(() => {
      wx.hideLoading()
      wx.showToast({ title: '签到失败', icon: 'none' })
    })
  },

  loadStudyStats() {
    return studyRecordAPI.getStudyStats().then(res => {
      // res = { code: 200, message: "...", data: { totalWords, todayLearned, todayReviewed, streakDays, ... } }
      const stats = res.data || {}
      const level = calculateLevel(stats.totalWords || 0)
      const userInfo = getUserInfo()
      const updatedUserInfo = { ...userInfo, level }
      wx.setStorageSync('userInfo', updatedUserInfo)

      this.setData({
        stats: [
          { label: '今日学习', value: stats.todayLearned || '0', unit: '词' },
          { label: '今日复习', value: stats.todayReviewed || '0', unit: '词' },
          { label: '累计学习', value: stats.totalWords || '0', unit: '词' },
          { label: '学习天数', value: stats.streakDays || '0', unit: '天' }
        ],
        'userInfo.level': level,
        // 也设置掌握进度（复用 study stats 数据）
        progress: {
          mastered: stats.masteredWords || 0,
          total: stats.totalWords || 0,
          percent: stats.totalWords > 0
            ? Math.round((stats.masteredWords || 0) / stats.totalWords * 100)
            : 0
        }
      })
    }).catch(() => {})
  },

  loadVocabularyProgress() {
    // 数据已从 loadStudyStats 中获取，无需重复请求
    return Promise.resolve()
  },

  goToMenuItem(e) {
    const item = e.currentTarget.dataset.item
    wx.showToast({ title: `${item.label}功能开发中`, icon: 'none' })
  },

  async logout() {
    wx.showModal({
      title: '确认退出',
      content: '确定要退出登录吗？',
      success: async (res) => {
        if (res.confirm) {
          const token = wx.getStorageSync('token')
          try {
            await userAPI.logout(token)
          } catch (err) {
            // ignore
          }
          wx.removeStorageSync('token')
          wx.removeStorageSync('userInfo')
          wx.showToast({ title: '退出成功', icon: 'success' })
          setTimeout(() => {
            wx.reLaunch({ url: '/pages/login/login' })
          }, 1500)
        }
      }
    })
  },

  onPullDownRefresh() {
    if (this.data.isLoggedIn) {
      this.loadUserData().finally(() => {
        wx.stopPullDownRefresh()
      })
    } else {
      wx.stopPullDownRefresh()
    }
  }
})
