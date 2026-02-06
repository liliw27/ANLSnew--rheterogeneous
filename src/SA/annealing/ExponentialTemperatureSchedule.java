package SA.annealing;

/**
 * 指数降温：\(T(it) = T_0 * (T_f / T_0)^{it / maxIter}\)。
 *
 * <p>与原实现保持一致。</p>
 */
public final class ExponentialTemperatureSchedule implements TemperatureSchedule {
    private final double initialTemp;
    private final double finalTemp;
    private final int maxIter;

    public ExponentialTemperatureSchedule(double initialTemp, double finalTemp, int maxIter) {
        this.initialTemp = initialTemp;
        this.finalTemp = finalTemp;
        this.maxIter = Math.max(maxIter, 1); // 防御：避免除零
    }

    @Override
    public double temperatureAt(int iteration) {
        double normalizedAnnealingTime = 1.0 * iteration / maxIter;
        return initialTemp * Math.pow(finalTemp / initialTemp, normalizedAnnealingTime);
    }
}


