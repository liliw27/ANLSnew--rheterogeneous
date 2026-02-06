package model;

/**
 * 客户访问节点（visit）。
 *
 * <p><b>重要</b>：本问题允许<strong>拆分配送（split delivery）</strong>，因此同一个客户（同一个 {@link #index}）
 * 可以在解中出现多次：可能在不同车辆、不同 trip，甚至同一条 route 的不同 segment 里出现。</p>
 *
 * <p>每一个 {@link CustomerNode} 表示“一次访问”，并携带该次访问对每个产品的配送量向量
 * {@link #deliveryQuantity}。</p>
 */
public class CustomerNode extends Node {

    /** 本次访问的配送量向量：deliveryQuantity[p] 表示产品 p 的配送量。 */
    public int[] deliveryQuantity;

    public CustomerNode(int index, int[] deliveryQuantity) {
        this.index = index;
        this.deliveryQuantity = deliveryQuantity;
    }

    /** 兼容旧接口：返回本次访问的配送量向量。 */
    public int[] getDemand() {
        return deliveryQuantity;
    }

    @Override
    protected State getState() {
        return new State();
    }

    private final class State extends Node.State {
        /**
         * 注意：这里保存的是数组引用（浅拷贝），与历史实现一致。
         *
         * <p>在本项目中，deliveryQuantity 通常在创建 CustomerNode 后不再被原地修改；如果你打算在 move 中
         * 直接修改数组内容，请先确认回滚语义是否满足需求（可能需要深拷贝）。</p>
         */
        public final int[] deliveryQuantity;

        private State() {
            super();
            this.deliveryQuantity = CustomerNode.this.deliveryQuantity;
        }

        public void restore() {
            super.restore();
            CustomerNode.this.deliveryQuantity = this.deliveryQuantity;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append("(");
        for (int p = 0; p < this.deliveryQuantity.length; p++) {
            sb.append(" ").append(deliveryQuantity[p]);
        }
        sb.append(")");
        return sb.toString();
    }
}
