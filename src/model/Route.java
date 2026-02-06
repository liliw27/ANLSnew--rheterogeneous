package model;

/**
 * 一辆车的完整行程（itinerary）。
 *
 * <p><b>核心表示</b>：Route 是一条双向链表，链表节点为 {@link Node}：</p>
 *
 * <ul>
 *   <li>链表以 {@link DepotNode} 开始（{@code routeStart}）并以 {@link DepotNode} 结束（{@code routeEnd}）</li>
 *   <li>当链表中间出现额外的 {@link DepotNode} 时，表示车辆“回仓补给并开始下一趟”，即 Route 被切分成多个 {@link Segment}</li>
 * </ul>
 *
 * <p><b>与论文对齐</b>：</p>
 * <ul>
 *   <li>一次 trip 对应一个 {@link Segment}</li>
 *   <li>拆 trip（STI, split-trip insertion）等价于在链表中插入一个 {@link DepotNode}（见 {@link #insertDepot(Node)})</li>
 * </ul>
 *
 * <p><b>重要不变量</b>（由 {@link SA.verify.SolutionVerifier} 检查）：</p>
 * <ul>
 *   <li>{@code duration} 等于链表相邻节点距离之和</li>
 *   <li>{@code nrNodes} 等于链表节点数（含 start/end depot）</li>
 *   <li>{@code segments} 等于 Route 中 Segment 的数量</li>
 * </ul>
 *
 * @author 20175993
 * @create 6/21/2018
 * @since 1.0.0
 */
public class Route extends ReversibleDataStructure
{
    public final int ID;
    public final Instance dataModel;

    /** 链表起点 depot。 */
    public DepotNode routeStart;
    /** 链表终点 depot。 */
    public DepotNode routeEnd;

    /** 当前 route 中的 segment（trip）数量。 */
    public int segments = 1;
    /** 链表节点数量（包含 start/end depot）。 */
    public int nrNodes = 2;
    /** route 总时长（相邻节点距离之和）。 */
    public int duration = 0;

    public Route(int ID, Instance dataModel) {
        this.ID = ID;
        this.dataModel = dataModel;

        // 初始化为：depotStart -> depotEnd（一趟空行程，对应一个 segment）
        this.routeStart = new DepotNode();
        this.routeEnd = new DepotNode();

        routeStart.next = routeEnd;
        routeEnd.prev = routeStart;
        routeStart.route = this;
        routeEnd.route = this;

        Segment segment = new Segment(routeStart, dataModel, dataModel.vehicles.get(this.ID));
        segment.addNode(routeStart);
        segment.addNode(routeEnd);
        segment.prev = null;
        segment.next = null;
    }
    public boolean isEmpty() {
        return nrNodes == 2; // 只有 start/end depot
    }

    public void removeNode(Node node) {
        if (node instanceof CustomerNode)
            this.removeCustomerNode((CustomerNode) node);
        else
            removeDepot((DepotNode) node);
    }

//    public void insertNode(Node prevNode,Node node){
//        if(node instanceof CustomerNode)
//            this.insertCustomerNode(prevNode,(CustomerNode) node);
//        else
//            insertDepot(prevNode);
//    }

    public void removeCustomerNode(CustomerNode node) {
        Node prevNode = node.prev;
        Node nextNode = node.next;
        Segment segment = node.segment;

        // 更新 route 时长：删掉 prev->node 与 node->next，补上 prev->next
        this.duration = duration
                - dataModel.distanceMatrix[prevNode.index][node.index]
                - dataModel.distanceMatrix[node.index][nextNode.index]
                + dataModel.distanceMatrix[prevNode.index][nextNode.index];

        // 从链表中摘除
        prevNode.next = nextNode;
        nextNode.prev = prevNode;
        node.prev = null;
        node.next = null;
        node.route = null;

        // 更新 segment 资源
        segment.removeNode(node);
        nrNodes--;
    }

    public void removeDepot(DepotNode depotNode) {
        assert depotNode != this.routeStart;
        assert depotNode != this.routeEnd;

        Segment segment = depotNode.segment;
        Segment prevSegment = segment.prev;
        Segment nextSegment = segment.next;

        // 1) 从 segment 链表中摘除
        if (prevSegment != null) prevSegment.next = nextSegment;
        if (nextSegment != null) nextSegment.prev = prevSegment;
        segments--;

        // 2) 将被删除 depot 后面的整段 nodes 合并进 prevSegment（等价于“合并两趟 trip”）
        Node node = depotNode.next;
        while (node != null && node.segment == segment) {
            node.segment.removeNode(node);
            prevSegment.addNode(node);
            node = node.next;
        }

        // 3) 更新 route 时长：删掉 prev->depot 与 depot->next，补上 prev->next
        Node prevNode = depotNode.prev;
        Node nextNode = depotNode.next;
        duration = duration
                - dataModel.distanceMatrix[prevNode.index][depotNode.index]
                - dataModel.distanceMatrix[depotNode.index][nextNode.index]
                + dataModel.distanceMatrix[prevNode.index][nextNode.index];

        // 4) 从链表中摘除 depot
        prevNode.next = nextNode;
        nextNode.prev = prevNode;

        depotNode.prev = null;
        depotNode.next = null;
        depotNode.segment = null;
        depotNode.route = null;
        nrNodes--;
    }

    public void insertCustomerNode(Node prevNode, Node customerNode) {
        Node nextNode = prevNode.next;

        // 更新 route 时长：删掉 prev->next，补上 prev->customer 与 customer->next
        duration = duration
                - dataModel.distanceMatrix[prevNode.index][nextNode.index]
                + dataModel.distanceMatrix[prevNode.index][customerNode.index]
                + dataModel.distanceMatrix[customerNode.index][nextNode.index];

        // 插入到链表
        prevNode.next = customerNode;
        customerNode.prev = prevNode;
        customerNode.next = nextNode;
        nextNode.prev = customerNode;
        customerNode.route = this;

        // 插入点所在的 segment 资源更新
        prevNode.segment.addNode(customerNode);
        nrNodes++;
    }

    public void insertDepot(Node prevNode) {
        // 在 prevNode 之后插入一个 depot：把当前 segment 拆成两个 segment（两趟 trip）
        Node nextNode = prevNode.next;
        Segment prevSegment = prevNode.segment;
        Segment nextSegment = prevNode.segment.next;

        // 1) 新建 depot 节点与新 segment
        DepotNode depotNode = new DepotNode();
        Segment segment = new Segment(depotNode, dataModel, dataModel.vehicles.get(this.ID));
        segment.addNode(depotNode);

        // 2) 挂接到 segment 链表：prevSegment -> newSegment -> nextSegment
        if (prevSegment != null) prevSegment.next = segment;
        segment.prev = prevSegment;
        segment.next = nextSegment;
        if (nextSegment != null) nextSegment.prev = segment;
        segments++;

        depotNode.route = this;

        // 3) 将 prevNode 后面仍属于 prevSegment 的所有节点迁移到新 segment
        Node node = prevNode.next;
        while (node != null && node.segment == prevSegment) {
            prevSegment.removeNode(node);
            segment.addNode(node);
            node = node.next;
        }

        // 4) 插入到链表，并更新 route 时长
        duration = duration
                - dataModel.distanceMatrix[prevNode.index][nextNode.index]
                + dataModel.distanceMatrix[prevNode.index][depotNode.index]
                + dataModel.distanceMatrix[depotNode.index][nextNode.index];

        prevNode.next = depotNode;
        depotNode.prev = prevNode;
        depotNode.next = nextNode;
        nextNode.prev = depotNode;
        nrNodes++;
    }

    /**
     * 从本 route 中移除整段 segment（一次 trip）。
     *
     * <p>用于 trip 级 destroy（例如 {@code TripsRemoval}）。该方法只维护：</p>
     * <ul>
     *   <li>链表指针</li>
     *   <li>{@link #duration}/{@link #nrNodes}/{@link #segments}</li>
     * </ul>
     *
     * <p><b>注意</b>：它不会尝试“修复可行性”，只做结构摘除；可行性由上层 move 保证。</p>
     */
    public void removeSegment(Segment segment) {
        this.segments--;

        SegmentBounds bounds = findSegmentBounds(segment);
        Node head = bounds.head;
        Node tail = bounds.tail; // tail 可能是 routeEnd

        // 1) duration / nrNodes：扣掉 segment 内每条边（head->...->tail）
        Node node = head;
        while (node != null && node.segment == segment) {
            if (node.next == null) break;
            this.duration -= dataModel.distanceMatrix[node.index][node.next.index];
            this.nrNodes--;
            node = node.next;
        }

        // 2) 链表摘除：三种情况（首段/末段/中间段）
        if (head == this.routeStart) {
            // 移除开头一段：routeStart 需要前移到下一个 segment 的 head
            this.routeStart = (DepotNode) segment.next.segmentHead;
            this.routeStart.prev = null;
            tail.next = null;
            return;
        }

        if (tail == this.routeEnd) {
            // 移除末尾一段：把 head.prev 直接连到 routeEnd
            Node before = head.prev;
            before.next = this.routeEnd;
            this.routeEnd.prev.next = null;
            this.routeEnd.prev = before;
            head.prev = null;
            this.routeEnd.segment = segment.prev;
            return;
        }

        // 中间段：把 head.prev 直接连到 nextSegment.head
        Node before = head.prev;
        Node after = segment.next.segmentHead;
        before.next = after;
        after.prev = before;
        head.prev = null;

        // 保持与历史实现一致：routeStart 被设置为“当前 route 的第一个 segment head”
        this.routeStart = (DepotNode) this.routeStart.segment.next.segmentHead;
        this.routeStart.prev = null;
        tail.next = null;
    }

    /**
     * 将一个 segment（一次 trip）追加到本 route 尾部。
     *
     * <p>用于 trip 级 repair（例如 {@code TripsInsertion}）。</p>
     */
    public void insertSegment(Segment segment) {
        this.segments++;

        SegmentBounds bounds = findSegmentBounds(segment);
        Node head = bounds.head;
        Node tail = bounds.tail;

        // 1) 链表拼接：把 segment 插到 routeEnd 前面
        head.prev = this.routeEnd.prev;
        tail.next = this.routeEnd;
        this.routeEnd.prev.next = head;
        this.routeEnd.prev = tail;
        this.routeEnd.segment = segment;

        // 2) duration / nrNodes：加上 segment 内每条边（head->...->tail），但不重复计 routeEnd 自身
        for (Node n = head; n.segment == segment && n != this.routeEnd; n = n.next) {
            this.duration += dataModel.distanceMatrix[n.index][n.next.index];
            this.nrNodes++;
        }
    }

    @Override
    public String toString() {
        Node node = routeStart;
        StringBuilder sb = new StringBuilder();
        sb.append(this.duration).append(" ").append(node);
        while (node != routeEnd) {
            sb.append("->").append(node.next);
            node = node.next;
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return this.ID;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Route && this.ID == ((Route) o).ID;
    }

    /**
     * 一个 segment 在链表中的边界（head 到 tail）。
     *
     * <p>tail 的定义：遍历 segment 内节点，得到“最后一个仍属于该 segment 的节点”。</p>
     */
    private static final class SegmentBounds {
        final Node head;
        final Node tail;

        private SegmentBounds(Node head, Node tail) {
            this.head = head;
            this.tail = tail;
        }
    }

    private SegmentBounds findSegmentBounds(Segment segment) {
        Node head = segment.segmentHead;
        Node tail = head;
        for (Node n = head; n.segment == segment; n = n.next) {
            tail = n;
        }
        return new SegmentBounds(head, tail);
    }

    @Override
    public State getState() {
        return new State();
    }

    protected class State extends ReversibleDataStructure.State {
        final int segments;
        final int nrNodes;
        final int duration;


        public State() {
            this.segments = Route.this.segments;
            this.nrNodes = Route.this.nrNodes;
            this.duration = Route.this.duration;
        }

        @Override
        public void restore() {
            Route.this.segments = this.segments;
            Route.this.nrNodes = this.nrNodes;
            Route.this.duration = this.duration;
        }
    }
}
