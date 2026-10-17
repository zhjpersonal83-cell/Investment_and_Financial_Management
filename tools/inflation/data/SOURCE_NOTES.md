# 通胀数据来源与覆盖说明

采集日期：2026-10-17。数据来源为 Trading Economics 官方各国家／地区的 `Inflation Rate` 页面。币种与当前 IAFM 汇率页一致：USD、GBP、CNY、EUR、JPY、HKD、AUD、CAD、CHF、SGD。

## 文件

- `inflation_monthly_raw.csv`：1096 条已核实月度观测，适合与现有数据库合并。
- `inflation_change_points.csv`：932 条变化点；只折叠日历上连续且数值完全相同的月份，保留较早月份，`observed_through` 保留该段最后一个实际观测月。
- `coverage.json`：每个币种的来源、实际取得区间、条数、缺月和访问限制。
- `raw/visible-chart-evidence.json`：浏览器公开图表显示的文字证据。`history` 是逐点点击后显示的月份和数值；近期的 `axis` / `points` 或 `tooltipRecords` 用于独立一致性校验。
- `build_dataset.py`：从证据文件重建两个 CSV 和覆盖报告；没有联网、数据库写入或用户私有财务数据访问。

在本目录执行 `python3 build_dataset.py` 可复现校验与数据产物。

## 实际取得范围

| 币种 | 区域 | 起始参考月 | 结束参考月 | 原始条数 | 去重后 |
| --- | --- | --- | --- | ---: | ---: |
| USD | 美国 | 2016-10 | 2026-09 | 119 | 108 |
| GBP | 英国 | 2016-09 | 2026-08 | 120 | 100 |
| CNY | 中国 | 2016-10 | 2026-09 | 120 | 108 |
| EUR | 欧元区 | 2016-10 | 2026-09 | 120 | 106 |
| JPY | 日本 | 2016-09 | 2026-08 | 120 | 102 |
| HKD | 香港 | 2016-09 | 2026-08 | 120 | 97 |
| AUD | 澳大利亚 | 2025-04 | 2026-08 | 17 | 16 |
| CAD | 加拿大 | 2016-09 | 2026-08 | 120 | 107 |
| CHF | 瑞士 | 2016-10 | 2026-09 | 120 | 92 |
| SGD | 新加坡 | 2016-09 | 2026-08 | 120 | 96 |

USD 的公开图表没有 2025-10 数据点，保留为缺月，没有填零或推算。AUD 的当前月度系列在公开 `10Y` 图表内从 2025-04 开始，不能拿季度同比补成月度。网站描述中提到的全历史起点没有当作本次数据覆盖起点。

## 采集方法与限制

正常打开官方页面，选择公开可用的 `10Y` 图表按钮，然后逐个点击图表柱形，读取页面实际显示的提示框月份与精确数值。最近一年另外读取图表上可见数字标签及横坐标；澳元使用可见提示框。重建脚本将这批近期证据与十年捕获逐月比对。

英国页面的 `MAX` 按钮明确显示仅订阅者可用；本次没有访问订阅历史、重用受保护接口或绕过访问限制。Trading Economics 的[官方 API 认证说明](https://docs.tradingeconomics.com/get_started/authentication/)也要求订阅与 API key。已公开获得的十年范围之外不声称完整。

## 数据口径

- 所有数值均为总体消费者价格**同比**通胀率。`rate=3.10` 表示 3.10%，不是 0.031，也不是价格指数、环比、预测或核心通胀。
- EUR 使用欧元区 HICP 同比，`measure=headline_hicp_yoy`；其余使用对应地区总体 CPI 同比，`measure=headline_cpi_yoy`。
- 日期按用户要求统一为**参考月份的 15 日**，不是实际发布日期。比如 2026 年 8 月数据写作 `2026-08-15`，即使它在 9 月发布。
- 负值与零都是有效观测，应保留。前端可显示两位小数。
- 相邻日历月值相同才折叠。缺月断开相同值区间；不跨缺月合并，不从无数据状态推断物价不变。
- 没有将当月同比率当作当月实际涨幅。年度通胀调整仍需应用自己的既有模型，不能简单连续相乘每个月的同比百分数。

## 合并建议

导入原始月度表，与既有 GBP/CNY 历史按币种和规范化参考月份匹配。保留本次覆盖之前的现有记录；覆盖内如现有值不同，应记录差异并使用已核实的最新来源值。最后对合并后的**连续月份**做相同值折叠。不要先删除既有 GBP/CNY 全历史再导入这份有限范围。

如果现有数据库本身已经采用变化点存储，不能把相邻存储行当作连续自然月份；需结合实际覆盖／观测信息处理。`observed_through` 与原始表保留了本次压缩前的月份证据。

## 官方页面

- USD：https://tradingeconomics.com/united-states/inflation-cpi
- GBP：https://tradingeconomics.com/united-kingdom/inflation-cpi
- CNY：https://tradingeconomics.com/china/inflation-cpi
- EUR：https://tradingeconomics.com/euro-area/inflation-cpi
- JPY：https://tradingeconomics.com/japan/inflation-cpi
- HKD：https://tradingeconomics.com/hong-kong/inflation-cpi
- AUD：https://tradingeconomics.com/australia/inflation-cpi
- CAD：https://tradingeconomics.com/canada/inflation-cpi
- CHF：https://tradingeconomics.com/switzerland/inflation-cpi
- SGD：https://tradingeconomics.com/singapore/inflation-cpi

本目录不含私有账户流水、数据库口令或任何服务器变更。
