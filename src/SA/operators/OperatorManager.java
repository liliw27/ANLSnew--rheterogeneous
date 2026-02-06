package SA.operators;

import move.insertion.operators.Insertion;
import move.removal.Removal;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * ALNS 算子管理工具（教学版）。
 *
 * <p>把“算子选择（均匀/按权重）+ 权重更新 + 记录 usedNum/iterationRecord”等与主循环解耦，
 * 让 {@link SimulatedAnnealing#run()} 更聚焦于“执行 move + SA 接受/回滚”。</p>
 *
 * <p>设计原则：尽量保持与历史实现的行为一致（特别是 burn-in 期间的均匀选择逻辑）。</p>
 */
public final class OperatorManager {

    private OperatorManager() {}

    /** usedNum==0 时返回 0，避免除零。 */
    public static double safeAverageScore(double score, double usedNum) {
        return usedNum <= 0 ? 0.0 : score / usedNum;
    }

    /** burn-in 结束时初始化每个算子的 weight/score/usedNum。 */
    public static void initWeights(List<Insertion> insertionList, List<Removal> removalList) {
        for (Insertion ins : insertionList) {
            ins.weight.add(1.0);
            ins.score = 0;
            ins.usedNum = 0;
        }
        for (Removal rem : removalList) {
            rem.weight.add(1.0);
            rem.score = 0;
            rem.usedNum = 0;
        }
    }

    /** 每个 segment 末尾按反应因子更新权重，并清空 score/usedNum。 */
    public static void updateWeights(double reactionFactor, List<Insertion> insertionList, List<Removal> removalList) {
        for (Insertion ins : insertionList) {
            int size = ins.weight.size();
            double reward = safeAverageScore(ins.score, ins.usedNum);
            double newWeight = ins.weight.get(size - 1) * (1 - reactionFactor) + reactionFactor * reward;
            ins.weight.add(newWeight);
            ins.score = 0;
            ins.usedNum = 0;
        }
        for (Removal rem : removalList) {
            int size = rem.weight.size();
            double reward = safeAverageScore(rem.score, rem.usedNum);
            double newWeight = rem.weight.get(size - 1) * (1 - reactionFactor) + reactionFactor * reward;
            rem.weight.add(newWeight);
            rem.score = 0;
            rem.usedNum = 0;
        }
    }

    /** burn-in 期间的均匀随机选择（保持历史实现：用区间判断，而不是 nextInt）。 */
    public static Removal chooseUniformRemoval(SplittableRandom rand, List<Removal> removalList) {
        double r = rand.nextDouble();
        double n = 1.0 / removalList.size();
        Removal next = removalList.get(0);
        for (int i = 0; i < removalList.size(); i++) {
            if (r >= i * n && r <= (i + 1) * n) {
                next = removalList.get(i);
            }
        }
        return next;
    }

    /** burn-in 期间的均匀随机选择（保持历史实现：用区间判断，而不是 nextInt）。 */
    public static Insertion chooseUniformInsertion(SplittableRandom rand, List<Insertion> insertionList) {
        double r = rand.nextDouble();
        double n = 1.0 / insertionList.size();
        Insertion next = insertionList.get(0);
        for (int i = 0; i < insertionList.size(); i++) {
            if (r >= i * n && r <= (i + 1) * n) {
                next = insertionList.get(i);
            }
        }
        return next;
    }

    /**
     * 按当前权重比例选择算子（轮盘赌）。
     *
     * <p>健壮性：sumWeight==0 或浮点误差时提供兜底，不返回 null。</p>
     */
    public static Removal chooseWeightedRemoval(SplittableRandom rand, List<Removal> removalList) {
        return chooseByWeight(rand, removalList);
    }

    public static Insertion chooseWeightedInsertion(SplittableRandom rand, List<Insertion> insertionList) {
        return chooseByWeight(rand, insertionList);
    }

    private static <T> T chooseByWeight(SplittableRandom rand, List<T> operators) {
        if (operators == null || operators.isEmpty()) {
            throw new IllegalStateException("算子列表为空，无法选择算子");
        }

        double r = rand.nextDouble();
        double sumWeight = 0.0;
        List<Double> weights = new ArrayList<>(operators.size());

        for (T op : operators) {
            double w;
            if (op instanceof Insertion) {
                Insertion ins = (Insertion) op;
                w = ins.weight.get(ins.weight.size() - 1);
            } else if (op instanceof Removal) {
                Removal rem = (Removal) op;
                w = rem.weight.get(rem.weight.size() - 1);
            } else {
                throw new IllegalArgumentException("未知算子类型: " + op.getClass());
            }
            weights.add(w);
            sumWeight += w;
        }

        if (sumWeight <= 0.0) {
            return operators.get(rand.nextInt(operators.size()));
        }

        double ratio = 0.0;
        for (int i = 0; i < operators.size() - 1; i++) {
            double nextRatio = ratio + weights.get(i) / sumWeight;
            if (r >= ratio && r <= nextRatio) {
                return operators.get(i);
            }
            ratio = nextRatio;
        }
        return operators.get(operators.size() - 1);
    }

    /** 记录一次算子被选用（保持历史字段语义：usedNum/iterationNum/iterationRecord）。 */
    public static void recordUse(Insertion insertion, int it) {
        insertion.usedNum++;
        insertion.iterationNum++;
        insertion.iterationRecord.add(it + 1);
    }

    /** 记录一次算子被选用（保持历史字段语义：usedNum/iterationNum/iterationRecord）。 */
    public static void recordUse(Removal removal, int it) {
        removal.usedNum++;
        removal.iterationNum++;
        removal.iterationRecord.add(it + 1);
    }
}


