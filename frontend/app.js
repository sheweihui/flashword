const { userAPI } = require('./utils/api')

App({
  onLaunch() {
    this.autoLogin()
  },

  async autoLogin() {
    const token = wx.getStorageSync('token')
    const userInfo = wx.getStorageSync('userInfo')

    if (token && userInfo?.id) {
      try {
        await userAPI.validateToken(token)
      } catch (err) {
        wx.removeStorageSync('token')
        wx.removeStorageSync('userInfo')
      }
    }
  }
})
