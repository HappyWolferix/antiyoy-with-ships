package yio.tro.antiyoy.ai;

/**
 * Per-province strategy the balancer AI picks once per turn, based on what surrounds that province.
 */
public enum ProvinceMode {

    /** Lots of free neutral land nearby - flood cheap units and grab it. */
    EXPAND,

    /** Boxed in but safe - invest into farms and grow the income. */
    ECONOMY,

    /** Strong or well fortified neighbours - fortify and spend more carefully. */
    TURTLE;


    /**
     * How many times each mode has been picked, for AiSkirmishHarness to report. Diagnostics only -
     * nothing in the AI reads this, and it is never reset by gameplay.
     */
    public static final int[] census = new int[values().length];


    void countOnce() {
        census[ordinal()]++;
    }
}
