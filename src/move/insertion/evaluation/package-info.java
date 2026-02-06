/**
 * 插入评估层（只“算”，不“改”）。
 *
 * <p>本包的类负责枚举候选插法、估计资源可行性，并计算“插入代价/目标增量”。它们不会修改解结构。</p>
 *
 * <p>典型入口：</p>
 * <ul>
 *   <li>{@link move.insertion.evaluation.EvaluateInsertion}：比较 II/SI/STI 三类策略，产出最优 {@link move.insertion.evaluation.Evaluation}</li>
 * </ul>
 */
package move.insertion.evaluation;


