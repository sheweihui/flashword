const { vocabAPI, wordAPI } = require('../../utils/api')
const { getUserInfo, formatDate, getUserId } = require('../../utils/helper')

Page({
  data: {
    books: [],
    isLoading: true,
    showAddModal: false,
    showCreateBookModal: false,
    showEditBookModal: false,
    selectedBookId: null,
    newBookName: '',
    newBookDesc: '',
    editBookId: null,
    editBookName: '',
    editBookDesc: '',
    wordForm: {
      wordText: '',
      phonetic: '',
      partOfSpeech: '',
      definition: '',
      exampleSentence: '',
      exampleTranslation: ''
    }
  },

  onLoad() {
    const userInfo = getUserInfo()
    if (userInfo?.id) {
      this.loadBooks()
    } else {
      wx.redirectTo({ url: '/pages/login/login' })
    }
  },

  onShow() {
    const userInfo = getUserInfo()
    if (userInfo?.id) {
      this.loadBooks()
    }
  },

  loadBooks() {
    this.setData({ isLoading: true })
    const userId = getUserId()
    if (!userId) { this.setData({ books: [], isLoading: false }); return }

    vocabAPI.getBookList(userId).then(res => {
      const books = res && res.code === 200 ? res.data : []
      this.setData({ books: books })
    }).catch(() => {
      this.setData({ books: [] })
    }).finally(() => {
      this.setData({ isLoading: false })
    })
  },

  goToBookDetail(e) {
    const book = e.currentTarget.dataset.book
    wx.navigateTo({
      url: `/pages/book-detail/book-detail?id=${book.id}&name=${encodeURIComponent(book.bookName)}`
    })
  },

  onPullDownRefresh() {
    this.loadBooks().finally(() => {
      wx.stopPullDownRefresh()
    })
  },

  showAddWordModal() {
    if (this.data.books.length === 0) {
      wx.showToast({ title: '请先创建单词书', icon: 'none' })
      return
    }
    this.setData({
      showAddModal: true,
      selectedBookId: null,
      wordForm: {
        wordText: '',
        phonetic: '',
        partOfSpeech: '',
        definition: '',
        exampleSentence: '',
        exampleTranslation: ''
      }
    })
  },

  closeAddModal() {
    this.setData({ showAddModal: false })
  },

  selectBook(e) {
    const bookId = e.currentTarget.dataset.bookId
    this.setData({ selectedBookId: bookId })
  },

  handleInput(e) {
    const field = e.currentTarget.dataset.field
    const value = e.detail.value
    this.setData({ [`wordForm.${field}`]: value })
  },

  async aiFillWord() {
    const { wordText } = this.data.wordForm
    if (!wordText.trim()) {
      wx.showToast({ title: '请先输入单词', icon: 'none' })
      return
    }

    try {
      wx.showLoading({ title: 'AI分析中...' })
      const result = await wordAPI.aiFillWord(wordText)

      if (result && result.code === 200 && result.data) {
        const wordData = result.data
        this.setData({
          wordForm: {
            wordText: wordData.wordText || wordText,
            phonetic: wordData.phonetic || '',
            partOfSpeech: wordData.partOfSpeech || '',
            definition: wordData.definition || '',
            exampleSentence: wordData.exampleSentence || '',
            exampleTranslation: wordData.exampleTranslation || ''
          }
        })
        wx.showToast({ title: 'AI填充成功', icon: 'success' })
      } else {
        wx.showToast({ title: result?.message || '填充失败', icon: 'none' })
      }
    } catch (err) {
      wx.showToast({ title: 'AI填充失败', icon: 'none' })
    } finally {
      wx.hideLoading()
    }
  },

  async submitAddWord() {
    const { selectedBookId, wordForm } = this.data

    if (!selectedBookId) {
      wx.showToast({ title: '请选择单词书', icon: 'none' })
      return
    }

    if (!wordForm.wordText.trim()) {
      wx.showToast({ title: '请输入单词', icon: 'none' })
      return
    }

    if (!wordForm.definition.trim()) {
      wx.showToast({ title: '请输入释义', icon: 'none' })
      return
    }

    try {
      wx.showLoading({ title: '添加中...' })
      const wordData = {
        wordText: wordForm.wordText,
        phonetic: wordForm.phonetic,
        partOfSpeech: wordForm.partOfSpeech,
        definition: wordForm.definition,
        exampleSentence: wordForm.exampleSentence,
        exampleTranslation: wordForm.exampleTranslation
      }
      await vocabAPI.addWordToBook(selectedBookId, wordData)
      wx.hideLoading()
      wx.showToast({ title: '添加成功', icon: 'success' })
      this.closeAddModal()
      this.loadBooks()
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: '添加失败', icon: 'none' })
    }
  },

  showCreateBookModal() {
    this.setData({
      showCreateBookModal: true,
      newBookName: '',
      newBookDesc: ''
    })
  },

  closeCreateBookModal() {
    this.setData({ showCreateBookModal: false })
  },

  onBookNameInput(e) {
    this.setData({ newBookName: e.detail.value })
  },

  onBookDescInput(e) {
    this.setData({ newBookDesc: e.detail.value })
  },

  async createBook() {
    const { newBookName, newBookDesc } = this.data

    if (!newBookName.trim()) {
      wx.showToast({ title: '请输入单词书名称', icon: 'none' })
      return
    }

    try {
      wx.showLoading({ title: '创建中...' })
      const userId = getUserId()
      if (!userId) { wx.hideLoading(); return }
      await vocabAPI.createBook(userId, newBookName, newBookDesc)
      wx.hideLoading()
      wx.showToast({ title: '创建成功', icon: 'success' })
      this.closeCreateBookModal()
      this.loadBooks()
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: '创建失败', icon: 'none' })
    }
  },

  editBookTap(e) {
    const dataset = e.currentTarget.dataset
    this.setData({
      showEditBookModal: true,
      editBookId: parseInt(dataset.bookid) || dataset.bookid,
      editBookName: dataset.bookname,
      editBookDesc: dataset.desc || ''
    })
  },

  closeEditModal() {
    this.setData({ showEditBookModal: false })
  },

  editNameInput(e) {
    this.setData({ editBookName: e.detail.value })
  },

  editDescInput(e) {
    this.setData({ editBookDesc: e.detail.value })
  },

  async confirmEditBook() {
    const { editBookId, editBookName, editBookDesc } = this.data

    if (!editBookName.trim()) {
      wx.showToast({ title: '请输入名称', icon: 'none' })
      return
    }

    try {
      wx.showLoading({ title: '保存中...' })
      await vocabAPI.updateBook(editBookId, {
        bookName: editBookName,
        description: editBookDesc
      })
      wx.hideLoading()
      wx.showToast({ title: '修改成功', icon: 'success' })
      this.closeEditModal()
      this.loadBooks()
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: '修改失败', icon: 'none' })
    }
  },

  deleteBookTap(e) {
    const dataset = e.currentTarget.dataset
    wx.showModal({
      title: '删除单词书',
      content: `确定删除「${dataset.bookname}」？`,
      confirmText: '删除',
      confirmColor: '#f5576c',
      success: async (res) => {
        if (res.confirm) {
          try {
            wx.showLoading({ title: '删除中...' })
            await vocabAPI.deleteBook(dataset.bookid)
            wx.hideLoading()
            wx.showToast({ title: '删除成功', icon: 'success' })
            this.loadBooks()
          } catch (err) {
            wx.hideLoading()
            wx.showToast({ title: '删除失败', icon: 'none' })
          }
        }
      }
    })
  }
})
