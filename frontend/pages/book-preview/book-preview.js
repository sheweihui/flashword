const { storeAPI } = require('../../utils/api')

Page({
  data: {
    bookId: null,
    bookName: '',
    words: [],
    filteredWords: [],
    searchKeyword: '',
    isLoading: true
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

  loadWords() {
    if (!this.data.bookId) return
    this.setData({ isLoading: true })

    storeAPI.getBookWords(this.data.bookId).then(res => {
      const words = res && res.code === 200 ? res.data : []
      this.setData({
        words: words,
        filteredWords: words,
        isLoading: false
      })
    }).catch(() => {
      this.setData({ isLoading: false })
    })
  },

  onSearchInput(e) {
    const keyword = e.detail.value.toLowerCase()
    const { words } = this.data

    if (!keyword) {
      this.setData({ filteredWords: words, searchKeyword: '' })
      return
    }

    const filtered = words.filter(word =>
      word.wordText.toLowerCase().includes(keyword) ||
      (word.definition && word.definition.toLowerCase().includes(keyword))
    )

    this.setData({
      filteredWords: filtered,
      searchKeyword: keyword
    })
  },

  goBack() {
    wx.navigateBack()
  }
})
