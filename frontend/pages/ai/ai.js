const { agentAPI, wordAPI, aiAPI } = require('../../utils/api')

Page({
  data: {
    messages: [
      {
        id: 1,
        type: 'ai',
        content: '你好！我是你的AI英语学习助手。我可以帮你查单词、造句、测试词汇量，或者一起练习英语对话。'
      }
    ],
    inputText: '',
    isLoading: false,
    conversationId: null,
    isLoggedIn: false,
    backendOnline: false,
    agentOnline: false,
    toView: 'bottom'
  },

  onLoad() {
    const userInfo = wx.getStorageSync('userInfo')
    if (userInfo?.id) {
      this.setData({ isLoggedIn: true })
    }
    this.checkHealth()
  },

  onShow() {
    const userInfo = wx.getStorageSync('userInfo')
    this.setData({ isLoggedIn: !!userInfo?.id })
  },

  /** 检测后端 AI 服务与 Python Agent 状态 */
  async checkHealth() {
    try {
      const res = await aiAPI.health()
      this.setData({ backendOnline: res?.agent_ready === true })
    } catch (e) {
      this.setData({ backendOnline: false })
    }

    try {
      const res = await agentAPI.health()
      this.setData({ agentOnline: res?.status === 'ok' })
    } catch (e) {
      this.setData({ agentOnline: false })
    }
  },

  scrollToBottom() {
    this.setData({ toView: 'bottom' })
  },

  sendMessage() {
    if (!this.data.inputText.trim() || this.data.isLoading) return

    const token = wx.getStorageSync('token')
    if (!token) {
      wx.showModal({
        title: '提示',
        content: '请先登录后使用AI助手',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/login/login' })
          }
        }
      })
      return
    }

    const newMessage = {
      id: Date.now(),
      type: 'user',
      content: this.data.inputText.trim()
    }

    this.setData({
      messages: [...this.data.messages, newMessage],
      inputText: '',
      isLoading: true
    }, () => {
      this.scrollToBottom()
    })

    this.callAI(newMessage.content, newMessage.id)
  },

  /** 按优先级调用 AI 服务：后端直连 DeepSeek > Python Agent > 查词回退 */
  async callAI(question, messageId) {
    try {
      if (this.data.backendOnline) {
        await this.callBackendChat(question)
        return
      }

      if (this.data.agentOnline) {
        await this.callAgentChat(question)
        return
      }

      // 都不可用时，尝试查词回退
      await this.callAIFillWord(question)
    } catch (err) {
      console.error('AI 调用失败:', err)
      this.markMessageFailed(messageId)
    }
  },

  /** 通过后端直连 DeepSeek 对话 */
  async callBackendChat(question) {
    const userInfo = wx.getStorageSync('userInfo')
    const res = await aiAPI.chat(question, userInfo?.id, this.data.conversationId)

    const aiReply = {
      id: Date.now() + 1,
      type: 'ai',
      content: this.cleanMarkdown(res.reply) || '抱歉，没有收到回复'
    }

    this.setData({
      messages: [...this.data.messages, aiReply],
      isLoading: false,
      conversationId: res.conversation_id
    }, () => {
      this.scrollToBottom()
    })
  },

  /** 通过 Python Agent 对话 */
  async callAgentChat(question) {
    const userInfo = wx.getStorageSync('userInfo')
    const res = await agentAPI.chat(question, userInfo?.id, this.data.conversationId)

    const aiReply = {
      id: Date.now() + 1,
      type: 'ai',
      content: this.cleanMarkdown(res.reply) || '抱歉，没有收到回复'
    }

    this.setData({
      messages: [...this.data.messages, aiReply],
      isLoading: false,
      conversationId: res.conversation_id
    }, () => {
      this.scrollToBottom()
    })
  },

  /** 单词查询回退 */
  async callAIFillWord(wordText) {
    try {
      const res = await wordAPI.aiFillWord(wordText)
      if (res && res.code === 200 && res.data && res.data.wordText) {
        const content = this.formatWordResponse(res.data)
        const aiReply = { id: Date.now() + 1, type: 'ai', content }
        this.setData({ messages: [...this.data.messages, aiReply], isLoading: false }, () => {
          this.scrollToBottom()
        })
      } else {
        this.showFallbackMessage()
      }
    } catch (err) {
      this.showFallbackMessage()
    }
  },

  /** 清洗 markdown 符号（微信 <text> 不支持 markdown） */
  cleanMarkdown(text) {
    if (!text) return ''
    return text
      .replace(/\*\*([^*]+)\*\*/g, '$1')       // **加粗** → 普通
      .replace(/(?<!\*)\*([^*]+)\*(?!\*)/g, '$1') // *斜体* → 普通
      .replace(/`([^`]+)`/g, '$1')              // `代码` → 普通
      .replace(/```[\s\S]*?```/g, '')           // 代码块 → 移除
      .replace(/^#{1,6}\s+/gm, '')              // ##标题 → 标题文本
      .replace(/^>\s+/gm, '')                   // >引用 → 普通
      .replace(/^[\s]*[-*]\s+/gm, '• ')         // 列表符号 → •
  },

  formatWordResponse(word) {
    let content = `【${word.wordText}】 ${word.phonetic || ''}\n\n`
    content += `释义：${word.partOfSpeech || ''} ${word.definition || ''}\n\n`
    if (word.exampleSentence) {
      content += `例句：${word.exampleSentence}\n`
    }
    if (word.exampleTranslation) {
      content += `翻译：${word.exampleTranslation}\n\n`
    }
    if (word.note) {
      content += `备注：${word.note}\n\n`
    }
    content += '想了解更多单词吗？直接输入单词即可查询。'
    return content
  },

  markMessageFailed(messageId) {
    const { messages } = this.data
    const updated = messages.map(msg =>
      msg.id === messageId ? { ...msg, failed: true } : msg
    )
    this.setData({ messages: updated, isLoading: false })
  },

  retryMessage(e) {
    const id = e.currentTarget.dataset.id
    const { messages } = this.data
    const failedMsg = messages.find(m => m.id === id)
    if (!failedMsg) return

    const updated = messages.map(m =>
      m.id === id ? { ...m, failed: false } : m
    )
    this.setData({ messages: updated, isLoading: true }, () => {
      this.scrollToBottom()
    })

    this.callAI(failedMsg.content, id)
  },

  showFallbackMessage() {
    const aiReply = {
      id: Date.now() + 1,
      type: 'ai',
      content: '暂时无法连接到AI服务，请确保后端服务已启动。你可以稍后重试。'
    }
    this.setData({ messages: [...this.data.messages, aiReply], isLoading: false }, () => {
      this.scrollToBottom()
    })
  },

  clearChat() {
    wx.showModal({
      title: '确认清空',
      content: '确定要清空所有对话吗？',
      success: (res) => {
        if (res.confirm) {
          const convId = this.data.conversationId
          if (convId) {
            Promise.all([
              aiAPI.clearConversation(convId).catch(() => {}),
              agentAPI.clearConversation(convId).catch(() => {})
            ])
          }
          this.setData({
            messages: [{
              id: 1,
              type: 'ai',
              content: '你好！我是你的AI英语学习助手。我可以帮你查单词、造句、测试词汇量，或者一起练习英语对话。'
            }],
            conversationId: null
          })
        }
      }
    })
  },

  quickAction(e) {
    const action = e.currentTarget.dataset.action
    this.setData({ inputText: action })
  },

  onInput(e) {
    this.setData({ inputText: e.detail.value })
  }
})
