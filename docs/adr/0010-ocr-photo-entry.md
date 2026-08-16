# OCR 拍照记账：ML Kit 本地识别 + DeepSeek 结构化解析

OCR 记账是继 AI 文字记账之后的第二条输入通道：用户从相册选取支付宝/微信/云闪付截图，本地 ML Kit 提取文字，再复用现有 DeepSeek 解析通道，最终走同一套确认卡片流程入库。三个核心决策需要记录。

**引擎选 ML Kit Text Recognition v2，不裸用 TFLite 模型**：ML Kit 底层就是 TFLite，Google 维护，中文识别开箱即用，API 返回结构化文字块（TextBlock → Line → Element），无网络请求。替代方案是手动打包 PaddleOCR 等 `.tflite` 文件并自写预处理——工程量 5–10 倍且识别质量无优势，没有理由自己造这个轮子。

**后处理复用 ParseEntryUseCase，不写平台专属正则**：ML Kit 拿回的原始文字直接送进已有的 `ParseEntryUseCase`（DeepSeek）。拒绝的方案是为支付宝、微信、云闪付各写一套 Regex 规则——三个平台随时改 UI，规则悄悄失效且要分别维护；AI 对格式变化有天然容错。调用时传 `isOcrInput = true`，system prompt 末尾追加「以下输入来自支付截图的 OCR 文字，请从中提取收支信息」，给模型明确上下文，提升罕见截图格式的鲁棒性。

**架构：新增 OcrEntryUseCase，Screen 用 PickVisualMedia**：OCR 链路（Uri → ML Kit → 文字 → ParseEntryUseCase）封装在独立的 `OcrEntryUseCase` 中，通过 Hilt 注入 `@ApplicationContext`，保持 ViewModel 薄且可单测。图库选图使用 `ActivityResultContracts.PickVisualMedia(ImageOnly)`，manifest 无需任何权限声明（系统临时授权所选 Uri）。ML Kit 返回文字去除空白后不足 15 字符时视为识别失败，在对话流中插入 `AssistantMessage.Assistant` 气泡提示，与现有解析错误的呈现方式一致。v1 仅支持相册选图；相机实时拍留待 v2。
