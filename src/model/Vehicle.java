package model;

/**
 * 车辆定义（多舱结构 + 舱容量）。
 *
 * <p>在 FRP（Fuel Replenishment Problem）的这类变体里，车辆具有多个相互隔离的舱室（compartment）：</p>
 * <ul>
 *   <li>一次 trip/segment 内，每个舱室只能装一种产品</li>
 *   <li>每个舱室的容量为 {@link #comCapacity}</li>
 *   <li>因此车辆一次 trip 的总装载能力为：{@code compartmentNum * comCapacity}</li>
 * </ul>
 *
 * <p>注意：本项目中 Reader 会把原始需求按“千单位”缩放，因此 comCapacity 也可能已被相同方式缩放。</p>
 */
public class Vehicle {
    /** 舱室数量（compartment 个数）。 */
    public int compartmentNum;
    /** 单舱容量。 */
    public int comCapacity;
    /** 车辆编号（读入后可能会因为按容量排序而被重编号）。 */
    public int vIndex;

    public Vehicle(int compartmentNum, int comCapacity, int vIndex) {
        this.vIndex = vIndex;
        this.comCapacity = comCapacity;
        this.compartmentNum = compartmentNum;
    }

    @Override
    public String toString() {
        return compartmentNum + " " + comCapacity + " " + vIndex;
    }
}