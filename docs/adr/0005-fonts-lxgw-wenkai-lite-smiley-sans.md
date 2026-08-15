# 字体体系：霞鹜文楷 Lite 正文 + 得意黑标题

正文用霞鹜文楷 Lite（OFL，lxgw/LxgwWenKai-Lite v1.522，13.9MB），品牌标题用得意黑 Smiley Sans（OFL，v2.0.1，2.6MB），均打包进 `res/font`。选择打包而非 Google Fonts Provider：中文字体 5MB+，按需下载的首次体验劣于直装。两个取舍需要记录：文楷 Lite 只含常用字，生僻字缺失字形会静默回退系统字体，这是刻意控制体积的结果；得意黑只有一个斜体字重，仅用于 headline/titleLarge；文楷仅打包 Regular 字重，M3 中 500 字重的样式（titleMedium、label 系列）以 400 呈现。
