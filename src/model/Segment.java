package model;

/**
 * 一个 {@link Segment} 表示同一车辆 {@link Route} 中的一次“趟次 / trip”。
 *
 * <p>在本项目中，一辆车的行程（可能包含多趟）用一条双向链表 {@link Route} 表示；链表中间插入的
 * {@link DepotNode} 用来把 Route 切分成多个 Segment。</p>
 *
 * <p>Segment 的职责是维护“多舱多产品”的资源状态（与论文中的舱室容量/产品分配约束对应）：</p>
 * <ul>
 *   <li><b>{@code productUsed[p]}</b>: 本趟内产品 p 的累计配送量</li>
 *   <li><b>{@code productResidual[p]}</b>: 由于舱容量必须整舱计数（向上取整）而产生的剩余可用量</li>
 *   <li><b>{@code compartmentResidual}</b>: 空舱剩余数量</li>
 * </ul>
 *
 * <p><b>重要不变量</b>：对任意 p，都应满足 {@code productResidual[p] >= 0} 且
 * {@code compartmentResidual >= 0}。如果出现负数，说明插入/删除逻辑破坏了可行性。</p>
 *
 * @author 20175993
 * @create 6/21/2018
 * @since 1.0.0
 */
public class Segment extends ReversibleDataStructure
{
    public final Node segmentHead;
    /** 是否为“满载直达 trip”（Full-Load Direct Trip, FTL）。 */
    public boolean isFullDirect = false;
    /** 所属实例数据。 */
    public final Instance dataModel;
    /**
     * 本 segment 内的节点计数。
     *
     * <p><b>注意</b>：这里的计数包含 depot 节点（与 {@link SA.verify.SolutionVerifier} 的检查一致）。</p>
     */
    public int customersInSegment = 0;
    /** 本趟内每种产品的累计配送量（按 visit 的 {@link CustomerNode#deliveryQuantity} 叠加）。 */
    public int[] productUsed;
    /**
     * 每种产品的“剩余可用量”（由整舱取整导致）。
     *
     * <p>计算方式：\( \lceil used/cap \rceil \cdot cap - used \)。</p>
     */
    public int[] productResidual;
    /** 空舱剩余数量（vehicle.compartmentNum - 已占用舱数）。 */
    public int compartmentResidual;
    public Segment next;
    public Segment prev;
    public Vehicle vehicle;

    public Segment(Node segmentHead, Instance dataModel,Vehicle vehicle)
    {
        this.segmentHead = segmentHead;

        this.dataModel = dataModel;
        this.productUsed = new int[dataModel.nrProducts];
        this.productResidual = new int[dataModel.nrProducts];
        this.vehicle=vehicle;
        this.compartmentResidual = vehicle.compartmentNum;
    }

    public boolean isEmpty()
    {
        return customersInSegment == 0;
    }

    public void addNode(Node node)
    {
        customersInSegment++;
        node.segment = this;
        if (node instanceof CustomerNode)
        {
            // customer visit：会改变资源占用
            for (int p = 0; p < dataModel.nrProducts; p++) {
                this.productUsed[p] += ((CustomerNode) node).deliveryQuantity[p];
            }
            recomputeResiduals();
        }

    }

    public void removeNode(Node node)
    {
        customersInSegment--;
        node.segment = null;
        if (node instanceof CustomerNode)
        {
            // customer visit：会改变资源占用
            for (int p = 0; p < dataModel.nrProducts; p++) {
                this.productUsed[p] -= ((CustomerNode) node).deliveryQuantity[p];
            }
            recomputeResiduals();
        }
    }

    /**
     * 重新计算 {@link #productResidual} 与 {@link #compartmentResidual}。
     *
     * <p>把“整舱取整”的细节集中在一个地方，减少学生修改时漏更的概率。</p>
     */
    private void recomputeResiduals() {
        int usedCompartments = 0;
        for (int p = 0; p < dataModel.nrProducts; p++) {
            int used = this.productUsed[p];
            int compartmentsForP = (int) Math.ceil(used * 1.0 / vehicle.comCapacity);
            usedCompartments += compartmentsForP;
            this.productResidual[p] = compartmentsForP * vehicle.comCapacity - used;
        }
        this.compartmentResidual = vehicle.compartmentNum - usedCompartments;
        // 若 compartmentResidual < 0，则该 segment 容量不可行（上层插入评估应避免这种情况）。
    }

    @Override
    protected State getState()
    {
        return new State ( );
    }

    protected class State extends ReversibleDataStructure.State
    {
        final int[] productUsed = new int[dataModel.nrProducts];
        final int[] productResidual = new int[dataModel.nrProducts];
        final int compartmentResidual;
        final int customersInSegment;
        final Segment next;
        final Segment prev;
        final Vehicle vehicle;

        public State()
        {
            for (int p = 0; p < dataModel.nrProducts; p++)
            {
                this.productUsed[p] = Segment.this.productUsed[p];
                this.productResidual[p] = Segment.this.productResidual[p];

            }
            this.compartmentResidual = Segment.this.compartmentResidual;
            this.customersInSegment = Segment.this.customersInSegment;
            this.next = Segment.this.next;
            this.prev = Segment.this.prev;
            this.vehicle=Segment.this.vehicle;
        }

        @Override
        public void restore()
        {
            for (int p = 0; p < dataModel.nrProducts; p++)
            {
                Segment.this.productUsed[p] = this.productUsed[p];
                Segment.this.productResidual[p] = this.productResidual[p];
            }
            Segment.this.compartmentResidual = this.compartmentResidual;
            Segment.this.customersInSegment = this.customersInSegment;
            Segment.this.next = this.next;
            Segment.this.prev = this.prev;
            Segment.this.vehicle=this.vehicle;
        }
    }
}
