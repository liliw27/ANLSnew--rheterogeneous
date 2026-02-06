package move.insertion.operators;

import SA.SimulatedAnnealing;
import model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * TripsInsertion（trip/segment 级别的 repair 算子）。
 *
 * <p>与 {@link move.removal.TripsRemoval} 配合使用：TripsRemoval 会把若干 {@link Segment} 从当前 schedule 中移除，
 * 放入 {@link SA.SimulatedAnnealing#removedSegments}。本算子负责将这些 segments 重新分配回车辆路线。</p>
 *
 * <p><b>实现</b>：</p>
 * <ol>
 *   <li>计算每个 removed segment 的“段内时长”（按相邻节点距离求和）并按升序排序</li>
 *   <li>循环：每次取一个 segment，插入到当前总时长最短的 {@link Route} 末尾（{@link Route#insertSegment(Segment)}）</li>
 * </ol>
 *
 * <p><b>与论文 TR 对齐</b>：论文的 TR 会按 trip 时长排序后依次分配到“当前最短 itinerary”的车辆上。
 * 本实现与该思想一致，但将插入位置固定为“route 尾部”。</p>
 */
public class TripsInsertion extends Insertion {
    protected final SimulatedAnnealing sa;
    protected final Instance dataModel;

    public TripsInsertion(SimulatedAnnealing sa, Instance dataModel) {
        this.sa = sa;
        this.dataModel = dataModel;
    }

    @Override
    public int getObjective() {
        int makespanAfterMove = Arrays.stream(sa.routes).mapToInt(r -> r.duration).max().getAsInt();
        int sumOfComplTimesAfterMove = Arrays.stream(sa.routes).mapToInt(r -> r.duration).sum();
        return makespanAfterMove * sa.MAKESPAN_MULTIPLIER + sumOfComplTimesAfterMove;
    }

    @Override
    public void move() {
        // 1) 给每个 removed segment 计算“段内时长”，并按升序排序
        List<RankedSegment> rankedSegments = new ArrayList<>();
        for (Segment segment : sa.removedSegments) {
            int duration = 0;
            for (Node node = segment.segmentHead; node.segment == segment; node = node.next) {
                if (node.next != null) {
                    duration += dataModel.distanceMatrix[node.index][node.next.index];
                }
            }
            rankedSegments.add(new RankedSegment(segment, duration));
        }
        Collections.sort(rankedSegments);

        sa.removedSegments.clear();
        for (RankedSegment rs : rankedSegments) {
            sa.removedSegments.add(rs.segment);
        }

        // 2) 依次把 segment 插回“当前最短 route”的末尾
        while (!sa.removedSegments.isEmpty()) {
            Route shortestR = shortestRoute(sa.routes);
            shortestR.insertSegment(sa.removedSegments.remove(0));
        }
    }

    private static Route shortestRoute(Route[] routes) {
        Route best = routes[0];
        int bestDuration = Integer.MAX_VALUE;
        for (Route r : routes) {
            if (r.duration < bestDuration) {
                bestDuration = r.duration;
                best = r;
            }
        }
        return best;
    }

    private static final class RankedSegment implements Comparable<RankedSegment> {
        final Segment segment;
        final int duration;

        RankedSegment(Segment segment, int duration) {
            this.segment = segment;
            this.duration = duration;
        }

        @Override
        public int compareTo(RankedSegment other) {
            return Integer.compare(this.duration, other.duration); // increasing
        }
    }
}


