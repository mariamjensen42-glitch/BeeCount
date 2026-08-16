# 本地直连 DeepSeek，用户自带 API Key

App 直接调用 `https://api.deepseek.com`，API Key 由用户在设置页自行填写、以明文存于本地 DataStore，**不设后端代理**。原因是这是个人自用应用，用户接受"Key 只在自己设备上、风险自担"的边界；搭建 Cloudflare Worker 之类的代理虽能隐藏 Key，但对单人使用是部署与维护负担，性价比不足。若未来将应用分发给他人使用，必须重新评估此决策（明文存储的 Key 和直连模式都不再合适）。
