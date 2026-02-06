package SA;

import SA.annealing.ExponentialTemperatureSchedule;
import SA.annealing.TerminationCriteria;
import SA.annealing.TemperatureSchedule;
import SA.operators.OperatorManager;
import SA.trace.RunTrace;

import java.io.IOException;

/**
 * ALNS+SA 的主迭代循环（从 {@link SimulatedAnnealing} 中抽出）。
 *
 * <p>目的：让 {@link SimulatedAnnealing} 更像“状态容器 + 算子集合 + 快照能力”，
 * 把长且线性的主循环逻辑放到独立类中，结构更清晰。</p>
 *
 * <p><b>行为约束</b>：本类应尽量保持与原始 {@code SimulatedAnnealing.run()} 完全一致的行为（包括终止方式、计数与 trace 输出）。</p>
 */
final class AnnealingLoop {
    private final SimulatedAnnealing sa;

    AnnealingLoop(SimulatedAnnealing sa) {
        this.sa = sa;
    }

    void run() throws IOException {
        long startTimeMs = System.currentTimeMillis();

        // 1) 构造并初始化初始解
        sa.initialize();

        // 2) 可选预热：用于估计初始温度（当前默认关闭，参数从 config 给定）
        // sa.warmup();

        // 3) 运行 SA 主循环
        Label:
        for (int run = 0; run < sa.SA_RESTARTS; run++) {
            if (run > 0) { // 除第一次外，重启时恢复到初始解
                sa.restoreEarlierState(SimulatedAnnealing.StateType.INIT_SOLUTION);
            }

            TemperatureSchedule temperatureSchedule =
                    new ExponentialTemperatureSchedule(sa.SA_INIT_TEMP, sa.SAFINALTEMP, sa.SA_MAXITER);
            TerminationCriteria termination =
                    new TerminationCriteria(sa.MAX_NON_IMPROVING_ITERATIONS, sa.TIME_LIMIT_MS);

            // 迭代循环：
            // - 终止主要由“时间上限 / 连续非改进上限”控制
            // - SA_MAXITER 当前不作为硬截断，仅参与温度归一化（normalizedAnnealingTime）
            for (int it = 0; true; it++) {
                chooseOperatorsAndUpdateWeightsIfNeeded(it);

                double temperature = temperatureSchedule.temperatureAt(it);

                int objectiveTentativeSolution = sa.executeNextMove();

                if (termination.updateNonImproveAndCheck(objectiveTentativeSolution, sa.getCurrentObjective())) {
                    // 与历史行为保持一致：退出前恢复到“最优解”
                    sa.restoreBestSolution();
                    sa.log("makespan: " + sa.makespanBestSolution +
                            " duration: " + (sa.objectiveBestSolution - sa.makespanBestSolution * 100) +
                            " objective " + sa.objectiveBestSolution);
                    sa.iterations = it + 1;
                    sa.log("iteration: " + sa.iterations);
                    break;
                }
                // 保持与原实现一致：把计数写回 sa（供调试/统计使用）
                sa.nonImproveIterations = termination.getNonImproveIterations();

                if (sa.acceptNextMove(temperature, objectiveTentativeSolution - sa.getCurrentObjective())) {
                    sa.acceptCandidateSolution(objectiveTentativeSolution);
                } else {
                    sa.rejectCandidateSolution();
                }

                RunTrace trace = sa.getTrace();
                if (trace != null) {
                    trace.writeTemperature(it, temperature);
                    trace.writeObjective(it, sa.getCurrentObjective());
                }

                sa.updateBestSolutionIfNeeded(it);

                long elapsedMs = System.currentTimeMillis() - startTimeMs;
                assert sa.verifyRouteState();

                // 硬时间上限（默认与论文一致：3600s）
                if (termination.timeLimitReached(elapsedMs)) {
                    sa.restoreEarlierState(SimulatedAnnealing.StateType.BEST_SOLUTION);
                    sa.iterations = it + 1;
                    break Label;
                }
            }
        }
    }

    private void chooseOperatorsAndUpdateWeightsIfNeeded(int it) {
        // burn-in 结束后初始化算子权重（前 UNIFORM_SELECTION_ITERATIONS 次均匀随机选择）
        if (it == sa.UNIFORM_SELECTION_ITERATIONS) {
            OperatorManager.initWeights(sa.insertionList, sa.removalList);
        } else if (it % sa.SEGMENT_ITERATIONS == 0 && it > 0) {
            // 自适应权重更新（ALNS）：根据上一段 segment 内算子的表现更新权重
            OperatorManager.updateWeights(sa.REACTION_FACTOR, sa.insertionList, sa.removalList);
        }

        if (it < sa.UNIFORM_SELECTION_ITERATIONS) {
            sa.nextRemoval = OperatorManager.chooseUniformRemoval(sa.rand, sa.removalList);
            OperatorManager.recordUse(sa.nextRemoval, it);

            sa.nextInsert = OperatorManager.chooseUniformInsertion(sa.rand, sa.insertionList);
            OperatorManager.recordUse(sa.nextInsert, it);
        } else {
            sa.nextRemoval = OperatorManager.chooseWeightedRemoval(sa.rand, sa.removalList);
            OperatorManager.recordUse(sa.nextRemoval, it);

            sa.nextInsert = OperatorManager.chooseWeightedInsertion(sa.rand, sa.insertionList);
            OperatorManager.recordUse(sa.nextInsert, it);
        }
    }
}


