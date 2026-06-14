const { vocabAPI, wordAPI, bookWordAPI } = require('../../utils/api')
const { getUserId } = require('../../utils/helper')

Page({
  data: {
    bookId: null,
    bookName: '',
    words: [],
    filteredWords: [],
    loading: false,
    searchKeyword: '',
    showModal: false,
    showWordDetail: false,
    showEditModal: false,
    selectedWord: null,
    noteText: '',
    editWordId: null,
    batchMode: false,
    selectedWords: {},
    selectedCount: 0,
    wordForm: {
      wordText: '',
      phonetic: '',
      partOfSpeech: '',
      definition: '',
      exampleSentence: '',
      exampleTranslation: ''
    },
    editForm: {
      wordText: '',
      phonetic: '',
      partOfSpeech: '',
      definition: '',
      exampleSentence: '',
      exampleTranslation: ''
    }
  },

  onLoad(options) {
    if (options?.id && options?.name) {
      this.setData({
        bookId: parseInt(options.id),
        bookName: decodeURIComponent(options.name)
      })
      this.loadWords()
    }
  },

  onShow() {
    if (this.data.bookId) {
      this.loadWords()
    }
  },

  loadWords() {
    if (!this.data.bookId) return
    this.setData({ loading: true })

    vocabAPI.getWordsByBook(this.data.bookId).then(res => {
      const words = res && res.code === 200 ? res.data : []
      this.setData({
        words: words,
        filteredWords: words,
        loading: false
      })
    }).catch(() => {
      this.setData({ words: [], filteredWords: [], loading: false })
    })
  },

  goBack() {
    wx.navigateBack()
  },

  onSearchInput(e) {
    const keyword = e.detail.value
    this.setData({ searchKeyword: keyword })

    if (!keyword.trim()) {
      this.setData({ filteredWords: this.data.words })
    } else {
      const filtered = this.data.words.filter(word =>
        word.wordText.toLowerCase().includes(keyword.toLowerCase()) ||
        (word.definition && word.definition.includes(keyword))
      )
      this.setData({ filteredWords: filtered })
    }
  },

  showWordDetail(e) {
    const word = e.currentTarget.dataset.word
    this.setData({
      selectedWord: word,
      noteText: word.note || '',
      showWordDetail: true
    })
  },

  closeWordDetail() {
    this.setData({ showWordDetail: false, noteText: '' })
  },

  onNoteInput(e) {
    this.setData({ noteText: e.detail.value })
  },

  /** 标记单词为已掌握 */
  async markAsMastered() {
    const userId = getUserId()
    if (!userId) return
    const { bookId, selectedWord } = this.data

    try {
      wx.showLoading({ title: '标记中...' })
      await bookWordAPI.markAsMastered(userId, bookId, selectedWord.id)
      wx.hideLoading()
      wx.showToast({ title: '已标记为掌握', icon: 'success' })
      this.setData({
        'selectedWord.mastered': true,
        showWordDetail: false
      })
      this.loadWords()
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: '标记失败', icon: 'none' })
    }
  },

  /** 保存单词笔记 */
  async saveNote() {
    const userId = getUserId()
    if (!userId) return
    const { bookId, selectedWord, noteText } = this.data
    if (!noteText.trim()) {
      wx.showToast({ title: '请输入笔记内容', icon: 'none' })
      return
    }

    try {
      wx.showLoading({ title: '保存中...' })
      await bookWordAPI.addNote(userId, bookId, selectedWord.id, noteText.trim())
      wx.hideLoading()
      wx.showToast({ title: '笔记已保存', icon: 'success' })
      this.setData({
        'selectedWord.note': noteText.trim(),
        showWordDetail: false
      })
      this.loadWords()
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: '保存失败', icon: 'none' })
    }
  },

  showAddWordModal() {
    this.setData({
      showModal: true,
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

  closeModal() {
    this.setData({ showModal: false })
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
    const { bookId, wordForm } = this.data

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
      await vocabAPI.addWordToBook(bookId, wordData)
      wx.hideLoading()
      wx.showToast({ title: '添加成功', icon: 'success' })
      this.closeModal()
      this.loadWords()
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: '添加失败', icon: 'none' })
    }
  },

  showEditWordModal(e) {
    const word = e.currentTarget.dataset.word
    this.setData({
      editWordId: word.id,
      showEditModal: true,
      editForm: {
        wordText: word.wordText || '',
        phonetic: word.phonetic || '',
        partOfSpeech: word.partOfSpeech || '',
        definition: word.definition || '',
        exampleSentence: word.exampleSentence || '',
        exampleTranslation: word.exampleTranslation || ''
      }
    })
  },

  closeEditModal() {
    this.setData({ showEditModal: false })
  },

  handleEditInput(e) {
    const field = e.currentTarget.dataset.field
    const value = e.detail.value
    this.setData({ [`editForm.${field}`]: value })
  },

  async submitEditWord() {
    const { editWordId, editForm } = this.data

    if (!editWordId) {
      wx.showToast({ title: '单词ID不存在', icon: 'none' })
      return
    }

    if (!editForm.wordText.trim()) {
      wx.showToast({ title: '请输入单词', icon: 'none' })
      return
    }

    if (!editForm.definition.trim()) {
      wx.showToast({ title: '请输入释义', icon: 'none' })
      return
    }

    try {
      wx.showLoading({ title: '保存中...' })
      await wordAPI.updateWord(editWordId, editForm)
      wx.hideLoading()
      wx.showToast({ title: '修改成功', icon: 'success' })
      this.closeEditModal()
      this.loadWords()
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: '修改失败', icon: 'none' })
    }
  },

  deleteWord(e) {
    const word = e.currentTarget.dataset.word
    const { bookId } = this.data

    wx.showModal({
      title: '确认删除',
      content: `确定要删除单词"${word.wordText}"吗？`,
      confirmColor: '#f5576c',
      success: async (res) => {
        if (res.confirm) {
          try {
            wx.showLoading({ title: '删除中...' })
            await vocabAPI.removeWordFromBook(bookId, word.id)
            wx.hideLoading()
            wx.showToast({ title: '删除成功', icon: 'success' })
            this.loadWords()
          } catch (err) {
            wx.hideLoading()
            wx.showToast({ title: '删除失败', icon: 'none' })
          }
        }
      }
    })
  },

  toggleBatchMode() {
    this.setData({
      batchMode: !this.data.batchMode,
      selectedWords: {},
      selectedCount: 0
    })
  },

  toggleWordSelection(e) {
    const word = e.currentTarget.dataset.word
    const wordId = word.id || word.wordText
    const { selectedWords, selectedCount } = this.data

    const newSelected = { ...selectedWords }
    if (newSelected[wordId]) {
      delete newSelected[wordId]
    } else {
      newSelected[wordId] = true
    }

    this.setData({
      selectedWords: newSelected,
      selectedCount: Object.keys(newSelected).length
    })
  },

  toggleSelectAll() {
    const { filteredWords, selectedCount } = this.data

    if (selectedCount === filteredWords.length && filteredWords.length > 0) {
      this.setData({ selectedWords: {}, selectedCount: 0 })
    } else {
      const newSelected = {}
      filteredWords.forEach(word => {
        newSelected[word.id || word.wordText] = true
      })
      this.setData({
        selectedWords: newSelected,
        selectedCount: filteredWords.length
      })
    }
  },

  batchDeleteWords() {
    const { selectedWords, selectedCount, bookId } = this.data

    if (selectedCount === 0) {
      wx.showToast({ title: '请先选择要删除的单词', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认批量删除',
      content: `确定要删除选中的 ${selectedCount} 个单词吗？`,
      confirmColor: '#f5576c',
      success: async (res) => {
        if (res.confirm) {
          try {
            wx.showLoading({ title: '删除中...' })
            const wordIds = Object.keys(selectedWords).map(id => parseInt(id))
            await Promise.all(wordIds.map(wordId =>
              vocabAPI.removeWordFromBook(bookId, wordId)
            ))
            wx.hideLoading()
            wx.showToast({ title: `成功删除 ${wordIds.length} 个单词`, icon: 'success' })
            this.setData({
              batchMode: false,
              selectedWords: {},
              selectedCount: 0
            })
            this.loadWords()
          } catch (err) {
            wx.hideLoading()
            wx.showToast({ title: '批量删除失败', icon: 'none' })
          }
        }
      }
    })
  }
})
