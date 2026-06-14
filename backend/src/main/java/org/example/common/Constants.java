package org.example.common;

/**
 * 常量类
 */
public class Constants {
    
    /**
     * 操作类型常量
     */
    public static class ActionType {
        /**
         * 添加到生词本
         */
        public static final int ADD_TO_VOCABULARY = 1;
        
        /**
         * 复习
         */
        public static final int REVIEW = 2;
        
        /**
         * 标记掌握
         */
        public static final int MARK_MASTERED = 3;
        
        /**
         * 移除
         */
        public static final int REMOVE = 4;
    }
    
    /**
     * 掌握状态常量
     */
    public static class MasteredStatus {
        /**
         * 未掌握
         */
        public static final int NOT_MASTERED = 0;
        
        /**
         * 已掌握
         */
        public static final int MASTERED = 1;
    }
    
    /**
     * 学习结果常量
     */
    public static class StudyResult {
        /**
         * 正确
         */
        public static final int CORRECT = 1;
        
        /**
         * 错误
         */
        public static final int WRONG = 2;
    }
}
