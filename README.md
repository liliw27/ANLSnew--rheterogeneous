# FRP（Fuel Replenishment Problem）— ALNS + SA 教学版代码基线

本项目是论文 **“The fuel replenishment problem: A split-delivery multi-compartment vehicle routing problem with multiple trips”**（见根目录 PDF）的一份 Java 实现原型，核心算法为 **ALNS（Adaptive Large Neighborhood Search）+ SA（Simulated Annealing 接受准则）**。

项目目标：在满足多舱、多产品、多趟、可拆分配送约束下，最小化加权目标：

- **makespan**（所有车辆行程中最长的那条）
- **总旅行时间**（所有车辆行程时间之和）

代码中对应为：

- `objective = makespan * MAKESPAN_MULTIPLIER + sum(route.duration)`
- 默认 `MAKESPAN_MULTIPLIER = 100`（对应论文实验设置 \(\alpha=100,\beta=1\)）

---

## 快速开始（推荐给学生）

### 方式 1（最推荐）：用 IntelliJ IDEA 直接运行

运行入口：`src/test/testSA.java` 的 `main()`

`testSA.java` 默认使用项目根目录下的相对路径（`./data/Instance/caseStudy`），无需修改源码。

### 方式 2（可选/备用）：命令行编译运行（macOS / Linux / WSL）

在项目根目录执行：

```bash
mkdir -p build/classes
find src -name "*.java" > build/sources.txt
javac -encoding UTF-8 -d build/classes @build/sources.txt
java -cp build/classes test.testSA
```

### 方式 3（可选/备用，Windows）：PowerShell 一键运行

在项目根目录打开 PowerShell，执行：

```powershell
.\scripts\build.ps1
.\scripts\run_testSA.ps1 --start=1 --end=1 --runs=1
```

如果脚本执行被系统策略拦截，可以仅对当前 PowerShell 会话临时放开：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

> 提示：脚本不是必需的——如果你只在 IntelliJ 里开发/运行，完全可以忽略 `scripts/` 目录。

### 命令行参数（推荐）

`testSA` 支持两种参数形式：

- **位置参数（兼容旧用法）**：`<start> <end> <runs> <instanceDir?>`
- **命名参数**：`--start=1 --end=28 --runs=2 --instanceDir=... --dumpSolution=true --solutionDir=...`

示例（只跑 1 个实例 1 次，并导出解文件）：

```bash
java -cp build/classes test.testSA --start=1 --end=1 --runs=1 --dumpSolution=true --solutionDir=./output/solutions
```

---

## 目录结构（高层）

- **`src/io/`**：读取实例数据（`Reader.java` / `Reader2.java`）
- **`src/model/`**：解表示与可逆状态（`Route/Segment/Node/...`）
- **`src/move/removal/`**：destroy（移除/破坏）算子
- **`src/move/insertion/`**：repair（插入/修复）算子 + 插入评估
- **`src/SA/`**：ALNS+SA 主循环（`SimulatedAnnealing.java`）与初始解（`InitialConstructor.java`）
- **`src/test/`**：实验入口（`testSA.java`）
- **`data/`**：算例（`data/Instance/...` 等）
- **`output/`**：运行输出（目标随迭代、温度随迭代、解文件等）
- **`docs/ARCHITECTURE.md`**：架构与数据结构详解（建议学生先读）

> 提示：`build/`、`out/`、`output/` 都是运行/编译生成物。教学分发时建议保持仓库“只含源码与数据”，这些目录可随时删除并重建。

---

## 输入数据格式（`Reader.java` 读取的实例）

典型实例文件示例：`data/Instance/caseStudy/1.txt`。其结构大致为：

1. 头部参数（多行，含 `TRIPS/VEHICLES/NRSTATIONS/NRCOMPARTMENTS/CAPACITY/NRPRODUCTS` 等）
2. `VEHICLE SECTION ...`：每行一个车辆规格
3. `DEMANDS SECTION ...`：每行一个节点的多产品需求（包含 depot 和虚拟终点）
4. 距离矩阵 section：\((nrStations + 2) \times (nrStations + 2)\)

### 重要实现细节（务必让学生知道）

- **需求单位缩放**：`Reader` 会把需求除以 1000 并向上取整（`ceil(d/1000)`）。
- **车辆行**：文件里每行可能有 3 列（例如 `<compartmentNum> <capacity> <vehicle_id>`），当前实现只读取前两列，忽略 `vehicle_id`。
- **距离矩阵修正**：读取后会做三角不等式松弛，确保满足论文假设（`d[i][k] <= d[i][j] + d[j][k]`）。

---

## 输出说明

默认统计输出写在：

- `output/SAtemperature.txt`：迭代号、温度
- `output/SAobjective.txt`：迭代号、当前目标值

`testSA.java` 也会在控制台打印每辆车的 `Route`（包含多趟的 depot 分隔）。

### 调试提示（必要时再用）

- `SA/SimulatedAnnealing.java` 中存在一个 `VERBOSE_LOG` 开关，默认关闭；如果你需要观察算法中途状态，可以临时打开它。
- 注意：`printBestRoutes()` 会先把解状态 restore 到 `BEST_SOLUTION` 再打印（有副作用），调试时请留意当前解是否被覆盖。

---

## 辅助工具（非主求解流程）

- `src/io/GenerateInstance.java`：用于从一些“原始排班/经纬度/站点文件”生成案例数据的遗留工具，不参与 `Reader → SimulatedAnnealing` 的主求解链路。需要时再用，不建议学生把它当 solver 的一部分修改。

## 下一步要读什么

1. `docs/ARCHITECTURE.md`：理解解表示（`DepotNode` 分段、多趟）与一次迭代流程
2. `src/model/Segment.java`：多舱多产品约束如何用 `productUsed/productResidual/compartmentResidual` 表示
3. `src/move/insertion/EvaluateInsertion.java`：插入评估（II / SI / STI 三策略）
4. `src/SA/SimulatedAnnealing.java`：ALNS + SA 接受准则 + 算子权重更新


