package SA;

import SA.config.SAConfig;
import SA.init.InitialConstructor;
import SA.trace.RunTrace;
import SA.verify.SolutionVerifier;
import model.*;
import move.insertion.operators.*;
import move.removal.*;

import java.io.IOException;
import java.util.*;
import java.util.List;



public class SimulatedAnnealing extends ReversibleDataStructure {

    /* 参数与配置 */
    public final boolean TRACK_STATS = true;
    public final SAConfig config;
    /**
     * 是否开启更详细的控制台日志（用于调试）。
     *
     * <p>默认保持 {@code false}，避免批量跑实验时控制台刷屏，影响复现与阅读。</p>
     */
    private static final boolean VERBOSE_LOG = false;
    /** makespan 的权重（对应论文中的 \(\alpha\)）。 */
    public int MAKESPAN_MULTIPLIER = 100;

    public final long RANDOM_SEED; // 默认：0
    public final int SA_RESTARTS; // SA 从初始解重启的次数（默认：1）
    public final int SA_MARKOV_CHAIN_LENGTH = 1; // 常记为 L：同一温度下的迭代次数
    public final double SA_INITIAL_ACCEPTANCE_PROBABILITY; // 默认：0.4
    public final int SA_WARM_UP_NON_IMPROVING_ITERATIONS = 100; // 用于估计初始温度的“预热”非改进步数
    public double SA_INIT_TEMP = 200; // 系统初始温度（与实例相关，可能会在“预热”中重估）
    public final double SAFINALTEMP; // 终止温度（默认：1）
    public final int SA_MAXITER; // SA 最大迭代次数上限（与实例规模相关）
    public final int REMOVE_MAXNUM; // 每次迭代最大移除节点数上限

    public double REACTION_FACTOR = 0.5; // 反应因子 r：控制权重更新对近期表现的敏感程度
    public int SEGMENT_ITERATIONS = 100; // 一个“segment”的迭代次数（这里固定为 100）
    public int UNIFORM_SELECTION_ITERATIONS = 100; // 前若干次迭代使用均匀随机选算子（未启用自适应权重）
    public int RHO1 = 5; // 获得新的全局最优解
    public int RHO2 = 3; // 新解优于当前解
    public int RHO3 = 1; // 新解更差但被 SA 接受
    public double shawParameter1 = 3; // Shaw 相似性：距离权重
    public double shawParameter2 = 1; // Shaw 相似性：需求差异权重
    public double shawParameter3 = 2; // Shaw 相似性：是否同一 trip 的权重
    public int MAX_NON_IMPROVING_ITERATIONS = 1000;
    public long TIME_LIMIT_MS = 3_600_000L;
    /* 输入数据 */
    public Instance dataModel;

    /* 内部数据 */
    protected int N; // 节点数（visit 数；拆分后）
    public List<CustomerNode> nodes; // 所有可插入的 visit（不包含“满载直达 trip”节点）
    public List<CustomerNode> nodesFullLoad;
    public Route[] routes; // 所有车辆的 route

    public final SplittableRandom rand; // 随机数（算子选择、采样等）
    protected Insertion nextInsert; // 本次迭代选择的 repair 算子
    protected Removal nextRemoval;
    protected Insertion greedyInsertion;
    protected Insertion greedyInsertionSPPref;
    protected Insertion regret2Insertion;
    protected Insertion regret3Insertion;
    protected Removal randomRemoval;
    protected Removal worstRemoval;
    protected Removal longestRemoval;
    protected Removal shawRemoval;
    protected Removal CLRRemoval;
    public  List<Insertion> insertionList;
    public  List<Removal> removalList;
    public List<CustomerNode> unServedNodes;
    public List<Segment> removedSegments;
    public int iterations = 0; // 迭代计数（实际执行次数）
    protected int idleIterations = 0; // 距离上一次“接受新解”过去的迭代次数
    protected int nonImproveIterations = 0; // 距离上一次“当前解改进”过去的迭代次数

    /* 解与目标值 */
    public int objectiveBestSolution = Integer.MAX_VALUE;
    protected int currentObjective = Integer.MAX_VALUE;
    public int makespanBestSolution;
    /* 统计输出（温度/目标值随迭代变化） */
    private final RunTrace trace;
    public int count1 = 0;
    public int count2 = 0;
    public int count3 = 0;
    public int count4 = 0;
    public int count5 = 0;

    // 建议：尽量减少“入口形态”，避免在一堆构造器里迷路。
    // 本类仅保留 2 个 public 构造器：
    // - SimulatedAnnealing(instance)
    // - SimulatedAnnealing(instance, config)

    public SimulatedAnnealing(Instance dataModel) {
        this(dataModel, new SAConfig());
    }

    public SimulatedAnnealing(Instance dataModel, SAConfig config) {
        // 阅读建议：你只需要知道两件事——
        // 1) 所有可调参数都在 SAConfig 里；2) 构造函数做的事就是“读配置→建初始解→初始化算子→准备输出”。

        // 1) 保存输入与配置（复制一份，避免外部在构造后修改 config 造成“隐式副作用”）
        this.dataModel = dataModel;
        this.config = copyConfig(config);

        // 2) 初始化解数据结构，并构造初始可行解
        this.nodes = new ArrayList<>();
        this.nodesFullLoad = new ArrayList<>();
        this.routes = new Route[dataModel.vehicles.size()];
        InitialConstructor initialConstructor = new InitialConstructor(dataModel, this);
        initialConstructor.initial();
        this.N = nodes.size();

        // 3) 初始化随机数与终止/降温等关键参数（部分参数依赖 N）
        this.RANDOM_SEED = this.config.randomSeed;
        // 兼容历史行为：seed==0 表示“非确定性”（不显式设 seed）；seed!=0 才强制可复现
        this.rand = (this.RANDOM_SEED == 0L) ? new SplittableRandom() : new SplittableRandom(this.RANDOM_SEED);
        this.SA_RESTARTS = this.config.saRestarts;
        this.SA_INITIAL_ACCEPTANCE_PROBABILITY = this.config.initialAcceptanceProbability;
        this.SAFINALTEMP = this.config.finalTemperature;
        this.SA_MAXITER = (int) (this.N * this.config.saMaxIterationsScalar);
        this.REMOVE_MAXNUM = Math.max((int) (this.N * this.config.removeMaxNumRate), 2);

        // 4) 把 config 映射到当前类的“便于使用的字段”（保留字段，减少 config.xxx 噪音）
        applyConfig(this.config);

        // 5) 初始化算子、输出
        initOperators();
        this.trace = RunTrace.createIfEnabled(TRACK_STATS);
    }

    private static SAConfig copyConfig(SAConfig in) {
        SAConfig cfg = new SAConfig();
        if (in == null) return cfg;

        cfg.makespanMultiplier = in.makespanMultiplier;
        cfg.randomSeed = in.randomSeed;
        cfg.saRestarts = in.saRestarts;
        cfg.initialAcceptanceProbability = in.initialAcceptanceProbability;
        cfg.initialTemperature = in.initialTemperature;
        cfg.finalTemperature = in.finalTemperature;
        cfg.saMaxIterationsScalar = in.saMaxIterationsScalar;
        cfg.removeMaxNumRate = in.removeMaxNumRate;
        cfg.reactionFactor = in.reactionFactor;
        cfg.segmentIterations = in.segmentIterations;
        cfg.uniformSelectionIterations = in.uniformSelectionIterations;
        cfg.rhoNewBest = in.rhoNewBest;
        cfg.rhoImprovesCurrent = in.rhoImprovesCurrent;
        cfg.rhoAcceptedWorse = in.rhoAcceptedWorse;
        cfg.shawDistanceWeight = in.shawDistanceWeight;
        cfg.shawDemandWeight = in.shawDemandWeight;
        cfg.shawSameTripWeight = in.shawSameTripWeight;
        cfg.maxNonImprovingIterations = in.maxNonImprovingIterations;
        cfg.timeLimitMs = in.timeLimitMs;
        return cfg;
    }

    private void applyConfig(SAConfig cfg) {
        this.MAKESPAN_MULTIPLIER = cfg.makespanMultiplier;
        this.SA_INIT_TEMP = cfg.initialTemperature;
        this.REACTION_FACTOR = cfg.reactionFactor;
        this.SEGMENT_ITERATIONS = cfg.segmentIterations;
        this.UNIFORM_SELECTION_ITERATIONS = cfg.uniformSelectionIterations;
        this.RHO1 = cfg.rhoNewBest;
        this.RHO2 = cfg.rhoImprovesCurrent;
        this.RHO3 = cfg.rhoAcceptedWorse;
        this.shawParameter1 = cfg.shawDistanceWeight;
        this.shawParameter2 = cfg.shawDemandWeight;
        this.shawParameter3 = cfg.shawSameTripWeight;
        this.MAX_NON_IMPROVING_ITERATIONS = cfg.maxNonImprovingIterations;
        this.TIME_LIMIT_MS = cfg.timeLimitMs;
    }

    private void initOperators() {
        greedyInsertion = new GreedyInsertion(this, dataModel);
        greedyInsertionSPPref = new GreedyInsertionSPPref(this, dataModel);
        regret2Insertion = new Regret2Insertion(this, dataModel);
        regret3Insertion = new Regret3Insertion(this, dataModel);

        randomRemoval = new RandomRemoval(this);
        worstRemoval = new WorstRemoval(this, dataModel);
        longestRemoval = new LongestRouteRemoval(this);
        shawRemoval = new ShawRemoval(this, dataModel);
        CLRRemoval = new CLRRemoval(this);

        insertionList = new ArrayList<>();
        insertionList.add(greedyInsertion);
        insertionList.add(regret2Insertion);
        insertionList.add(regret3Insertion);
        insertionList.add(greedyInsertionSPPref);

        removalList = new ArrayList<>();
        removalList.add(longestRemoval);
        removalList.add(randomRemoval);
        removalList.add(shawRemoval);
//        removalList.add(worstRemoval);
        removalList.add(CLRRemoval);
    }




  

    /**
     * 模拟退火（SA）主循环（外层框架为 ALNS）。
     *
     * <p><b>高层流程（对齐论文中的“ALNS + SA”框架）</b>：</p>
     * <ol>
     *   <li>初始化一个可行解（构造函数 + {@link InitialConstructor}）</li>
     *   <li>重复迭代：
     *     <ul>
     *       <li>选择一个 destroy（removal）算子与一个 repair（insertion）算子</li>
     *       <li>destroy：从当前解中移除若干 visit，放入 {@code unServedNodes}</li>
     *       <li>repair：将 {@code unServedNodes} 全部重新插回路线计划，得到新解</li>
     *       <li>用 SA 接受准则决定“接受新解 / 回滚到上一步解”</li>
     *       <li>跟踪全局最优解，并按 segment 更新算子权重（自适应）</li>
     *     </ul>
     *   </li>
     *   <li>达到时间上限或连续过多非改进步时终止</li>
     * </ol>
     *
     * <p><b>终止条件（重点说明）</b>：</p>
     * <ul>
     *   <li><b>硬时间上限</b>：运行时间超过 {@link #TIME_LIMIT_MS}（默认 3600s）则退出，并恢复到 BEST_SOLUTION</li>
     *   <li><b>连续非改进上限</b>：若连续 {@link #MAX_NON_IMPROVING_ITERATIONS} 次没有让“当前解”变好，则退出并恢复到 BEST_SOLUTION</li>
     *   <li><b>迭代上限（名义存在）</b>：{@link #SA_MAXITER} 目前用于温度降温时间归一化，但主循环没有用它硬截断
     *       （for-loop 写成 {@code for (it = 0; true; it++)}）。如果你希望严格限制迭代次数，可以把循环条件改为
     *       {@code it < SA_MAXITER}，这属于“实验设置变更”，而不是结构重构。</li>
     * </ul>
     *
     * <p><b>实现说明</b>：接受/拒绝（accept/reject）通过快照实现：
     * {@link ReversibleDataStructure#createRestorePoint(StateType)} 与
     * {@link ReversibleDataStructure#restoreEarlierState(StateType)}。</p>
     *
     * @throws IOException
     */
    public void run() throws IOException {
        new AnnealingLoop(this).run();

        // 说明：历史遗留的“大段统计/打印代码”（用于画分布图、导出权重轨迹等）已从本文件移除，
        // 以免干扰主流程阅读。若需要参考旧实现，请见 `docs/LEGACY_NOTES.md`（或 git 历史）。
        if (trace != null) {
            trace.close();
        }
    }
    
    void initialize() {


        // 计算初始可行解的目标值
        int makespan = Arrays.stream(routes).mapToInt(r -> r.duration).max().getAsInt();
        int sumOfComplTimes = Arrays.stream(routes).mapToInt(r -> r.duration).sum();
        currentObjective = makespan * MAKESPAN_MULTIPLIER + sumOfComplTimes;
        makespanBestSolution = makespan;

        // 创建三套快照：上一步解、初始解、当前最优解
        this.createRestorePoint(StateType.PREVIOUS_SOLUTION);
        this.createRestorePoint(StateType.INIT_SOLUTION);
        objectiveBestSolution = currentObjective;
        this.createRestorePoint(StateType.BEST_SOLUTION);


        // （历史调试输出已移除：如需查看初始化目标值，可开启 VERBOSE_LOG 并自行打印）
        verifyRouteState();
        if (VERBOSE_LOG) {
            this.printRoutes();
        }
        int N1 = nodes.size();//+ nodesFullLoad.size ( )
        assert Arrays.stream(routes).mapToInt(r -> r.nrNodes - r.segments - 1).sum() == N1 : "sa.N: " + N1 + " after: " + Arrays.stream(routes).mapToInt(r -> r.nrNodes - r.segments - 1).sum();
    }

    /* -------------------- 主循环的私有辅助方法（不影响主流程阅读） -------------------- */

    void log(String msg) {
        if (VERBOSE_LOG) {
            System.out.println(msg);
        }
    }

    /**
     * 恢复“当前最优解（BEST_SOLUTION）”的快照。
     *
     * <p>注意：某些历史遗留的调试方法（例如 {@link #printBestRoutes()}）会在打印之前先 restore，
     * 这属于“有副作用”的调试工具。为了保持历史行为一致，这里单独封装成方法，便于显式调用。</p>
     */
    void restoreBestSolution() {
        this.restoreEarlierState(ReversibleDataStructure.StateType.BEST_SOLUTION);
    }

    /**
     * 接受候选解：更新 currentObjective，并将当前解保存为 PREVIOUS_SOLUTION（供下一次迭代回滚使用）。
     *
     * <p>注意：该方法不负责更新 BEST_SOLUTION（最优解快照）。</p>
     */
    void acceptCandidateSolution(int objectiveTentativeSolution) {
        currentObjective = objectiveTentativeSolution;
        idleIterations = 0;
        this.createRestorePoint(ReversibleDataStructure.StateType.PREVIOUS_SOLUTION);
    }

    /**
     * 拒绝候选解：回滚到 PREVIOUS_SOLUTION。
     */
    void rejectCandidateSolution() {
        idleIterations++;
        this.restoreEarlierState(ReversibleDataStructure.StateType.PREVIOUS_SOLUTION);
    }

    /**
     * 若当前解优于历史最优，则更新 BEST_SOLUTION（最优解快照）及相关记录。
     *
     * @param it 当前迭代下标（从 0 开始），用于把记录写成 “迭代号=it+1”
     */
    void updateBestSolutionIfNeeded(int it) {
        if (currentObjective < objectiveBestSolution) {
            nextInsert.bestRecord.add(it + 1);
            nextRemoval.bestRecord.add(it + 1);
            nextRemoval.improvedNum--;
            nextRemoval.bestNum++;
            nextInsert.improvedNum--;
            nextInsert.bestNum++;
            objectiveBestSolution = currentObjective;
            makespanBestSolution = Arrays.stream(routes).mapToInt(r -> r.duration).max().getAsInt();
            this.createRestorePoint(ReversibleDataStructure.StateType.BEST_SOLUTION); // 保存“最优解”
//                    this.printRoutes();
        }
    }

    int executeNextMove() {
        nextRemoval.move();
        nextInsert.move();
        return nextInsert.getObjective();
    }

    // 说明：算子选择与权重更新已抽到 OperatorManager，避免在本类中混入过多“管理逻辑”。

    boolean acceptNextMove(double temperature, int savings) {
        if (savings < 0) {
            nextInsert.score += RHO1;
            nextInsert.improvedNum++;
            nextRemoval.score += RHO1;
            nextRemoval.improvedNum++;
            return true;
        }
        double acceptanceProbability = Math.exp(-savings / temperature);
        boolean f=rand.nextDouble() < acceptanceProbability;
        if (f) {
            nextInsert.score += RHO2;
            nextInsert.nonImprovedNum++;
            nextRemoval.nonImprovedNum++;
            nextRemoval.score += RHO2;
        }
        else {
            nextInsert.score += RHO3;
            nextRemoval.score += RHO3;
        }
        return f;
    }


    /**
     * 获取 route 中“某个位置”的节点。
     *
     * <p>注意：位置从 1 开始计数（位置 0 永远是 depot）。</p>
     *
     * @param route
     * @param position
     * @return
     */
    protected Node getJob(int route, int position) {
        assert route >= 0 && route < routes.length;
        assert position > 0 && position < routes[route].nrNodes;

        Node node = routes[route].routeStart;
        for (int i = 1; i < position; i++)
            node = node.next;
        return node;
    }

    public void printRoutes() {
        for (int k = 0; k < dataModel.vehicles.size(); k++)
            System.out.println("k" + k + ": " + routes[k]);
    }
    public void printBestRoutes() {
        restoreBestSolution();
        for (int k = 0; k < dataModel.vehicles.size(); k++)
            System.out.println("k" + k + ": " + routes[k]);
    }

    /**
     * 获取当前解的目标值（makespan * alpha + 总路程）。
     *
     * <p>说明：该值在 SA 主循环中频繁被更新与校验。对外提供只读访问，便于跨包的验证器/统计代码使用。</p>
     */
    public int getCurrentObjective() {
        return currentObjective;
    }

    /**
     * 当前解状态一致性检查（护栏）。
     *
     * <p>完整检查逻辑已抽到 {@link SolutionVerifier}，避免本类过长。</p>
     */
    boolean verifyRouteState() {
        return SolutionVerifier.verify(this);
    }

    RunTrace getTrace() {
        return trace;
    }


    @Override
    public void createRestorePoint(ReversibleDataStructure.StateType stateType) {
        super.createRestorePoint(stateType);
        for (Route route : routes) {
            route.createRestorePoint(stateType);
            for (Node node = route.routeStart; node != null; node = node.next)
                node.createRestorePoint(stateType);
        }
    }

    @Override
    public void restoreEarlierState(StateType stateType) {
        super.restoreEarlierState(stateType);
        for (Route route : routes) {
            for (Node node = route.routeStart; node != null; node = node.next)
                node.restoreEarlierState(stateType);
            route.restoreEarlierState(stateType);
        }
    }

    @Override
    public State getState() {
        return new State();
    }

    protected class State extends ReversibleDataStructure.State {
        final int currentObjective;
        final List<CustomerNode> nodes = new ArrayList<>();

        public State() {
            this.currentObjective = SimulatedAnnealing.this.currentObjective;
            for (CustomerNode node : SimulatedAnnealing.this.nodes) {
                this.nodes.add(node);
            }
        }

        @Override
        public void restore() {
            SimulatedAnnealing.this.currentObjective = this.currentObjective;
            SimulatedAnnealing.this.nodes.clear();
            for (CustomerNode node : this.nodes) {
                SimulatedAnnealing.this.nodes.add(node);
            }

        }
    }

      /**
     * 预热：用于估计 SA 的初始温度。
     */
      @SuppressWarnings("unused")
      private void warmup() {
          assert SA_WARM_UP_NON_IMPROVING_ITERATIONS < SA_MAXITER;
          int nonImprovingMoves = 0;
          double averageIncreaseInCost = 0;
          // 可替换成不同的 destroy/repair 组合来做预热采样
          nextRemoval = new RandomRemoval(this);
  //        nextRemoval = new WorstRemoval (this,dataModel);
  //        nextInsert = new GreedyInsertion (this,dataModel);
          nextInsert = new GreedyInsertion(this, dataModel);
  
          while (nonImprovingMoves < SA_WARM_UP_NON_IMPROVING_ITERATIONS) {
              int objectiveTentativeSolution = this.executeNextMove();
              int savings = objectiveTentativeSolution - currentObjective;
              if (savings >= 0) { // 非改进步
                  nonImprovingMoves++;
                  averageIncreaseInCost += savings;
              }
              // 预热阶段：无条件接受（只是为了采样“平均劣化幅度”）
              currentObjective = objectiveTentativeSolution;
              // （历史调试打印已移除）
          }
          assert nonImprovingMoves == SA_WARM_UP_NON_IMPROVING_ITERATIONS;
          averageIncreaseInCost /= nonImprovingMoves;
          SA_INIT_TEMP = -averageIncreaseInCost / Math.log(SA_INITIAL_ACCEPTANCE_PROBABILITY);
          if (SA_INIT_TEMP < 100) SA_INIT_TEMP = 100;
  //        System.out.println("initial temperature: " + SA_INIT_TEMP + " average increase in cost: " + averageIncreaseInCost+"\n");
      }
}
