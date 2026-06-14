package org.example.service;

import org.example.common.PageResult;
import org.example.dto.FlashSaleDTO;
import org.example.dto.StoreBookQueryDTO;
import org.example.entity.PublicWord;
import org.example.vo.StoreBookVO;

import java.util.List;

/**
 * 商店服务接口
 */
public interface StoreService {

    /**
     * 分页查询商店单词书列表
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageResult<StoreBookVO> queryStoreBooks(StoreBookQueryDTO queryDTO);

    /**
     * 获取单词书详情
     * @param id 单词书ID
     * @return 单词书详情
     */
    StoreBookVO getBookDetail(Long id);

    /**
     * 购买单词书
     * @param userId 用户ID
     * @param storeBookId 商店单词书ID
     * @return 用户单词书ID
     */
    Long purchaseBook(Long userId, Long storeBookId);

    /**
     * 检查用户是否已购买
     * @param userId 用户ID
     * @param storeBookId 商店单词书ID
     * @return 是否已购买
     */
    Boolean isPurchased(Long userId, Long storeBookId);

    /**
     * 查询单词书的单词列表
     * @param id 单词书ID
     * @return 单词列表
     */
    List<PublicWord> queryBookWords(Long id);

    /**
     * 查询单词书的单词数量
     * @param id 单词书ID
     * @return 单词数量
     */
    Integer queryBookWord(Long id);

    /**
     * 查询秒杀商品列表
     * @return 秒杀商品列表
     */
    List<FlashSaleDTO> queryFlashSaleList();

    /**
     * 秒杀购买
     * @param userId 用户ID
     * @param id 秒杀活动ID
     * @return 秒杀订单ID
     */
    Long flashsale(Long userId, Long id);
}
