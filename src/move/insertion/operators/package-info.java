/**
 * repair operators（插入/修复算子）。
 *
 * <p>这些类会实际修改解结构（Route/Segment/Node 链表）。它们通常会：</p>
 * <ul>
 *   <li>从 {@code sa.unServedNodes} 中选择下一个要插入的 visit</li>
 *   <li>调用 {@link move.insertion.evaluation.EvaluateInsertion} 得到一个 {@link move.insertion.evaluation.Evaluation}</li>
 *   <li>执行插入，并做必要的后处理（见 {@link move.insertion.util.InsertionExecutor}）</li>
 * </ul>
 */
package move.insertion.operators;


