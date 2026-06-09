package com.companion.chat.data.context.tokenizer

/**
 * 轻量级中文分词器
 * 使用内置词典 + 正向最大匹配算法
 */
object SimpleChineseTokenizer {

    // 常用词词典（按长度分组，便于正向最大匹配）
    private val DICTIONARY = buildDictionary()

    // 常见停用词
    private val STOP_WORDS = setOf(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
        "都", "一", "上", "也", "很", "到", "说", "要", "去", "你",
        "会", "着", "没有", "看", "好", "自己", "这", "他", "她",
        "吗", "那", "么", "什么", "啊", "呢", "吧", "把", "被", "让",
        "给", "从", "向", "对", "以", "所以", "因为", "但是", "然后",
        "如果", "虽然", "可以", "已经", "还是", "或者", "只是", "这个",
        "那个", "这些", "那些", "怎么", "哪", "谁", "多少", "几",
        "个", "中", "大", "小", "多", "少", "上", "下", "左", "右",
        "的", "地", "得", "过", "来", "去", "起来", "下去", "上来"
    )

    // 中文标点符号
    private val PUNCTUATION = setOf(
        "。", "，", "！", "？", "；", "：", "、", """, """,
        "'", "'", "（", "）", "【", "】", "《", "》", "—", "…",
        ".", ",", "!", "?", ";", ":", "'", "'", "(", ")",
        "[", "]", "<", ">", "-", "/", "\\"
    )

    /**
     * 对文本进行分词
     * @return 分词结果列表（已过滤停用词和标点）
     */
    fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val tokens = mutableListOf<String>()
        var i = 0

        while (i < text.length) {
            val char = text[i]

            when {
                // 跳过空白字符
                char.isWhitespace() -> {
                    i++
                }
                // 中文字符：正向最大匹配
                isChinese(char) -> {
                    val matchResult = longestMatch(text, i)
                    if (matchResult != null) {
                        tokens.add(matchResult)
                        i += matchResult.length
                    } else {
                        // 单字
                        tokens.add(char.toString())
                        i++
                    }
                }
                // 英文和数字：按词分组
                char.isLetterOrDigit() -> {
                    val word = extractWord(text, i)
                    tokens.add(word)
                    i += word.length
                }
                // 标点符号：跳过
                char.toString() in PUNCTUATION -> {
                    i++
                }
                // 其他字符
                else -> {
                    i++
                }
            }
        }

        // 过滤停用词和太短的词
        return tokens.filter { token ->
            token.length > 1 && token.lowercase() !in STOP_WORDS
        }.map { it.lowercase() }
    }

    /**
     * 正向最大匹配
     */
    private fun longestMatch(text: String, start: Int): String? {
        val maxLen = minOf(4, text.length - start)
        for (len in maxLen downTo 2) {
            val word = text.substring(start, start + len)
            if (word in DICTIONARY) {
                return word
            }
        }
        return null
    }

    /**
     * 提取英文/数字单词
     */
    private fun extractWord(text: String, start: Int): String {
        val sb = StringBuilder()
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) {
            sb.append(text[i])
            i++
        }
        return sb.toString()
    }

    /**
     * 计算两个文本的词重叠度（Jaccard 相似度）
     */
    fun jaccardSimilarity(text1: String, text2: String): Double {
        val tokens1 = tokenize(text1).toSet()
        val tokens2 = tokenize(text2).toSet()

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        return intersection.toDouble() / union.toDouble()
    }

    private fun isChinese(char: Char): Boolean {
        val ub = Character.UnicodeBlock.of(char)
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
    }

    /**
     * 构建常用词词典
     */
    private fun buildDictionary(): Set<String> {
        return setOf(
            // 2字词
            "喜欢", "吃", "火锅", "今天", "天气", "真好", "生日",
            "温柔", "角色", "编程", "学习", "工作", "生活", "开心",
            "难过", "生气", "害怕", "担心", "期待", "希望", "梦想",
            "朋友", "家人", "父母", "老师", "同学", "同事", "领导",
            "公司", "学校", "医院", "银行", "超市", "餐厅", "公园",
            "电影", "音乐", "游戏", "运动", "旅游", "美食", "书籍",
            "手机", "电脑", "网络", "软件", "程序", "代码", "数据",
            "问题", "答案", "方法", "计划", "目标", "任务", "项目",
            "时间", "地点", "人物", "事件", "原因", "结果", "影响",
            "开始", "结束", "继续", "停止", "暂停", "恢复", "完成",
            "准备", "计划", "安排", "决定", "选择", "考虑", "思考",
            "知道", "了解", "理解", "明白", "记得", "忘记", "想起",
            "告诉", "询问", "回答", "解释", "说明", "介绍", "描述",
            // 3字词
            "计算机", "程序员", "工程师", "设计师", "产品经理",
            "大学生", "研究生", "博士生", "留学生", "毕业生",
            "互联网", "人工智能", "机器学习", "深度学习", "神经网络",
            "数据库", "操作系统", "应用程序", "网站", "平台",
            "图书馆", "博物馆", "电影院", "体育馆", "游乐场",
            "信用卡", "银行卡", "支付宝", "微信", "淘宝",
            "咖啡", "奶茶", "果汁", "啤酒", "饮料",
            "早餐", "午餐", "晚餐", "零食", "甜点",
            // 4字词
            "机器学习", "深度学习", "自然语言", "计算机视觉",
            "数据科学", "人工智能", "软件工程", "系统架构",
            "项目管理", "产品设计", "用户体验", "界面设计",
            "生日快乐", "新年快乐", "节日快乐", "周末愉快",
            "早上好", "下午好", "晚上好", "晚安",
            "谢谢", "不客气", "对不起", "没关系"
        )
    }
}
