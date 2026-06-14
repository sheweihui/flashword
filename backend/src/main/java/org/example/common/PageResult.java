package org.example.common;

import lombok.Data;

import java.util.List;

/**
 * 简单分页结果类（不依赖MyBatis-Plus）
 */
@Data
public class PageResult<T> {
    
    /**
     * 总记录数
     */
    private long total;
    
    /**
     * 当前页码
     */
    private long current;
    
    /**
     * 每页数量
     */
    private long size;
    
    /**
     * 总页数
     */
    private long pages;
    
    /**
     * 数据列表
     */
    private List<T> records;
    
    public PageResult() {
    }
    
    public PageResult(long total, long current, long size, List<T> records) {
        this.total = total;
        this.current = current;
        this.size = size;
        this.records = records;
        this.pages = (total + size - 1) / size; // 计算总页数
    }
}
