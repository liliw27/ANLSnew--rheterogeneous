# 架构与数据结构说明（建议先读）

本文档面向“在此代码基础上做拓展”的学生，目标是把 **论文概念** 与 **代码实现** 一一对齐，避免在没有理解解表示的情况下直接改算子导致不可行/状态不一致。

---

## 1. 论文模型到代码对象的映射

### 1.1 论文中的关键概念

- **Trip（一次趟次）**：车辆从 depot 出发到下一次回到 depot 之间的访问序列
- **Visit（一次访问）**：给某个客户配送一部分（可能全部）多产品需求的动作
- **Multi-compartment（多舱）**：一个车辆有多个互相隔离的舱室，每个舱一次只能装一种产品
- **Split delivery（可拆分配送）**：同一客户需求允许被拆成多个 visit，分配给不同 trip/车辆
- **Multi-trip（多趟）**：车辆允许多次执行 trip
- **目标函数**：加权最小化 makespan + 总旅行时间（论文式(2)）

### 1.2 代码中的对应

- **车辆**：`model.Vehicle`（`compartmentNum`, `comCapacity`）
- **实例**：`model.Instance`（`distanceMatrix`, `demands`, `vehicles`, `nrProducts`）
- **Visit**：`model.CustomerNode`（`index` + `deliveryQuantity[p]`）
- **Trip**：`model.Segment`
- **车辆 itinerary（含多趟）**：`model.Route`（双向链表）
- **depot 节点**：`model.DepotNode`（`index = 0`）

> 提示（给新手）：需要调实验参数时，优先修改 `SA/SAConfig.java`（或在 `testSA` 里通过命令行参数覆盖，例如 `--timeLimitMs=3000 --seed=1`），不要去改 `SimulatedAnnealing` 里散落的字段。

---

## 2. 解表示：Route + Segment（本项目最关键设计）

### 2.1 Route 是什么？

每辆车对应一个 `Route`，内部用 **双向链表** 表示节点序列：

```
Depot(start) -> ... -> Depot -> ... -> Depot(end)
```

其中中间出现的 `DepotNode` 不是“同一个 depot 重复”，而是用来表达 **车辆在 route 内回 depot 补给并开始下一趟** 的“分段断点”。

### 2.2 Segment 是什么？

`Segment` 表示 Route 中的一段 **trip**。每个节点 `Node` 都有一个 `segment` 指针指向它所属的 trip。

Segment 维护多舱多产品资源状态（对齐论文的“舱室分配/容量约束”）：

- `productUsed[p]`：trip 内配送的产品 p 总量
- `productResidual[p]`：在“按舱容量向上取整”之后的剩余可用量
- `compartmentResidual`：空舱剩余数量

这些变量在 `Segment.addNode/removeNode` 中维护，插入/删除一个 `CustomerNode` 会立刻更新残余容量。

### 2.3 为什么用 DepotNode 来“切 trip”？

在 `Route.insertDepot(prevNode)` 中：

- 在链表里插入一个 `DepotNode`
- 创建一个新的 `Segment`
- 把 `prevNode` 之后仍属于旧 segment 的节点全部迁移到新 segment

这等价于论文中的 **Split Trip Insertion (STI)**：通过在 trip 中间插入一次回 depot，把一趟拆成两趟，从而为插入新的 visit 留出资源与结构空间。

---

## 3. 目标函数与增量评估

### 3.1 全局目标（与论文式(2)一致）

代码使用：

- `makespan = max_k routes[k].duration`
- `sum = Σ_k routes[k].duration`
- `objective = makespan * MAKESPAN_MULTIPLIER + sum`

其中 `MAKESPAN_MULTIPLIER` 默认 100（论文实验中 \(\alpha=100,\beta=1\)）。

### 3.2 插入时的“目标增量”

`move/insertion/EvaluateInsertion.java` 会对“把一个 visit 插到某个位置”计算增量：

- **路程增量**：替换边 \((prev,next)\) 为 \((prev,cust)+(cust,next)\)
- **makespan 惩罚**：如果该 route 的新时长超过旧 makespan，按乘子加入

因此算子在局部比较多个候选插法时能快速排序。

---

## 4. 一次 ALNS + SA 迭代如何执行（论文 Algorithm 1 对齐）

核心类：`SA/SimulatedAnnealing.java`

每次迭代的高层流程是：

1. **选择 destroy 算子**（removal）
2. **选择 repair 算子**（insertion）
3. `removal.move()`：从当前解中移除若干 `CustomerNode`，放入 `sa.unServedNodes`
4. `insertion.move()`：把 `unServedNodes` 按某种策略插回去，得到新解
5. 计算新解目标 `objectiveTentativeSolution`
6. 用 **SA 接受准则** 决定“接受新解”还是“回滚到上一步解”
7. 如果新解成为全局最优，则保存为 `BEST_SOLUTION`
8. 每隔一段迭代（segment）更新算子权重（自适应选择）

### 4.1 SA 接受准则

- 若新解更好（`savings < 0`）：直接接受
- 否则以概率 \(\exp(-\Delta/T)\) 接受（\(\Delta =\) 新旧目标差）

### 4.2 可逆状态（为什么可以“接受/回滚”）

`model/ReversibleDataStructure.java` 提供三套快照：

- `PREVIOUS_SOLUTION`：上一步解（用于本次迭代不接受时回滚）
- `BEST_SOLUTION`：历史最优解
- `INIT_SOLUTION`：初始解（用于 restart/实验）

`SimulatedAnnealing.createRestorePoint(...)` 会递归地为：

- `routes[]`（每辆车的 `Route`）
- route 链表上的每个 `Node`
- `DepotNode` 持有的 `Segment`（其状态里包含 `productUsed/productResidual/compartmentResidual`）

建立快照，保证回滚后链表指针、segment 资源状态一致。

### 4.3 调试时的两个“容易踩坑”的副作用

- **`printBestRoutes()` 会改变当前解**：它会先 `restoreEarlierState(BEST_SOLUTION)` 再打印，这对调试很方便，但如果你在迭代中途调用它，会把当前 working solution 直接覆盖成 best solution。
- **控制台输出默认应当安静**：为了方便学生批量跑实验，`SimulatedAnnealing` 里有 `VERBOSE_LOG`（默认关闭）。需要追踪中间过程时再临时打开。

---

## 5. Repair（插入）策略：II / SI / STI

插入策略的统一入口：`move/insertion/EvaluateInsertion.java`

它会把当前 schedule 的所有 `Segment` 分成两类：

- **nonSplitSegments**：该 segment 的残余资源足以一次性满足该 visit（可用 II）
- **splitSegments**：无法一次性满足，但可能提供部分供给（可用 SI 或 STI）

然后在三种策略中选“目标增量最小”的：

- **II（Integral Insertion）**：`INSERT_NODE`
  - 直接把 `CustomerNode` 插入某个 segment 的某个位置
- **SI（Split Insertion）**：`INSERT_SPLITDELIVERY`
  - 把一个 `CustomerNode` 拆成多个 `CustomerNode`（配送向量相加仍等于原需求）
  - 分散插入多个 segment
- **STI（Split Trip Insertion）**：`INSERT_MULTITRIPID / INSERT_MULTITRIPDI`
  - 通过 `Route.insertDepot(...)` 在链表里插入 `DepotNode`，从而“拆 trip / 新开 trip”
  - 再插入该 visit

> 备注：当 repair 插入导致同一客户在同一 trip 内出现相邻重复 visit 时，部分插入算子末尾会做合并（避免碎片化）。

---

## 6. Destroy（移除）算子

destroy 算子位于 `move/removal/`，典型包括：

- `RandomRemoval`：随机移除若干 visit
- `ShawRemoval`：按“距离 + 需求向量差 + 是否同一 trip”的相似性迭代移除
- `WorstRemoval`：迭代移除“对目标贡献最大”的 visit
- `LongestRouteRemoval`：移除 makespan 路线上（最长 route）的 visit

这些算子共同特点：

- 只负责把一批 `CustomerNode` 从 `Route` 删除，并放入 `sa.unServedNodes`
- **不负责**把它们插回去（由 repair 做）

---

## 7. 初始解（InitialConstructor）

`SA/InitialConstructor.java` 负责从需求矩阵构造一个可行初解，核心思路是：

- 根据车辆舱室总容量，把大需求拆成若干“直达（depot->customer->depot）”的 visit
- 余量需求形成 reduced instance，再用插入评估逐个插入

这里会生成三类节点集合（阅读代码时建议学生先画图理解）：

- **fullDirectNodes**：满载直达
- **unFullDirectNodes**：非满载但直达（仍作为单独 trip）
- **reducedInstanceNodes**：剩余需求（需要插入到已有 trip 或触发拆 trip）

---

## 8. 不变量（写新算子前必须遵守）

建议学生在改动任何插入/移除逻辑之前，先理解并遵守以下不变量：

- **链表一致性**：`prev/next` 指针必须互相匹配，route 的 `routeStart/routeEnd` 必须可达
- **Route 时长一致性**：`Route.duration` 必须等于链表相邻节点距离之和
- **Segment 资源一致性**：`Segment.productUsed/productResidual/compartmentResidual` 必须与 segment 内实际客户配送量匹配
- **目标一致性**：`currentObjective` 必须与 `routes[]` 的 duration 计算出的 makespan+sum 相一致

代码里 `SimulatedAnnealing.verifyRouteState()` 会做上述一致性检查，是最重要的“自检器”。

### 8.1 `verifyRouteState()` 实际检查了哪些点？

在 `SA/SimulatedAnnealing.java` 中，`verifyRouteState()` 会逐条验证：

- **Route.duration**：沿链表累加 `distanceMatrix[node.index][node.next.index]`，必须等于 `Route.duration`
- **Route.nrNodes**：从 `routeStart` 走到 `routeEnd` 的节点数必须等于 `Route.nrNodes`
- **Route.segments**：遍历过程中检测到的 segment 数必须等于 `Route.segments`
- **Segment.productUsed[p]**：必须等于该 segment 内所有 `CustomerNode.deliveryQuantity[p]` 的总和
- **Segment.customersInSegment**：必须等于该 segment 内的节点数（本实现中包含 depot 节点）
- **currentObjective**：必须等于 `makespan * MAKESPAN_MULTIPLIER + sum(route.duration)`

### 8.2 常见“破坏不变量”的方式（学生最常踩坑）

- **只改链表指针，不更新 Segment**：移动/插入节点后忘记调用 `Segment.addNode/removeNode`，导致 `productUsed` 等资源状态错
- **只更新 Segment，不更新 Route 统计字段**：忘记同步 `Route.duration/nrNodes/segments`
- **插入/删除 depot 没处理“段迁移”**：`insertDepot/removeDepot` 的核心是把后续节点迁移到正确 segment（否则 segment 资源就错）
- **残留 route/segment 引用**：删除节点后没清空 `node.route/node.segment`（或恢复点回滚后引用错乱）

### 8.3 调试建议

- 在开发新算子时，保持 `assert verifyRouteState()` 开启
- 一旦报错，先打印当前 route（`printRoutes()`）并定位是哪条 segment 的资源统计不一致

---

## 10. 辅助/遗留工具（不属于主求解链路）

本仓库中有一些仅用于“数据准备/实例生成”的遗留类，例如：

- `src/io/GenerateInstance.java`

它们不参与 `Reader → SimulatedAnnealing` 的主流程；学生如果只做算子/约束扩展，可以先忽略这些文件，避免被与求解无关的 I/O 细节干扰。

---

## 9. 扩展点（学生可以从这里开始改）

### 9.1 新增 destroy 算子

1. 新建类继承 `move.removal.Removal`
2. 实现 `move()`：填充 `sa.unServedNodes` 并从 route 删除对应节点
3. 在 `SimulatedAnnealing` 构造函数中加入 `removalList`

### 9.2 新增 repair 算子

1. 新建类继承 `move.insertion.Insertion`
2. 实现 `move()` + `getObjective()`
3. 在 `SimulatedAnnealing` 构造函数中加入 `insertionList`

### 9.3 修改目标/约束

若要加入新约束（例如时间窗、服务时间、车辆异构更严格的舱室限制等），建议优先：

- 把约束状态放进 `Segment`（或新增一个与 segment 同步更新的状态对象）
- 在 `EvaluateInsertion` 中统一做可行性判断与增量计算


