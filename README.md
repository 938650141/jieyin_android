# 戒瘾助手 (JieYin Android)

戒除成瘾的辅助健康统计Android工具，通过日常记录数据来观察到目前成瘾的戒除情况。

## 功能特性

- ✅ **评分系统**: 基于科学算法计算0-100分的戒瘾状态评分
- 📊 **四级评估**: 严重成瘾、中度成瘾、轻度成瘾、已戒除四个等级
- 📝 **五类记录**: 支持记录成功、失败、读书、运动、睡眠五种活动
- 💾 **本地存储**: 数据保存在本地，保护用户隐私
- 📱 **简洁界面**: Material Design风格，操作简单直观
- 📈 **历史记录**: 查看最近的活动记录

## 核心算法

### 评分等级
- **0-59.99分**: 严重成瘾 (红色)
- **60-79.99分**: 中度成瘾 (橙色) 
- **80-94.99分**: 轻度成瘾 (绿色)
- **95-100分**: 已戒除 (蓝色)

### 计分规则
- **基础分**: 60分
- **成功**: 根据时间间隔加分 (每天+2分，上限10分)
- **失败**: 根据次数和密集度扣分 (基础-5分，越频繁扣分越多)
- **读书**: 每30分钟+1.5分
- **运动**: 每30分钟+2分
- **睡眠**: 7-9小时+2分，不足或过多会扣分

详细算法说明请查看 [ALGORITHM.md](ALGORITHM.md)

## 项目结构

```
app/src/main/java/com/jieyin/addiction/
├── model/                    # 数据模型
│   └── Models.kt            # ActivityType, ActivityRecord, ScoreLevel
├── algorithm/               # 核心算法
│   └── AddictionScoreCalculator.kt  # 评分计算器
├── storage/                 # 数据存储
│   └── ActivityStorage.kt   # SharedPreferences存储管理
└── MainActivity.kt          # 主界面
```

## 构建项目

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- Android SDK 24 (Android 7.0) 或更高版本
- Kotlin 1.9.0

### 构建步骤
1. 克隆仓库
```bash
git clone https://github.com/938650141/jieyin_android.git
cd jieyin_android
```

2. 用Android Studio打开项目

3. 等待Gradle同步完成

4. 运行项目到模拟器或真机

## 运行测试

```bash
./gradlew test
```

## 技术栈

- **语言**: Kotlin
- **UI框架**: Android View + Material Components
- **数据存储**: SharedPreferences + Gson
- **架构**: 简单的MVC模式
- **测试**: JUnit 4

## 使用说明

1. **记录成功**: 当你成功抵抗诱惑时点击"记录成功"
2. **记录失败**: 当你屈服于诱惑时点击"记录失败"  
3. **记录读书**: 输入今天读书的时长（分钟）
4. **记录运动**: 输入今天运动的时长（分钟）
5. **记录睡眠**: 输入昨晚的睡眠时长（分钟）
6. **查看评分**: 主界面会实时显示当前评分和等级
7. **查看历史**: 可以看到最近10条活动记录

## 贡献

欢迎提交Issue和Pull Request来改进这个项目！

## 许可证

MIT License

## 联系方式

如有问题，请在GitHub上提Issue。
