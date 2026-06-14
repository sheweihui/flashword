/** 后端 API 基础地址 */
const API_BASE_URL = 'http://localhost:8080/api'

/**
 * 编码表单数据为 URL query string
 */
function encodeFormData(data) {
  if (!data || typeof data !== 'object') {
    return ''
  }
  const pairs = []
  for (const key in data) {
    if (data.hasOwnProperty(key)) {
      const value = data[key]
      if (value !== undefined && value !== null) {
        pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
      }
    }
  }
  return pairs.join('&')
}

/** 处理 401 未授权：跳转登录页 */
const handleUnauthorized = () => {
  wx.showToast({
    title: '登录已过期，请重新登录',
    icon: 'none',
    duration: 1500
  })
  wx.removeStorageSync('token')
  wx.removeStorageSync('userInfo')
  setTimeout(() => {
    wx.reLaunch({ url: '/pages/login/login' })
  }, 1500)
}

/**
 * 通用请求方法
 * @param {string} url - 请求路径
 * @param {string} method - HTTP 方法
 * @param {object|null} data - 请求数据
 * @param {boolean} needAuth - 是否需要认证
 * @param {boolean} raw - 直接返回 res.data 跳过 code 检查（用于非标准响应格式）
 */
const request = (url, method = 'GET', data = null, needAuth = true, raw = false) => {
  return new Promise((resolve, reject) => {
    const header = {}

    if (needAuth) {
      const token = wx.getStorageSync('token')
      if (token) {
        header['Authorization'] = `Bearer ${token}`
      }
    }

    let requestData = null
    let requestUrl = `${API_BASE_URL}${url}`

    if (method === 'GET') {
      if (data) {
        requestUrl += '?' + encodeFormData(data)
      }
    } else {
      header['Content-Type'] = 'application/json'
      requestData = data ? JSON.stringify(data) : null
    }

    wx.request({
      url: requestUrl,
      method,
      data: requestData,
      header,
      success: (res) => {
        if (res.statusCode === 401) {
          handleUnauthorized()
          reject(new Error('未授权'))
          return
        }

        if (res.statusCode === 200) {
          if (raw) {
            resolve(res.data)
            return
          }

          if (res.data.code === 401) {
            handleUnauthorized()
            reject(new Error('未授权'))
            return
          }

          if (res.data.code === 200) {
            resolve(res.data)
          } else {
            reject(res.data)
          }
        } else {
          reject(res)
        }
      },
      fail: (err) => {
        wx.showToast({
          title: '网络请求失败，请检查后端服务',
          icon: 'none'
        })
        reject(err)
      }
    })
  })
}

/** 用户相关 API */
const userAPI = {
  /** 用户登录 */
  login: (username, password) => {
    return request('/user/login', 'POST', { username, password }, false)
  },

  /** 用户注册 */
  register: (data) => {
    return request('/user/register', 'POST', data, false)
  },

  /** 获取用户信息 */
  getUserInfo: (id) => {
    return request(`/user/${id}`)
  },

  /** 验证 token 有效性 */
  validateToken: (token) => {
    return request('/user/validate', 'POST', { token }, false)
  },

  /** 退出登录 */
  logout: (token) => {
    return request('/user/logout', 'POST', { token }, false)
  }
}

/** 单词相关 API */
const wordAPI = {
  /** 获取单词详情 */
  getWordById: (id) => {
    return request(`/word/${id}`, 'GET')
  },

  /** AI 填充单词信息 */
  aiFillWord: (wordText) => {
    return request('/word/ai-fill', 'POST', { wordText })
  },

  /** 更新单词 */
  updateWord: (id, data) => {
    return request(`/word/${id}`, 'PUT', data)
  }
}

/** 单词书相关 API */
const vocabAPI = {
  /** 创建单词书 */
  createBook: (userId, name, description) => {
    return request('/vocabulary-book', 'POST', { userId, bookName: name, description })
  },

  /** 获取用户单词书列表 */
  getBookList: (userId) => {
    return request(`/vocabulary-book/list/${userId}`, 'GET')
  },

  /** 添加单词到单词书 */
  addWordToBook: (bookId, wordData) => {
    return request('/vocabulary-book/add-word', 'POST', { bookId, ...wordData })
  },

  /** 获取单词书的单词列表 */
  getWordsByBook: (bookId) => {
    return request('/vocabulary-book/words', 'GET', { bookId })
  },

  /** 更新单词书 */
  updateBook: (id, data) => {
    return request(`/vocabulary-book/${id}`, 'PUT', data)
  },

  /** 删除单词书 */
  deleteBook: (id) => {
    return request(`/vocabulary-book/${id}`, 'DELETE')
  },

  /** 从单词书删除单词 */
  removeWordFromBook: (bookId, wordId) => {
    return request('/vocabulary-book/word/remove', 'DELETE', { bookId, wordId })
  },

  /** 获取用户学习列表 */
  getVocabList: (userId) => {
    return request(`/vocabulary-book/list/${userId}`, 'GET')
  }
}

/** 单词书-单词关联 API（走 /api/book-word） */
const bookWordAPI = {
  /** 标记单词为已掌握 */
  markAsMastered: (userId, bookId, wordId) => {
    return request('/book-word/master', 'PUT', { userId, bookId, wordId })
  },

  /** 添加单词笔记 */
  addNote: (userId, bookId, wordId, note) => {
    return request('/book-word/note', 'PUT', { userId, bookId, wordId, note })
  },

  /** 获取未掌握单词 */
  getUnmastered: (userId, bookId) => {
    return request('/book-word/unmastered', 'GET', { userId, bookId })
  },

  /** 获取已掌握单词 */
  getMastered: (userId, bookId) => {
    return request('/book-word/mastered', 'GET', { userId, bookId })
  }
}

/** 学习记录 API */
const studyRecordAPI = {
  /** 获取学习统计 */
  getStudyStats: () => {
    return request('/vocabulary-book/study/stats')
  }
}

/** 商店相关 API */
const storeAPI = {
  /** 获取积分余额 */
  getPointsBalance: () => {
    return request('/store/points/balance', 'GET')
  },

  /** 每日签到 */
  checkin: () => {
    return request('/store/checkin', 'POST')
  },

  /** 获取商店单词书列表 */
  getBooks: (params = {}) => {
    const query = Object.keys(params)
      .map(k => `${k}=${encodeURIComponent(params[k])}`)
      .join('&')
    const url = query ? `/store/books?${query}` : '/store/books'
    return request(url, 'GET')
  },

  /** 获取单词书详情 */
  getBookDetail: (id) => {
    return request(`/store/books/${id}`, 'GET')
  },

  /** 获取单词书的单词列表（预览） */
  getBookWords: (id) => {
    return request(`/store/books/${id}/words`, 'GET')
  },

  /** 购买单词书 */
  purchaseBook: (id) => {
    return request(`/store/books/${id}/purchase`, 'POST')
  },

  /** 获取秒杀活动列表 */
  getFlashSaleList: () => {
    return request('/store/flash-sale/list', 'GET')
  },

  /** 秒杀购买 — 把秒杀价也传过去，后端按这个扣积分 */
  purchaseFlashSale: (id, flashPrice) => {
    return request(`/store/flash-sale/purchase/${id}`, 'POST', { flashPrice })
  }
}

/** Agent 服务请求（通过后端转发，跳过标准 code 检查） */
const agentRequest = (url, method = 'GET', data = null) => {
  return request(url, method, data, true, true)
}

/** AI 对话 API */
const agentAPI = {
  /** 发送对话消息 */
  chat: (message, userId = null, conversationId = null) => {
    return agentRequest('/agent/chat', 'POST', {
      message,
      user_id: userId,
      conversation_id: conversationId
    })
  },

  /** 清空对话 */
  clearConversation: (convId) => {
    return agentRequest(`/agent/conversations/${convId}`, 'DELETE')
  },

  /** 健康检查 */
  health: () => {
    return agentRequest('/agent/health', 'GET')
  }
}

/** 后端直连 AI 对话 API（通过 DeepSeek，无需 Python Agent） */
const aiAPI = {
  /** 发送对话消息 */
  chat: (message, userId = null, conversationId = null) => {
    return request('/ai/chat', 'POST', {
      message,
      userId,
      conversationId
    })
  },

  /** 清空对话 */
  clearConversation: (convId) => {
    return request('/ai/clear', 'POST', { conversation_id: convId })
  },

  /** 健康检查 */
  health: () => {
    return request('/ai/health', 'GET')
  }
}

module.exports = {
  userAPI,
  wordAPI,
  vocabAPI,
  studyRecordAPI,
  storeAPI,
  agentAPI,
  aiAPI,
  bookWordAPI
}
