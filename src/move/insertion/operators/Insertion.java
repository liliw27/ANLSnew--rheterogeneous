package move.insertion.operators;

import java.util.ArrayList;
import java.util.List;

/**
 * repair operators（插入/修复算子）的抽象基类。
 *
 * <p>本类除了定义算子接口（{@link #move()} 与 {@link #getObjective()}）外，还保存自适应权重机制需要的统计字段：
 * 分数、使用次数、每段 segment 的权重轨迹等。</p>
 */
public abstract class Insertion {
    public double score = 0;
    public double usedNum = 0;
    public double iterationNum = 0;
    public double bestNum = 0;
    public double improvedNum = 0;
    public double nonImprovedNum = 0;
    public List<Integer> iterationRecord = new ArrayList<>();
    public List<Integer> bestRecord = new ArrayList<>();
    public List<Integer> improveRecord = new ArrayList<>();
    public List<Integer> nonImproveRecord = new ArrayList<>();
    public List<Double> weight = new ArrayList<>();

    public abstract int getObjective();

    /** 执行插入操作（会就地修改解结构）。 */
    public abstract void move();
}


