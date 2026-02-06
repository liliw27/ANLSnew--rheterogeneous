package move.insertion.util;

import SA.SimulatedAnnealing;
import model.Instance;

/**
 * insertion 算子使用的小型辅助容器：保存当前 SA 上下文引用。
 *
 * <p>历史上它曾用于放置“插入前的额外维护逻辑”。目前它只是 {@link SimulatedAnnealing} 与 {@link Instance}
 * 的一个 holder：保留它主要是为了兼容旧代码，同时也作为一个扩展点（学生可以在这里加入插入前的统一更新）。</p>
 */
public class UpdateSolutionBeforeInsertion {
    protected final SimulatedAnnealing sa;
    protected final Instance dataModel;

    public UpdateSolutionBeforeInsertion(SimulatedAnnealing sa, Instance dataModel) {
        this.sa = sa;
        this.dataModel = dataModel;
    }
}


