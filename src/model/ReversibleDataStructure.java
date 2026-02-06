package model;

public abstract class ReversibleDataStructure
{

   public  enum StateType {
        PREVIOUS_SOLUTION,
        BEST_SOLUTION,
        INIT_SOLUTION
    }

    /* Stores the state of the solution in the previous iteration */
    public State statePrevSolution;
    /* Stores the state of the best solution */
    public State stateBestSolution;
    /* Stores the state of the initial solution */
    public State stateInitSolution;

    public void createRestorePoint(StateType stateType){
        if (stateType == StateType.PREVIOUS_SOLUTION)
            statePrevSolution=getState();
        else if(stateType == StateType.BEST_SOLUTION)
            stateBestSolution=getState();
        else if(stateType == StateType.INIT_SOLUTION)
            stateInitSolution=getState();
        else
            throw new IllegalArgumentException("Unsupported state");
    }

    public void restoreEarlierState(StateType stateType){
        if (stateType == StateType.PREVIOUS_SOLUTION)
            statePrevSolution.restore();
        else if(stateType == StateType.BEST_SOLUTION)
            stateBestSolution.restore();
        else if(stateType == StateType.INIT_SOLUTION)
            stateInitSolution.restore();
        else
            throw new IllegalArgumentException("Unsupported state");
    }

    protected abstract State getState();

    public abstract class State{

        public abstract void restore();
    }
}
