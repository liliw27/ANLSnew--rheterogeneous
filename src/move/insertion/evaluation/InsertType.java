package move.insertion.evaluation;

/**
 * 插入类型（由评估层产出，供 repair 算子执行）。
 */
public enum InsertType {
    /** II：整单插入一个 customer visit。 */
    INSERT_NODE,
    /** SI：拆分配送插入（生成多个小 visit 分散插入）。 */
    INSERT_SPLITDELIVERY,
    /** STI：MTID（先插 customer，再插 depot 边界）。 */
    INSERT_MULTITRIPID,
    /** STI：MTDI（先插 depot 边界，再插 customer）。 */
    INSERT_MULTITRIPDI
}


