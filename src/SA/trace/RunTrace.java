package SA.trace;

import SA.SimulatedAnnealing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 运行轨迹输出（教学版）。
 *
 * <p>用于把 SA 的温度/目标值随迭代变化写入文件，便于画图或调试。</p>
 *
 * <p>注意：为了减少主类 {@link SimulatedAnnealing} 的代码长度与 I/O 噪音，I/O 逻辑集中在这里。</p>
 */
public final class RunTrace implements AutoCloseable {
    private final BufferedWriter temperatureWriter;
    private final BufferedWriter objectiveWriter;

    private RunTrace(BufferedWriter temperatureWriter, BufferedWriter objectiveWriter) {
        this.temperatureWriter = temperatureWriter;
        this.objectiveWriter = objectiveWriter;
    }

    /**
     * 若启用则创建 trace writer；否则返回 null。
     *
     * <p>路径保持与历史实现一致：`./output/SAtemperature.txt` 与 `./output/SAobjective.txt`。</p>
     * <p>若 output 目录不存在，会自动创建（对学生更友好）。</p>
     */
    public static RunTrace createIfEnabled(boolean enabled) {
        if (!enabled) return null;
        try {
            File outputDir = new File("./output");
            //noinspection ResultOfMethodCallIgnored
            outputDir.mkdirs();

            File temperature = new File(outputDir, "SAtemperature.txt");
            File objective = new File(outputDir, "SAobjective.txt");

            BufferedWriter tWriter = new BufferedWriter(new FileWriter(temperature));
            BufferedWriter oWriter = new BufferedWriter(new FileWriter(objective));
            return new RunTrace(tWriter, oWriter);
        } catch (IOException e) {
            // 与历史行为一致：打印异常，但不让算法崩溃（trace 只是辅助输出）
            e.printStackTrace();
            return null;
        }
    }

    public void writeTemperature(int it, double temperature) throws IOException {
        temperatureWriter.write(it + "\t" + temperature + "\n");
    }

    public void writeObjective(int it, int objective) throws IOException {
        objectiveWriter.write(it + "\t" + objective + "\n");
    }

    @Override
    public void close() throws IOException {
        temperatureWriter.close();
        objectiveWriter.close();
    }
}


