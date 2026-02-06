package model;

/**
 * 仓库访问节点（depot，{@link #index}=0）。
 *
 * <p>在本项目中，depot 节点用来在一条 {@link Route} 的链表中<strong>分隔多趟 trip</strong>：</p>
 * <ul>
 *   <li>每出现一个新的 depot 节点，就意味着开始一个新的 {@link Segment}</li>
 *   <li>删除一个中间 depot 节点，则等价于把相邻两趟 trip 合并成一趟（见 {@link Route#removeDepot(DepotNode)}）</li>
 * </ul>
 *
 * <p><b>快照注意</b>：depot 节点在 create/restore 时会同时对其所属 segment 做快照，
 * 以保证回滚时 segment 的资源状态也一致。</p>
 */
public class DepotNode extends Node {
    public DepotNode() {
        index = 0;
    }

    @Override
    public void createRestorePoint(ReversibleDataStructure.StateType stateType) {
        super.createRestorePoint(stateType);
        this.segment.createRestorePoint(stateType);
    }

    @Override
    public void restoreEarlierState(ReversibleDataStructure.StateType stateType) {
        super.restoreEarlierState(stateType);
        this.segment.restoreEarlierState(stateType);
    }

    @Override
    protected State getState() {
        return new State();
    }

    @Override
    public String toString()
    {
        return String.valueOf(index);
    }
}
