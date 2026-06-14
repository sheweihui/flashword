package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.entity.PublicWord;
import org.example.entity.StoreProduct;

import java.util.List;

/**
 * 商店商品Mapper接口
 */
@Mapper
public interface StoreProductMapper {
    
    /**
     * 插入商品
     */
    int insert(StoreProduct product);
    
    /**
     * 根据ID查询
     */
    StoreProduct selectById(@Param("id") Long id);
    
    /**
     * 查询所有上架商品
     */
    List<StoreProduct> selectAllActive();
    
    /**
     * 根据商品类型查询
     */
    List<StoreProduct> selectByType(@Param("productType") Integer productType);
    
    /**
     * 查询热门商品
     */
    List<StoreProduct> selectHotProducts();
    
    /**
     * 查询新品
     */
    List<StoreProduct> selectNewProducts();
    
    /**
     * 查询推荐商品
     */
    List<StoreProduct> selectRecommended();
    
    /**
     * 更新商品
     */
    int update(StoreProduct product);
    
    /**
     * 更新销售数量
     */
    int updateSalesCount(@Param("id") Long id, @Param("salesCount") Integer salesCount);

    /**
     * 根据单词书ID查询单词列表
     */
    List<PublicWord> selectWords(@Param("id") Long id);

    /**
     * 统计单词书的单词数量
     */
    int countWordsByBookId(@Param("id") Long id);

    /**
     * 条件分页查询
     */
    List<StoreProduct> selectByFilter(@Param("category") String category,
                                      @Param("difficulty") Integer difficulty,
                                      @Param("orderBy") String orderBy,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    /**
     * 条件计数
     */
    long countByFilter(@Param("category") String category,
                       @Param("difficulty") Integer difficulty);
}
