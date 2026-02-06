/**
 * 插入（repair）相关模块（更清晰的三层目录结构）。
 *
 * <p>为提升可读性，{@code move.insertion} 按职责拆成 3 个子包：</p>
 * <ul>
 *   <li>{@link move.insertion.operators}：repair 算子（会实际修改解结构）</li>
 *   <li>{@link move.insertion.evaluation}：评估层（只计算插入代价/方案，不改解）</li>
 *   <li>{@link move.insertion.util}：通用工具（执行 evaluation、后处理等公共逻辑）</li>
 * </ul>
 *
 * <p><b>推荐阅读入口</b>：先看 {@link move.insertion.operators.GreedyInsertion}（算子如何使用评估层），
 * 再看 {@link move.insertion.evaluation.EvaluateInsertion}（II/SI/STI 如何比较）。</p>
 */
package move.insertion;


