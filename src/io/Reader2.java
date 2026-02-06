package io;

import model.Instance;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 与 {@link Reader} 基本一致的实例读取器（历史版本）。
 *
 * <p>历史上 {@code Reader2} 与 {@code Reader} 的主要区别在于：对头部的“skip line”数量不同，
 * 用来适配早期数据格式。</p>
 *
 * <p>当前版本中，{@link Reader} 已升级为“按 section/关键字解析”的健壮实现，可同时支持：</p>
 * <ul>
 *   <li>含 {@code VEHICLE SECTION} 的异构车辆格式</li>
 *   <li>不含 {@code VEHICLE SECTION} 的同构车辆格式（使用头部 {@code NRCOMPARTMENTS}/{@code CAPACITY} 构造车辆）</li>
 * </ul>
 *
 * <p>因此，Reader2 仅作为兼容入口存在，内部直接委托给 {@link Reader}。</p>
 *
 * @author 20175993
 * @create 6/21/2018
 * @since 1.0.0
 */
@Deprecated
public class Reader2
{
    public static Instance readInstance(File file) throws FileNotFoundException
    {
        return Reader.readInstance(file);
    }
}
