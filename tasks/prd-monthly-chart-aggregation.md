# PRD: 计时历史图表按月聚合展示

## Introduction

当前计时历史页面的柱状图始终按「天」粒度展示计时次数。当选择「6个月」或「1年」过滤器时，数据量过大导致图表只显示最后 14 天（`takeLast(14)`），用户无法看到完整时间段的趋势。

本功能将图表的聚合粒度与时间范围匹配：7天/30天按天展示，6个月/1年按月展示（每月计时次数 = 该月所有天的计时次数之和）。

## Goals

- 6个月和1年视图下，柱状图按月聚合，每根柱子代表一个月的总计时次数
- X 轴标签简洁可读，格式为「1月」「2月」...「12月」（省略年份）
- 7天和30天的按天展示逻辑保持不变
- 下方历史列表不受影响，始终显示该时间段内所有计时记录

## User Stories

### US-001: ViewModel 按月聚合数据
**Description:** As a user, I need the chart data to aggregate by month when I select 6-month or 1-year filters, so I can see monthly trends.

**Acceptance Criteria:**
- [ ] 新增 `filteredTimerCountByMonth` StateFlow，当 filterDays >= 180 时返回按月聚合的 `DateCountPair` 列表
- [ ] 每月的 count = 该月所有天的计时次数之和
- [ ] `DateCountPair.date` 格式为 `"yyyy-MM"`，用于排序；显示时截取为 `"M月"` 格式
- [ ] 月份按时间升序排列
- [ ] filterDays < 180 时该 Flow 发射空列表（由 UI 决定不使用）
- [ ] Build 通过

### US-002: 图表组件支持月视图
**Description:** As a user, I want the bar chart to switch between daily and monthly display based on the selected filter, so the chart is readable at any time range.

**Acceptance Criteria:**
- [ ] `TimerBarChart` 接收一个 `isMonthly: Boolean` 参数（或通过数据格式自动判断）
- [ ] 月视图下 X 轴标签显示 "1月"、"2月" 等格式（从 `yyyy-MM` 截取）
- [ ] 月视图下柱子数量上限放宽到 12 根（6个月最多 6 根，1年最多 12 根），不需要 takeLast 截断
- [ ] 天视图逻辑完全不变（超过 14 根时 takeLast(14)）
- [ ] Build 通过

### US-003: UI 层切换数据源
**Description:** As a user, I want the chart to automatically show monthly data when I select 6-month or 1-year filter.

**Acceptance Criteria:**
- [ ] 选择 7天 或 30天 时，图表使用 `filteredTimerCountByDate`（按天）
- [ ] 选择 6个月 或 1年 时，图表使用 `filteredTimerCountByMonth`（按月）
- [ ] 切换过滤器时图表平滑更新，无闪烁
- [ ] 下方历史列表始终使用 `filteredTimerSessions`，不受聚合粒度影响
- [ ] Build 通过

## Functional Requirements

- FR-1: 当 `timerFilterDays >= 180`（6个月/1年）时，图表数据按月聚合
- FR-2: 月聚合逻辑：将同一 `yyyy-MM` 的所有 `DateCountPair.count` 求和
- FR-3: 月视图 X 轴标签格式为 `"M月"`（如 "1月"、"12月"），不显示年份
- FR-4: 月视图无 14 根柱子限制（最多 12 根，空间足够）
- FR-5: 天视图（7天/30天）保持现有逻辑不变，包括 14 根柱子限制
- FR-6: 历史列表不受图表聚合影响，始终展示完整记录

## Non-Goals

- 不改变 7天 和 30天 的按天展示逻辑
- 不改变历史列表的展示方式
- 不支持用户手动切换天/月粒度（由过滤器自动决定）
- 不涉及柱状图的交互（点击柱子查看详情等）
- 不涉及总计时时间的月聚合展示

## Technical Considerations

### 现有数据流
```
_timerFilterDays → getSessionsAfter() → _sessionsInTimeRange
                                                  ↓
                                    combine(tag) → filteredTimerSessions (列表)
                                    combine(tag) → filteredTimerCountByDate (图表)
```

### 改造方案
在 ViewModel 中新增 `filteredTimerCountByMonth`，与 `filteredTimerCountByDate` 平行：
```kotlin
val filteredTimerCountByMonth: StateFlow<List<DateCountPair>> = combine(
    _sessionsInTimeRange, _timerFilterTag
) { sessions, tag ->
    val filtered = if (tag.isNullOrBlank()) sessions else sessions.filter { it.tag == tag }
    filtered
        .groupBy { session ->
            SimpleDateFormat("yyyy-MM", Locale.US).format(Date(session.timestamp))
        }
        .map { (month, list) -> DateCountPair(month, list.size) }
        .sortedBy { it.date }
}.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

UI 层根据 `timerFilterDays` 选择数据源：
```kotlin
val chartData = if (currentFilter >= 180) filteredTimerCountByMonth else filteredTimerCountByDate
```

### 需修改的文件
| 文件 | 改动 |
|------|------|
| `HeartRateViewModel.kt` | 新增 `filteredTimerCountByMonth` StateFlow |
| `TimerHistoryScreen.kt` | 图表根据 filterDays 切换数据源；月视图 X 轴标签格式；取消月视图 14 根限制 |

## Success Metrics

- 6个月视图下图表展示 6 根柱子（每月一根）
- 1年视图下图表展示 12 根柱子（每月一根）
- X 轴标签为 "1月"~"12月" 格式
- 7天/30天视图行为与修改前完全一致
