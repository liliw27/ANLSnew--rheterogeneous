package model;

/**
 * 链表解表示中的基础节点类型（教学版）。
 *
 * <p>一条 {@link Route} 是由 {@link Node} 组成的<strong>双向链表</strong>。</p>
 *
 * <p>本项目只有两种具体节点：</p>
 * <ul>
 *   <li>{@link DepotNode}：仓库节点（{@link #index} 固定为 0），用于分隔不同的 {@link Segment}（多趟 trip）</li>
 *   <li>{@link CustomerNode}：客户访问节点（{@link #index} &gt; 0），携带一次访问的配送量向量</li>
 * </ul>
 *
 * <p><b>为什么每个节点要保存 route/segment 引用？</b></p>
 * <p>因为 destroy/repair move 经常会局部插入/删除节点。保存反向引用后，可以在 O(1) 级别更新局部统计/资源，
 * 避免每次都从头遍历整条路线。</p>
 */
public abstract class Node extends ReversibleDataStructure {
    /** 节点编号：depot=0；customer=客户编号（允许同一客户出现多次 visit）。 */
    public int index;
    /** 链表后继指针。 */
    public Node next = null;
    /** 链表前驱指针。 */
    public Node prev = null;
    /** 当前节点所属的 trip（segment）。 */
    public Segment segment = null;
    /** 当前节点所属的 route。 */
    public Route route = null;

    @Override
    protected abstract State getState();

    /**
     * 可回滚快照（ReversibleDataStructure）。
     *
     * <p>该快照只保存“链表指针 + 所属 segment/route 引用”。节点自身的业务数据（例如 CustomerNode 的
     * deliveryQuantity）由子类 State 负责补充。</p>
     */
    protected class State extends ReversibleDataStructure.State {
        final Node next;
        final Node prev;
        final Segment segment;
        final Route route;

        public State() {
            this.next = Node.this.next;
            this.prev = Node.this.prev;
            this.segment = Node.this.segment;
            this.route = Node.this.route;
        }

        public void restore() {
            Node.this.next = this.next;
            Node.this.prev = this.prev;
            Node.this.segment = this.segment;
            Node.this.route = this.route;
        }
    }
}
