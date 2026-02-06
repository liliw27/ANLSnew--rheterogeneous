package SA.annealing;

/**
 * 温度调度（cooling schedule）接口。
 *
 * <p>目的：把“温度怎么随迭代变化”从主循环中抽出来，方便学生替换不同的降温策略。</p>
 */
public interface TemperatureSchedule {
    /**
     * @param iteration 迭代下标（从 0 开始）
     * @return 当前迭代的温度
     */
    double temperatureAt(int iteration);
}


