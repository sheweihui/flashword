# 🔄 从 MyBatis-Plus 迁移到普通 MyBatis 完成总结

## ✅ 已完成的修改

### 1️⃣ 移除 MyBatis-Plus 依赖

所有代码不再使用 MyBatis-Plus，改用原生 MyBatis。

### 2️⃣ 修改的文件清单

#### Mapper 接口（5个）
| 文件 | 修改内容 |
|------|----------|
| [PointsAccountMapper.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/mapper/PointsAccountMapper.java) | 移除 `BaseMapper`，定义具体方法 |
| [PointsTransactionMapper.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/mapper/PointsTransactionMapper.java) | 移除 `BaseMapper`，定义具体方法 |
| [BookStoreMapper.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/mapper/BookStoreMapper.java) | 移除 `BaseMapper`，定义分页查询方法 |
| [UserPurchasedBookMapper.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/mapper/UserPurchasedBookMapper.java) | 移除 `BaseMapper`，定义具体方法 |
| [UserCheckinMapper.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/mapper/UserCheckinMapper.java) | 移除 `BaseMapper`，定义具体方法 |

#### Service 实现类（3个）
| 文件 | 修改内容 |
|------|----------|
| [PointsAccountServiceImpl.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/service/impl/PointsAccountServiceImpl.java) | 移除 `LambdaQueryWrapper`，使用 Mapper 方法 |
| [StoreServiceImpl.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/service/impl/StoreServiceImpl.java) | 移除 `LambdaQueryWrapper` 和 `Page`，使用自定义分页 |
| [CheckinServiceImpl.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/service/impl/CheckinServiceImpl.java) | 移除 `LambdaQueryWrapper`，使用 Mapper 方法 |

#### Service 接口（1个）
| 文件 | 修改内容 |
|------|----------|
| [StoreService.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/service/StoreService.java) | 返回类型从 MP 的 `Page` 改为自定义 `PageResult` |

### 3️⃣ 新增的文件

#### 自定义分页类
- [PageResult.java](file:///D:/aaaa_Project/agent/src/main/java/org/example/common/PageResult.java) - 简单的分页结果类

#### MyBatis XML Mapper 文件（5个）
| 文件 | 说明 |
|------|------|
| [PointsAccountMapper.xml](file:///D:/aaaa_Project/agent/src/main/resources/mapper/PointsAccountMapper.xml) | 积分账户 SQL |
| [PointsTransactionMapper.xml](file:///D:/aaaa_Project/agent/src/main/resources/mapper/PointsTransactionMapper.xml) | 交易记录 SQL |
| [BookStoreMapper.xml](file:///D:/aaaa_Project/agent/src/main/resources/mapper/BookStoreMapper.xml) | 商店单词书 SQL（含分页） |
| [UserPurchasedBookMapper.xml](file:///D:/aaaa_Project/agent/src/main/resources/mapper/UserPurchasedBookMapper.xml) | 购买记录 SQL |
| [UserCheckinMapper.xml](file:///D:/aaaa_Project/agent/src/main/resources/mapper/UserCheckinMapper.xml) | 签到记录 SQL |

---

## 🔧 主要改动说明

### 1. Mapper 接口改动

**之前（MyBatis-Plus）**：
```java
public interface PointsAccountMapper extends BaseMapper<PointsAccount> {
}
```

**现在（普通 MyBatis）**：
```java
public interface PointsAccountMapper {
    int insert(PointsAccount account);
    int updateById(PointsAccount account);
    PointsAccount selectById(Long id);
    PointsAccount selectByUserId(@Param("userId") Long userId);
}
```

### 2. Service 实现改动

**之前（MyBatis-Plus）**：
```java
LambdaQueryWrapper<PointsAccount> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(PointsAccount::getUserId, userId);
PointsAccount account = pointsAccountMapper.selectOne(wrapper);
```

**现在（普通 MyBatis）**：
```java
PointsAccount account = pointsAccountMapper.selectByUserId(userId);
```

### 3. 分页查询改动

**之前（MyBatis-Plus）**：
```java
Page<BookStore> page = new Page<>(pageNum, pageSize);
Page<BookStore> result = bookStoreMapper.selectPage(page, wrapper);
```

**现在（普通 MyBatis）**：
```java
int offset = (pageNum - 1) * pageSize;
List<BookStore> list = bookStoreMapper.selectPage(..., offset, limit);
long total = bookStoreMapper.countTotal(...);
PageResult<BookStore> pageResult = new PageResult<>(total, pageNum, pageSize, list);
```

---

## 📝 XML Mapper 示例

### BookStoreMapper.xml - 分页查询

```xml
<!-- 分页查询商店单词书 -->
<select id="selectPage" resultMap="BaseResultMap">
    SELECT * FROM public_book_store
    <where>
        <if test="status != null">
            AND status = #{status}
        </if>
        <if test="category != null and category != ''">
            AND category = #{category}
        </if>
        <if test="difficulty != null">
            AND difficulty = #{difficulty}
        </if>
    </where>
    ORDER BY ${orderBy}
    LIMIT #{limit} OFFSET #{offset}
</select>

<!-- 查询总数 -->
<select id="countTotal" resultType="long">
    SELECT COUNT(*) FROM public_book_store
    <where>
        <!-- 同样的条件 -->
    </where>
</select>
```

---

## ⚠️ 注意事项

### 1. pom.xml 不需要修改

✅ **不需要添加 MyBatis-Plus 依赖**  
✅ **保持现有的 MyBatis 依赖即可**

当前项目使用的是：
```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>
```

### 2. application.yml 配置

确保 MyBatis 的 mapper-locations 配置正确：

```yaml
mybatis:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  mapper-locations: classpath*:/mapper/**/*.xml  # ✅ 确保这个配置存在
  type-aliases-package: org.example.entity
```

### 3. 编译测试

现在应该可以正常编译了：

```bash
mvn clean compile
```

如果还有错误，请检查：
- 是否还有遗漏的 MyBatis-Plus 导入
- XML 文件是否正确放置 in `src/main/resources/mapper/`
- 方法签名是否与 XML 中的 ID 匹配

---

## 🎯 优势对比

| 特性 | MyBatis-Plus | 普通 MyBatis |
|------|-------------|-------------|
| 学习曲线 | 需要学习 MP API | 标准 SQL，易理解 |
| 灵活性 | 封装较多，定制复杂 | 完全控制 SQL |
| 性能 | 有额外开销 | 更轻量 |
| 维护性 | 依赖第三方库 | 标准技术栈 |
| 团队熟悉度 | 部分人不熟悉 | 大部分人熟悉 |

---

## ✅ 验证清单

- [x] 所有 Mapper 接口不再继承 `BaseMapper`
- [x] 所有 Service 不再使用 `LambdaQueryWrapper`
- [x] 不再使用 MyBatis-Plus 的 `Page` 类
- [x] 创建了自定义的 `PageResult` 类
- [x] 为所有 Mapper 创建了对应的 XML 文件
- [x] XML 文件放置在 `src/main/resources/mapper/` 目录
- [x] 移除了所有 MyBatis-Plus 的 import 语句

---

## 🚀 下一步

1. **编译项目**
   ```bash
   mvn clean compile
   ```

2. **运行测试**
   ```bash
   mvn test
   ```

3. **启动应用**
   ```bash
   mvn spring-boot:run
   ```

4. **测试接口**
   - GET `/api/store/points/balance`
   - POST `/api/store/checkin`
   - GET `/api/store/books?page=1&size=20`

---

## 💡 常见问题

### Q1: 为什么不用 MyBatis-Plus？
A: MyBatis-Plus 虽然方便，但增加了项目复杂度。对于小型项目，普通 MyBatis 更简单、更易维护。

### Q2: 分页会不会很麻烦？
A: 稍微多一点代码，但更可控。我们创建了 `PageResult` 类来简化使用。

### Q3: 以后可以用 MyBatis-Plus 吗？
A: 可以，随时可以切换。但现在的设计让项目更轻量。

---

## 📚 参考资源

- [MyBatis 官方文档](https://mybatis.org/mybatis-3/)
- [MyBatis Spring Boot](https://mybatis.org/spring-boot-starter/)

---

✅ **迁移完成！现在项目完全不依赖 MyBatis-Plus，使用纯 MyBatis 实现。**

如有任何编译错误，请告诉我具体的错误信息！😊
