const { userAPI } = require('../../utils/api')

Page({
  data: {
    activeTab: 'login',
    username: '',
    password: '',
    showPassword: false,
    regUsername: '',
    regNickname: '',
    regPassword: '',
    regConfirmPassword: '',
    showRegPassword: false,
    showRegConfirmPassword: false,
    isLoading: false
  },

  onLoad() {
    const userInfo = wx.getStorageSync('userInfo')
    if (userInfo?.id) {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  togglePassword() {
    this.setData({ showPassword: !this.data.showPassword })
  },

  onRegUsernameInput(e) {
    this.setData({ regUsername: e.detail.value })
  },

  onRegNicknameInput(e) {
    this.setData({ regNickname: e.detail.value })
  },

  onRegPasswordInput(e) {
    this.setData({ regPassword: e.detail.value })
  },

  onRegConfirmPasswordInput(e) {
    this.setData({ regConfirmPassword: e.detail.value })
  },

  toggleRegPassword() {
    this.setData({ showRegPassword: !this.data.showRegPassword })
  },

  toggleRegConfirmPassword() {
    this.setData({ showRegConfirmPassword: !this.data.showRegConfirmPassword })
  },

  async login() {
    const { username, password } = this.data

    if (!username.trim()) {
      wx.showToast({ title: '请输入用户名', icon: 'none' })
      return
    }

    if (!password.trim()) {
      wx.showToast({ title: '请输入密码', icon: 'none' })
      return
    }

    this.setData({ isLoading: true })

    try {
      const res = await userAPI.login(username, password)
      // res.data = { code: 200, message: "登录成功", data: { token, userId, nickname, ... } }
      const loginData = res.data
      wx.setStorageSync('token', loginData.token)

      const userData = {
        id: loginData.userId,
        username: loginData.username || username.trim(),
        nickname: loginData.nickname || username.trim(),
        avatar: loginData.avatar || ''
      }
      wx.setStorageSync('userInfo', userData)

      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        wx.switchTab({ url: '/pages/index/index' })
      }, 1000)
    } catch (err) {
      wx.showToast({ title: err.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ isLoading: false })
    }
  },

  async register() {
    const { regUsername, regPassword, regConfirmPassword, regNickname } = this.data

    if (!regUsername.trim()) {
      wx.showToast({ title: '请输入用户名', icon: 'none' })
      return
    }

    if (regUsername.length < 3 || regUsername.length > 20) {
      wx.showToast({ title: '用户名长度为3-20个字符', icon: 'none' })
      return
    }

    if (!regPassword.trim()) {
      wx.showToast({ title: '请输入密码', icon: 'none' })
      return
    }

    if (regPassword.length < 6) {
      wx.showToast({ title: '密码至少6个字符', icon: 'none' })
      return
    }

    if (regPassword !== regConfirmPassword) {
      wx.showToast({ title: '两次输入的密码不一致', icon: 'none' })
      return
    }

    this.setData({ isLoading: true })

    try {
      await userAPI.register({
        username: regUsername.trim(),
        password: regPassword.trim(),
        nickname: regNickname.trim() || regUsername.trim()
      })

      wx.showToast({ title: '注册成功，请登录', icon: 'success' })
      setTimeout(() => {
        this.setData({ activeTab: 'login' })
      }, 1500)
    } catch (err) {
      wx.showToast({ title: err.message || '注册失败', icon: 'none' })
    } finally {
      this.setData({ isLoading: false })
    }
  },

  goBack() {
    wx.navigateBack()
  }
})
