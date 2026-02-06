package move.removal;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for ALNS/SA destroy operators (removal heuristics).
 *
 * <p>This class mirrors {@link move.insertion.operators.Insertion} and stores adaptive-weight bookkeeping:
 * scores, usage counts, and per-segment weight history.</p>
 *
 * @author 20175993
 * @create 7/3/2018
 * @since 1.0.0
 */
public abstract class Removal
{
    public double score=0;
    public double usedNum=0;
    public double iterationNum=0;
    public double bestNum=0;
    public double improvedNum=0;
    public double nonImprovedNum=0;
    public List<Integer> iterationRecord=new ArrayList<>();
    public List<Integer> bestRecord=new ArrayList<>();
    public List<Integer> improveRecord=new ArrayList<>();
    public List<Integer> nonImproveRecord=new ArrayList<>();
    public List<Double> weight=new ArrayList<> ();


    /**
     * Apply the removal move to the current solution (updates the data structures in-place).
     */
    public abstract void move();
}
