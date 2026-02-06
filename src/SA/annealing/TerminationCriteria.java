package SA.annealing;

/**
 * 终止条件管理器。
 *
 * <p>把“连续不改进次数”和“时间上限”等条件从主循环中抽出来，避免 AnnealingLoop 过长。</p>
 */
public final class TerminationCriteria {
    private final int maxNonImprovingIterations;
    private final long timeLimitMs;

    private int nonImproveIterations = 0;

    public TerminationCriteria(int maxNonImprovingIterations, long timeLimitMs) {
        this.maxNonImprovingIterations = maxNonImprovingIterations;
        this.timeLimitMs = timeLimitMs;
    }

    /**
     * 在拿到一个候选目标值后，更新“连续不改进计数”。
     *
     * @return 是否触发“连续不改进上限”终止
     */
    public boolean updateNonImproveAndCheck(int objectiveTentativeSolution, int currentObjective) {
        if (objectiveTentativeSolution < currentObjective) {
            nonImproveIterations = 0;
            return false;
        }
        nonImproveIterations++;
        return nonImproveIterations >= maxNonImprovingIterations;
    }

    public int getNonImproveIterations() {
        return nonImproveIterations;
    }

    public boolean timeLimitReached(long elapsedMs) {
        return elapsedMs > timeLimitMs;
    }
}


